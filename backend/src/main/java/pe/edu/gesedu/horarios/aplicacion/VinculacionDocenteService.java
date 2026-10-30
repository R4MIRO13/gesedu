package pe.edu.gesedu.horarios.aplicacion;

import java.time.OffsetDateTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.CursoResumen;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.CursoOpcionDto;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.VinculacionDto;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.VinculacionesAnioDto;
import pe.edu.gesedu.horarios.dominio.AsociacionDocenteCurso;
import pe.edu.gesedu.horarios.dominio.BloqueDisponible;
import pe.edu.gesedu.horarios.dominio.DisponibilidadDocente;
import pe.edu.gesedu.horarios.dominio.VinculacionDocente;
import pe.edu.gesedu.horarios.infraestructura.AsignacionHorarioRepository;
import pe.edu.gesedu.horarios.infraestructura.AsociacionDocenteCursoRepository;
import pe.edu.gesedu.horarios.infraestructura.BloqueDisponibleRepository;
import pe.edu.gesedu.horarios.infraestructura.DisponibilidadDocenteRepository;
import pe.edu.gesedu.horarios.infraestructura.VinculacionDocenteRepository;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;

/**
 * Docentes habilitados para dictar en el año y los cursos que pueden dictar (RF-46, RF-47). Sin una vinculación
 * habilitada un docente no entra al horario; sin una asociación con el curso no se le ofrece para él.
 */
@Service
public class VinculacionDocenteService {

    private static final int CARGA_MAXIMA = 60;

    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final DirectorioDocentes directorio;
    private final VinculacionDocenteRepository vinculaciones;
    private final AsociacionDocenteCursoRepository asociaciones;
    private final DisponibilidadDocenteRepository disponibilidades;
    private final BloqueDisponibleRepository bloquesDisponibles;
    private final AsignacionHorarioRepository asignaciones;
    private final Clock reloj;

    VinculacionDocenteService(AnioEscolarService anios, CatalogoAcademicoService catalogo, DirectorioDocentes directorio,
                              VinculacionDocenteRepository vinculaciones, AsociacionDocenteCursoRepository asociaciones,
                              DisponibilidadDocenteRepository disponibilidades, BloqueDisponibleRepository bloquesDisponibles,
                              AsignacionHorarioRepository asignaciones, Clock reloj) {
        this.anios = anios;
        this.catalogo = catalogo;
        this.directorio = directorio;
        this.vinculaciones = vinculaciones;
        this.asociaciones = asociaciones;
        this.disponibilidades = disponibilidades;
        this.bloquesDisponibles = bloquesDisponibles;
        this.asignaciones = asignaciones;
        this.reloj = reloj;
    }

    /** Todos los docentes registrados frente al año: vinculados o no, con sus cursos y el estado de su disponibilidad. */
    @Transactional(readOnly = true)
    public VinculacionesAnioDto listar(int anio) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        List<CursoResumen> cursos = catalogo.cursos(a.id());
        Map<String, VinculacionDocente> porDocente = vinculaciones.findByAnioEscolarId(a.id()).stream()
                .collect(Collectors.toMap(VinculacionDocente::getDocenteId, v -> v));
        Map<String, List<String>> cursosDe = cursosPorVinculacion(porDocente.values());
        Map<String, DisponibilidadDocente> disponibilidadDe = porDocente.isEmpty() ? Map.of()
                : disponibilidades.findByVinculacionIdIn(porDocente.values().stream().map(VinculacionDocente::getId).toList())
                        .stream().collect(Collectors.toMap(DisponibilidadDocente::getVinculacionId, d -> d));
        Map<String, Long> marcados = marcados(disponibilidadDe.values());

        List<VinculacionDto> docentes = directorio.todos().stream()
                .map(d -> dto(d, porDocente.get(d.id()), cursosDe, disponibilidadDe, marcados))
                .sorted(Comparator.comparing((VinculacionDto v) -> !v.vinculado()).thenComparing(VinculacionDto::nombre))
                .toList();
        return new VinculacionesAnioDto(a.anio(), a.estado(), docentes,
                cursos.stream().map(c -> new CursoOpcionDto(c.id(), c.nombre())).toList());
    }

    /**
     * Crea o actualiza la vinculación del docente con el año y reemplaza sus cursos asociados. No se puede quitar un
     * curso ni deshabilitar a un docente que ya tiene clases en el borrador del horario (409 DOCENTE_CON_CLASES).
     */
    @Transactional
    @Auditable(accion = "GUARDAR_VINCULACION_DOCENTE", modulo = "HORARIOS", tipoEntidad = "VinculacionDocente",
            idEntidad = "#result.vinculacionId()")
    public VinculacionDto guardar(int anio, String docenteId, boolean habilitado, int cargaSemanal, List<String> cursoIds) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        if ("CERRADO".equals(a.estado())) {
            throw new ExcepcionNegocio(CodigoError.ANIO_CERRADO);
        }
        if (cargaSemanal < 0 || cargaSemanal > CARGA_MAXIMA) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                    "La carga semanal debe estar entre 0 y " + CARGA_MAXIMA + " bloques");
        }
        DocenteResumen docente = directorio.porIds(Set.of(docenteId)).get(docenteId);
        if (docente == null) {
            throw new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el docente");
        }
        Set<String> pedidos = new HashSet<>(cursoIds == null ? List.of() : cursoIds);
        Set<String> delAnio = catalogo.cursos(a.id()).stream().map(CursoResumen::id).collect(Collectors.toSet());
        if (!delAnio.containsAll(pedidos)) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "Algún curso elegido no pertenece al año " + anio);
        }

        Optional<VinculacionDocente> existente = vinculaciones.findByDocenteIdAndAnioEscolarId(docenteId, a.id());
        VinculacionDocente vinculacion;
        if (existente.isPresent()) {
            vinculacion = existente.get();
            if (vinculacion.isHabilitado() != habilitado) {
                ContextoAuditoria.registrarCambio("habilitado", vinculacion.isHabilitado(), habilitado);
            }
            if (vinculacion.getCargaSemanalReferencia() != cargaSemanal) {
                ContextoAuditoria.registrarCambio("cargaSemanal", vinculacion.getCargaSemanalReferencia(), cargaSemanal);
            }
            if (!habilitado && vinculacion.isHabilitado()
                    && asignaciones.contarClasesDelDocenteEnElBorrador(a.id(), docenteId) > 0) {
                throw new ExcepcionNegocio(CodigoError.DOCENTE_CON_CLASES,
                        docente.nombreCompleto() + " tiene clases en el borrador del horario: quítalas antes de deshabilitarlo");
            }
            vinculacion.actualizar(habilitado, cargaSemanal);
        } else {
            vinculacion = new VinculacionDocente(docenteId, a.id(), habilitado, cargaSemanal);
            ContextoAuditoria.registrarCambio("docente", null, docente.nombreCompleto());
            ContextoAuditoria.registrarCambio("habilitado", null, habilitado);
            ContextoAuditoria.registrarCambio("cargaSemanal", null, cargaSemanal);
        }
        try {
            vinculaciones.saveAndFlush(vinculacion);
        } catch (DataIntegrityViolationException e) {
            throw new ExcepcionNegocio(CodigoError.CONFLICTO_CONCURRENCIA,
                    "Otra persona vinculó al docente al mismo tiempo; vuelve a intentarlo");
        }
        reemplazarAsociaciones(a, vinculacion, docente, pedidos);

        Map<String, List<String>> cursosDe = cursosPorVinculacion(List.of(vinculacion));
        Optional<DisponibilidadDocente> disponibilidad = disponibilidades.findByVinculacionId(vinculacion.getId());
        Map<String, DisponibilidadDocente> disponibilidadDe = disponibilidad.map(d -> Map.of(vinculacion.getId(), d)).orElse(Map.of());
        return dto(docente, vinculacion, cursosDe, disponibilidadDe, marcados(disponibilidadDe.values()));
    }

    private void reemplazarAsociaciones(AnioEscolarResumen a, VinculacionDocente vinculacion, DocenteResumen docente,
                                        Set<String> pedidos) {
        List<AsociacionDocenteCurso> actuales = asociaciones.findByVinculacionId(vinculacion.getId());
        Set<String> actualesIds = actuales.stream().map(AsociacionDocenteCurso::getCursoId).collect(Collectors.toSet());
        Map<String, String> nombres = catalogo.cursos(a.id()).stream()
                .collect(Collectors.toMap(CursoResumen::id, CursoResumen::nombre));

        List<AsociacionDocenteCurso> quitadas = actuales.stream().filter(x -> !pedidos.contains(x.getCursoId())).toList();
        for (AsociacionDocenteCurso q : quitadas) {
            if (asignaciones.existsByAsociacionDocenteCursoId(q.getId())) {
                throw new ExcepcionNegocio(CodigoError.DOCENTE_CON_CLASES, docente.nombreCompleto()
                        + " tiene clases de " + nombres.get(q.getCursoId()) + " en el borrador del horario: quítalas antes de "
                        + "desasociar el curso");
            }
        }
        asociaciones.deleteAll(quitadas);
        quitadas.forEach(q -> ContextoAuditoria.registrarCambio("curso " + nombres.get(q.getCursoId()), "asociado", null));
        List<AsociacionDocenteCurso> nuevas = new ArrayList<>();
        for (String cursoId : pedidos) {
            if (!actualesIds.contains(cursoId)) {
                nuevas.add(new AsociacionDocenteCurso(vinculacion.getId(), cursoId));
                ContextoAuditoria.registrarCambio("curso " + nombres.get(cursoId), null, "asociado");
            }
        }
        asociaciones.saveAll(nuevas);
        asociaciones.flush();
    }

    private Map<String, List<String>> cursosPorVinculacion(Collection<VinculacionDocente> lista) {
        if (lista.isEmpty()) {
            return Map.of();
        }
        return asociaciones.findByVinculacionIdIn(lista.stream().map(VinculacionDocente::getId).toList()).stream()
                .collect(Collectors.groupingBy(AsociacionDocenteCurso::getVinculacionId,
                        Collectors.mapping(AsociacionDocenteCurso::getCursoId, Collectors.toList())));
    }

    private Map<String, Long> marcados(Collection<DisponibilidadDocente> lista) {
        if (lista.isEmpty()) {
            return Map.of();
        }
        return bloquesDisponibles.findByDisponibilidadIdIn(lista.stream().map(DisponibilidadDocente::getId).toList()).stream()
                .collect(Collectors.groupingBy(BloqueDisponible::getDisponibilidadId, Collectors.counting()));
    }

    private VinculacionDto dto(DocenteResumen docente, VinculacionDocente v, Map<String, List<String>> cursosDe,
                               Map<String, DisponibilidadDocente> disponibilidadDe, Map<String, Long> marcados) {
        if (v == null) {
            return new VinculacionDto(docente.id(), docente.nombreCompleto(), null, false, false, 0, List.of(), false, 0, null);
        }
        DisponibilidadDocente d = disponibilidadDe.get(v.getId());
        OffsetDateTime confirmacion = d == null ? null : d.getFechaHoraConfirmacion().atZone(reloj.getZone()).toOffsetDateTime();
        return new VinculacionDto(docente.id(), docente.nombreCompleto(), v.getId(), true, v.isHabilitado(),
                v.getCargaSemanalReferencia(), cursosDe.getOrDefault(v.getId(), List.of()), d != null,
                d == null ? 0 : marcados.getOrDefault(d.getId(), 0L).intValue(), confirmacion);
    }
}
