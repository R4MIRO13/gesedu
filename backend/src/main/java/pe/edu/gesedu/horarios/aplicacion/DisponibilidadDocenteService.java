package pe.edu.gesedu.horarios.aplicacion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.PeriodoDto;
import pe.edu.gesedu.horarios.aplicacion.DisponibilidadDtos.DisponibilidadDto;
import pe.edu.gesedu.horarios.aplicacion.DisponibilidadDtos.ValidacionDisponibilidadDto;
import pe.edu.gesedu.horarios.dominio.AsociacionDocenteCurso;
import pe.edu.gesedu.horarios.dominio.BloqueDisponible;
import pe.edu.gesedu.horarios.dominio.BloqueHorario;
import pe.edu.gesedu.horarios.dominio.DisponibilidadDocente;
import pe.edu.gesedu.horarios.dominio.VinculacionDocente;
import pe.edu.gesedu.horarios.infraestructura.AsociacionDocenteCursoRepository;
import pe.edu.gesedu.horarios.infraestructura.BloqueDisponibleRepository;
import pe.edu.gesedu.horarios.infraestructura.BloqueHorarioRepository;
import pe.edu.gesedu.horarios.infraestructura.DisponibilidadDocenteRepository;
import pe.edu.gesedu.horarios.infraestructura.VinculacionDocenteRepository;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;

/**
 * UC-20, Registrar disponibilidad docente (RF-46, RF-47). El docente marca los bloques en que puede dictar mientras el
 * periodo de registro está abierto; el sistema conserva solo la última versión confirmada (FA-1), advierte si los
 * bloques parecen insuficientes para su carga (FA-2) y rechaza el registro con el periodo cerrado (FE-1).
 */
@Service
public class DisponibilidadDocenteService {

    private static final int LARGO_OBSERVACIONES = 500;

    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final DirectorioDocentes directorio;
    private final ContextoUsuario contexto;
    private final PeriodoDisponibilidadService periodos;
    private final VinculacionDocenteRepository vinculaciones;
    private final AsociacionDocenteCursoRepository asociaciones;
    private final DisponibilidadDocenteRepository disponibilidades;
    private final BloqueDisponibleRepository marcados;
    private final BloqueHorarioRepository bloques;
    private final Clock reloj;

    DisponibilidadDocenteService(AnioEscolarService anios, CatalogoAcademicoService catalogo, DirectorioDocentes directorio,
                                 ContextoUsuario contexto, PeriodoDisponibilidadService periodos,
                                 VinculacionDocenteRepository vinculaciones, AsociacionDocenteCursoRepository asociaciones,
                                 DisponibilidadDocenteRepository disponibilidades, BloqueDisponibleRepository marcados,
                                 BloqueHorarioRepository bloques, Clock reloj) {
        this.anios = anios;
        this.catalogo = catalogo;
        this.directorio = directorio;
        this.contexto = contexto;
        this.periodos = periodos;
        this.vinculaciones = vinculaciones;
        this.asociaciones = asociaciones;
        this.disponibilidades = disponibilidades;
        this.marcados = marcados;
        this.bloques = bloques;
        this.reloj = reloj;
    }

    /** Pasos 1 y 2: la matriz con la disponibilidad ya registrada, si existe. */
    @Transactional(readOnly = true)
    public DisponibilidadDto consultar(int anio) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        DocenteResumen docente = docenteActual();
        Optional<VinculacionDocente> vinculacion = vinculaciones.findByDocenteIdAndAnioEscolarId(docente.id(), a.id());
        return armar(a, docente, vinculacion.filter(VinculacionDocente::isHabilitado).orElse(null));
    }

    /** Paso 4 y FA-2: compara los bloques que se están marcando con la carga horaria del docente, sin guardar. */
    @Transactional(readOnly = true)
    public ValidacionDisponibilidadDto validar(int anio, Collection<String> bloqueIds) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        DocenteResumen docente = docenteActual();
        VinculacionDocente v = vinculacionHabilitada(a, docente);
        return evaluar(v.getCargaSemanalReferencia(), validos(a, bloqueIds).size());
    }

    /**
     * Pasos 5 a 8: guarda la disponibilidad como la última versión confirmada. Con el periodo cerrado responde 422
     * {@code PERIODO_DISPONIBILIDAD_CERRADO} y conserva la anterior (FE-1). La advertencia de insuficiencia no impide
     * confirmar: el docente ya la vio antes de decidir (FA-2).
     */
    @Transactional
    @Auditable(accion = "CONFIRMAR_DISPONIBILIDAD", modulo = "HORARIOS", tipoEntidad = "DisponibilidadDocente",
            idEntidad = "#result.vinculacionId()")
    public DisponibilidadDto confirmar(int anio, Collection<String> bloqueIds, String observaciones) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        DocenteResumen docente = docenteActual();
        if ("CERRADO".equals(a.estado())) {
            throw new ExcepcionNegocio(CodigoError.ANIO_CERRADO);
        }
        // El bloqueo de la vinculación serializa dos confirmaciones del mismo docente (sin filas duplicadas)
        VinculacionDocente v = vinculaciones.bloquearPorDocenteYAnio(docente.id(), a.id())
                .filter(VinculacionDocente::isHabilitado)
                .orElseThrow(() -> sinVinculo(a));
        PeriodoDto periodo = periodos.obtenerPorId(a.id(), a.anio());
        if (!periodo.abierto()) {
            throw new ExcepcionNegocio(CodigoError.PERIODO_DISPONIBILIDAD_CERRADO,
                    "El periodo de registro de disponibilidad está cerrado: ya no se permiten modificaciones y se mantiene "
                            + "tu última disponibilidad confirmada");
        }
        Set<String> nuevos = validos(a, bloqueIds);
        String texto = limpiar(observaciones);

        Optional<DisponibilidadDocente> existente = disponibilidades.findByVinculacionId(v.getId());
        Set<String> anteriores = existente.map(d -> marcados.findByDisponibilidadId(d.getId()).stream()
                .map(BloqueDisponible::getBloqueId).collect(Collectors.toSet())).orElse(Set.of());
        DisponibilidadDocente disponibilidad = existente.orElseGet(() ->
                disponibilidades.save(new DisponibilidadDocente(v.getId(), reloj.instant(), texto)));
        if (existente.isPresent()) {
            ContextoAuditoria.registrarCambio("observaciones", disponibilidad.getObservaciones(), texto);
            disponibilidad.confirmar(reloj.instant(), texto);
            // Solo se conserva la última versión: se reemplazan los bloques anteriores (FA-1)
            marcados.deleteByDisponibilidadId(disponibilidad.getId());
            marcados.flush();
        }
        marcados.saveAll(nuevos.stream().map(id -> new BloqueDisponible(disponibilidad.getId(), id)).toList());
        disponibilidades.flush();
        marcados.flush();

        ContextoAuditoria.registrarCambio("bloques disponibles", anteriores.size(), nuevos.size());
        return armar(a, docente, v);
    }

    // ---------------------------------------------------------------------------------------------------------

    private DisponibilidadDto armar(AnioEscolarResumen a, DocenteResumen docente, VinculacionDocente v) {
        PeriodoDto periodo = periodos.obtenerPorId(a.id(), a.anio());
        List<BloqueHorarioDto> todos = bloques.findByAnioEscolarId(a.id()).stream()
                .sorted(Comparator.comparing(BloqueHorario::getDiaSemana).thenComparing(BloqueHorario::getHoraInicio))
                .map(b -> new BloqueHorarioDto(b.getId(), b.getDiaSemana(), b.getHoraInicio(), b.getHoraFin())).toList();
        if (v == null) {
            return new DisponibilidadDto(a.anio(), false, null, docente.nombreCompleto(), 0, List.of(), periodo, todos,
                    List.of(), null, null, null);
        }
        Optional<DisponibilidadDocente> disponibilidad = disponibilidades.findByVinculacionId(v.getId());
        List<String> seleccionados = disponibilidad.map(d -> marcados.findByDisponibilidadId(d.getId()).stream()
                .map(BloqueDisponible::getBloqueId).sorted().toList()).orElse(List.of());
        List<CursoOpcionDto> cursos = cursosDe(a, v);
        OffsetDateTime confirmacion = disponibilidad
                .map(d -> d.getFechaHoraConfirmacion().atZone(reloj.getZone()).toOffsetDateTime()).orElse(null);
        return new DisponibilidadDto(a.anio(), true, v.getId(), docente.nombreCompleto(), v.getCargaSemanalReferencia(),
                cursos, periodo, todos, seleccionados, disponibilidad.map(DisponibilidadDocente::getObservaciones).orElse(null),
                confirmacion, evaluar(v.getCargaSemanalReferencia(), seleccionados.size()));
    }

    private List<CursoOpcionDto> cursosDe(AnioEscolarResumen a, VinculacionDocente v) {
        Set<String> ids = asociaciones.findByVinculacionId(v.getId()).stream()
                .map(AsociacionDocenteCurso::getCursoId).collect(Collectors.toSet());
        return catalogo.cursos(a.id()).stream().filter(c -> ids.contains(c.id()))
                .map((CursoResumen c) -> new CursoOpcionDto(c.id(), c.nombre())).toList();
    }

    static ValidacionDisponibilidadDto evaluar(int carga, int marcados) {
        int faltan = Math.max(0, carga - marcados);
        boolean suficiente = faltan == 0;
        String mensaje = suficiente ? null : "Tu disponibilidad podría ser insuficiente para tu carga horaria. Faltan al menos "
                + faltan + (faltan == 1 ? " bloque." : " bloques.");
        return new ValidacionDisponibilidadDto(marcados, carga, faltan, suficiente, mensaje);
    }

    private DocenteResumen docenteActual() {
        String usuarioId = contexto.idUsuarioActual().orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO));
        return directorio.docenteDeUsuario(usuarioId).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo un docente registra su disponibilidad"));
    }

    private VinculacionDocente vinculacionHabilitada(AnioEscolarResumen a, DocenteResumen docente) {
        return vinculaciones.findByDocenteIdAndAnioEscolarId(docente.id(), a.id())
                .filter(VinculacionDocente::isHabilitado).orElseThrow(() -> sinVinculo(a));
    }

    private static ExcepcionNegocio sinVinculo(AnioEscolarResumen a) {
        return new ExcepcionNegocio(CodigoError.VINCULO_ACADEMICO_INEXISTENTE,
                "No estás habilitado como docente para el año " + a.anio() + ". Consulta con la administración.");
    }

    /** Los bloques deben pertenecer al año; los repetidos cuentan una sola vez. */
    private Set<String> validos(AnioEscolarResumen a, Collection<String> bloqueIds) {
        Set<String> delAnio = bloques.findByAnioEscolarId(a.id()).stream().map(BloqueHorario::getId).collect(Collectors.toSet());
        Set<String> pedidos = new HashSet<>(bloqueIds == null ? List.of() : bloqueIds);
        pedidos.removeIf(Objects::isNull);
        if (!delAnio.containsAll(pedidos)) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "Algún bloque no pertenece al año " + a.anio());
        }
        return pedidos;
    }

    private static String limpiar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String t = texto.trim();
        if (t.length() > LARGO_OBSERVACIONES) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                    "Las observaciones admiten hasta " + LARGO_OBSERVACIONES + " caracteres");
        }
        return t;
    }
}
