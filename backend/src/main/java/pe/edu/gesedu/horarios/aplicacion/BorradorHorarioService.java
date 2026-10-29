package pe.edu.gesedu.horarios.aplicacion;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.CursoResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.SeccionResumen;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.compartido.web.Etags;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.AsignacionDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.BorradorDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.CargaCursoDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.CargaSeccionDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.ConflictoDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.DocenteDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.OpcionDocenteDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.OpcionesDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.Perspectiva;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.ResultadoAsignacion;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.SeccionDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.SolicitudAsignacion;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.ValidacionDto;
import pe.edu.gesedu.horarios.dominio.AsignacionHorario;
import pe.edu.gesedu.horarios.dominio.AsociacionDocenteCurso;
import pe.edu.gesedu.horarios.dominio.BloqueHorario;
import pe.edu.gesedu.horarios.dominio.DiaSemana;
import pe.edu.gesedu.horarios.dominio.HorarioBorrador;
import pe.edu.gesedu.horarios.dominio.ValidadorConflictos;
import pe.edu.gesedu.horarios.dominio.ValidadorConflictos.Candidata;
import pe.edu.gesedu.horarios.dominio.ValidadorConflictos.Conflicto;
import pe.edu.gesedu.horarios.dominio.ValidadorConflictos.Existente;
import pe.edu.gesedu.horarios.dominio.VinculacionDocente;
import pe.edu.gesedu.horarios.infraestructura.AsignacionHorarioRepository;
import pe.edu.gesedu.horarios.infraestructura.AsociacionDocenteCursoRepository;
import pe.edu.gesedu.horarios.infraestructura.BloqueDisponibleRepository;
import pe.edu.gesedu.horarios.infraestructura.BloqueHorarioRepository;
import pe.edu.gesedu.horarios.infraestructura.CargaHorariaCursoRepository;
import pe.edu.gesedu.horarios.infraestructura.HorarioBorradorRepository;
import pe.edu.gesedu.horarios.infraestructura.VinculacionDocenteRepository;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;

/**
 * Elaboración del horario (UC-21, RF-39 a RF-42, RF-47): un borrador por año, con las perspectivas Sección y
 * Docente sobre los mismos datos, validación de cruces en vivo y alta, cambio y baja de clases.
 * <p>
 * Concurrencia: cada alta, cambio o baja exige la versión del borrador en {@code If-Match} (409
 * CONFLICTO_CONCURRENCIA si cambió) y la incrementa con {@code @Version}; además, los índices
 * únicos de la base detienen los cruces que escapen a la validación (409 CONFLICTO_HORARIO).
 */
@Service
public class BorradorHorarioService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("H:mm");
    private static final Comparator<BloqueHorario> POR_DIA_Y_HORA =
            Comparator.comparing(BloqueHorario::getDiaSemana).thenComparing(BloqueHorario::getHoraInicio);

    /** Todo lo que se necesita saber del año para validar y mostrar: bloques, secciones, cursos y docentes. */
    private record Marco(String anioId, int anio, Map<String, BloqueHorario> bloques, List<SeccionResumen> secciones,
                         Map<String, SeccionResumen> seccionPorId, List<CursoResumen> cursos,
                         Map<String, CursoResumen> cursoPorId, List<VinculacionDocente> vinculaciones,
                         Map<String, String> asociacionPorDocenteYCurso) {
    }

    @PersistenceContext
    private EntityManager em;

    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final DirectorioDocentes directorio;
    private final HorarioBorradorRepository borradores;
    private final AsignacionHorarioRepository asignaciones;
    private final BloqueHorarioRepository bloques;
    private final VinculacionDocenteRepository vinculaciones;
    private final AsociacionDocenteCursoRepository asociaciones;
    private final CargaHorariaCursoRepository cargas;
    private final BloqueDisponibleRepository disponibles;
    private final CreadorBorrador creador;
    private final Clock reloj;

    BorradorHorarioService(AnioEscolarService anios, CatalogoAcademicoService catalogo, DirectorioDocentes directorio,
                           HorarioBorradorRepository borradores, AsignacionHorarioRepository asignaciones,
                           BloqueHorarioRepository bloques, VinculacionDocenteRepository vinculaciones,
                           AsociacionDocenteCursoRepository asociaciones, CargaHorariaCursoRepository cargas,
                           BloqueDisponibleRepository disponibles, CreadorBorrador creador, Clock reloj) {
        this.anios = anios;
        this.catalogo = catalogo;
        this.directorio = directorio;
        this.borradores = borradores;
        this.asignaciones = asignaciones;
        this.bloques = bloques;
        this.vinculaciones = vinculaciones;
        this.asociaciones = asociaciones;
        this.cargas = cargas;
        this.disponibles = disponibles;
        this.creador = creador;
        this.reloj = reloj;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Consulta del borrador (pasos 1 a 3)
    // ---------------------------------------------------------------------------------------------------------

    /**
     * El borrador del año; lo crea si no existe. Sin {@code seccionId} (o {@code docenteId}) se muestra la primera
     * sección (o el primer docente).
     */
    @Transactional
    public BorradorDto obtener(int anio, Perspectiva perspectiva, String seccionId, String docenteId) {
        Marco marco = marco(anio);
        HorarioBorrador borrador = obtenerOCrear(marco.anioId());
        List<AsignacionHorario> todas = asignaciones.findByHorarioBorradorId(borrador.getId());
        Map<String, DocenteResumen> docentes = docentes(marco, todas, List.of());

        String seccionElegida = seccionId;
        String docenteElegido = docenteId;
        List<AsignacionHorario> visibles;
        CargaSeccionDto carga = null;
        if (perspectiva == Perspectiva.DOCENTE) {
            docenteElegido = docenteId != null ? docenteId
                    : marco.vinculaciones().stream().map(VinculacionDocente::getDocenteId)
                            .min(Comparator.comparing(id -> nombreOrden(docentes, id))).orElse(null);
            if (docenteElegido != null && !docentes.containsKey(docenteElegido)) {
                throw noExiste("docente");
            }
            String docenteFiltro = docenteElegido;
            visibles = todas.stream().filter(a -> Objects.equals(a.getDocenteId(), docenteFiltro)).toList();
        } else {
            seccionElegida = seccionId != null ? seccionId
                    : marco.secciones().stream().findFirst().map(SeccionResumen::id).orElse(null);
            if (seccionElegida != null && !marco.seccionPorId().containsKey(seccionElegida)) {
                throw noExiste("sección");
            }
            String seccionFiltro = seccionElegida;
            visibles = todas.stream().filter(a -> Objects.equals(a.getSeccionId(), seccionFiltro)).toList();
            carga = seccionElegida == null ? null : carga(marco, marco.seccionPorId().get(seccionElegida), todas);
        }

        List<BloqueHorarioDto> bloquesDto = marco.bloques().values().stream().sorted(POR_DIA_Y_HORA)
                .map(b -> new BloqueHorarioDto(b.getId(), b.getDiaSemana(), b.getHoraInicio(), b.getHoraFin()))
                .toList();
        List<DocenteDto> docentesDto = marco.vinculaciones().stream().map(VinculacionDocente::getDocenteId)
                .filter(docentes::containsKey).distinct()
                .sorted(Comparator.comparing(id -> nombreOrden(docentes, id)))
                .map(id -> new DocenteDto(id, docentes.get(id).nombreCompleto())).toList();
        List<SeccionDto> seccionesDto = marco.secciones().stream()
                .map(s -> new SeccionDto(s.id(), s.nombre())).toList();

        return new BorradorDto(borrador.getId(), anio, borrador.getVersion(), perspectiva, seccionElegida,
                docenteElegido, bloquesDto, seccionesDto, docentesDto,
                ordenadas(visibles, marco).stream().map(a -> aDto(a, marco, docentes)).toList(), carga);
    }

    /**
     * Cursos de la sección con sus horas, y — al elegir un curso — los docentes asociados con su disponibilidad en
     * el bloque y si ya tienen clase en él (paso 5, RF-47, RF-43).
     */
    @Transactional(readOnly = true)
    public OpcionesDto opciones(int anio, String bloqueId, String seccionId, String cursoId, String docenteId,
                                String asignacionId) {
        Marco marco = marco(anio);
        BloqueHorario bloque = bloque(marco, bloqueId);
        SeccionResumen seccion = seccion(marco, seccionId);
        HorarioBorrador borrador = borradores.findByAnioEscolarId(marco.anioId()).orElse(null);
        List<AsignacionHorario> todas = borrador == null ? List.of()
                : asignaciones.findByHorarioBorradorId(borrador.getId());

        List<CargaCursoDto> cursos = carga(marco, seccion, todas).cursos();
        List<OpcionDocenteDto> docentesDto = List.of();
        if (cursoId != null) {
            CursoResumen curso = curso(marco, cursoId);
            Set<String> libres = disponibles.docentesDisponiblesEn(marco.anioId(), bloque.getId());
            Map<String, DocenteResumen> docentes = docentes(marco, todas, List.of());
            Map<String, AsignacionHorario> ocupados = todas.stream()
                    .filter(a -> a.getBloqueId().equals(bloque.getId()))
                    .filter(a -> !Objects.equals(a.getId(), asignacionId))
                    .collect(Collectors.toMap(AsignacionHorario::getDocenteId, Function.identity(), (a, b) -> a));
            docentesDto = marco.vinculaciones().stream().map(VinculacionDocente::getDocenteId).distinct()
                    .filter(id -> marco.asociacionPorDocenteYCurso().containsKey(clave(id, curso.id())))
                    .filter(id -> docenteId == null || docenteId.equals(id))
                    .filter(docentes::containsKey)
                    .map(id -> {
                        AsignacionHorario ocupado = ocupados.get(id);
                        String donde = ocupado == null ? null : marco.seccionPorId().get(ocupado.getSeccionId()).nombre();
                        String detalle = ocupado == null ? null
                                : "tiene clase en " + donde + " el " + descripcion(bloque);
                        return new OpcionDocenteDto(id, docentes.get(id).nombreCompleto(), libres.contains(id), donde,
                                detalle);
                    })
                    .sorted(Comparator.comparing((OpcionDocenteDto o) -> !(o.disponible() && o.ocupadoEn() == null))
                            .thenComparing(OpcionDocenteDto::nombre))
                    .toList();
        }
        return new OpcionesDto(cursos, docentesDto);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Validación en vivo (pasos 6 y 7)
    // ---------------------------------------------------------------------------------------------------------

    /** Todos los conflictos de la clase pedida, sin guardar nada. */
    @Transactional(readOnly = true)
    public ValidacionDto validar(int anio, SolicitudAsignacion solicitud) {
        Marco marco = marco(anio);
        HorarioBorrador borrador = borradores.findByAnioEscolarId(marco.anioId()).orElse(null);
        List<AsignacionHorario> todas = borrador == null ? List.of()
                : asignaciones.findByHorarioBorradorId(borrador.getId());
        Map<String, DocenteResumen> docentes = docentes(marco, todas, List.of(solicitud.docenteId()));
        List<Conflicto> conflictos = conflictos(marco, todas, solicitud);
        List<ConflictoDto> dtos = conflictos.stream().map(c -> aDto(c, solicitud, marco, docentes)).toList();
        return new ValidacionDto(dtos.isEmpty(), dtos);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Alta, cambio y baja (pasos 8 y 9; FA-1 y FA-2)
    // ---------------------------------------------------------------------------------------------------------

    @Transactional
    @Auditable(accion = "CREAR_ASIGNACION_HORARIO", modulo = "HORARIOS", tipoEntidad = "AsignacionHorario",
            idEntidad = "#result.asignacion().id()")
    public ResultadoAsignacion crear(int anio, SolicitudAsignacion solicitud, String ifMatch) {
        Marco marco = marco(anio);
        HorarioBorrador borrador = borradorExistente(marco);
        Etags.verificar(borrador.getVersion(), ifMatch);

        List<AsignacionHorario> todas = asignaciones.findByHorarioBorradorId(borrador.getId());
        rechazarSiHayConflictos(marco, todas, solicitud);

        AsignacionHorario nueva = new AsignacionHorario(borrador.getId(), solicitud.bloqueId(),
                solicitud.seccionId(), solicitud.cursoId(), solicitud.docenteId(),
                marco.asociacionPorDocenteYCurso().get(clave(solicitud.docenteId(), solicitud.cursoId())));
        guardar(borrador, () -> asignaciones.saveAndFlush(nueva));

        Map<String, DocenteResumen> docentes = docentes(marco, List.of(nueva), List.of());
        ContextoAuditoria.registrarCambio("sección", null, marco.seccionPorId().get(nueva.getSeccionId()).nombre());
        ContextoAuditoria.registrarCambio("curso", null, marco.cursoPorId().get(nueva.getCursoId()).nombre());
        ContextoAuditoria.registrarCambio("docente", null, docentes.get(nueva.getDocenteId()).nombreCompleto());
        ContextoAuditoria.registrarCambio("bloque", null, descripcion(marco.bloques().get(nueva.getBloqueId())));
        return new ResultadoAsignacion(aDto(nueva, marco, docentes), borrador.getVersion());
    }

    @Transactional
    @Auditable(accion = "MODIFICAR_ASIGNACION_HORARIO", modulo = "HORARIOS", tipoEntidad = "AsignacionHorario",
            idEntidad = "#asignacionId")
    public ResultadoAsignacion modificar(int anio, String asignacionId, SolicitudAsignacion solicitud,
                                         String ifMatch) {
        Marco marco = marco(anio);
        HorarioBorrador borrador = borradorExistente(marco);
        Etags.verificar(borrador.getVersion(), ifMatch);

        List<AsignacionHorario> todas = asignaciones.findByHorarioBorradorId(borrador.getId());
        AsignacionHorario actual = todas.stream().filter(a -> a.getId().equals(asignacionId)).findFirst()
                .orElseThrow(() -> noExiste("asignación"));
        // La sección de una clase no cambia: se corrige el bloque, el curso o el docente (FA-1)
        SolicitudAsignacion edicion = new SolicitudAsignacion(solicitud.bloqueId(), actual.getSeccionId(),
                solicitud.cursoId(), solicitud.docenteId(), asignacionId);
        rechazarSiHayConflictos(marco, todas, edicion);

        Map<String, DocenteResumen> docentes = docentes(marco, todas, List.of(solicitud.docenteId()));
        String bloqueAntes = descripcion(marco.bloques().get(actual.getBloqueId()));
        String cursoAntes = marco.cursoPorId().get(actual.getCursoId()).nombre();
        String docenteAntes = docentes.get(actual.getDocenteId()).nombreCompleto();

        actual.reasignar(solicitud.bloqueId(), solicitud.cursoId(), solicitud.docenteId(),
                marco.asociacionPorDocenteYCurso().get(clave(solicitud.docenteId(), solicitud.cursoId())));
        guardar(borrador, () -> asignaciones.saveAndFlush(actual));

        cambio("bloque", bloqueAntes, descripcion(marco.bloques().get(actual.getBloqueId())));
        cambio("curso", cursoAntes, marco.cursoPorId().get(actual.getCursoId()).nombre());
        cambio("docente", docenteAntes, docentes.get(actual.getDocenteId()).nombreCompleto());
        return new ResultadoAsignacion(aDto(actual, marco, docentes), borrador.getVersion());
    }

    /** @return la nueva versión del borrador */
    @Transactional
    @Auditable(accion = "ELIMINAR_ASIGNACION_HORARIO", modulo = "HORARIOS", tipoEntidad = "AsignacionHorario",
            idEntidad = "#asignacionId")
    public long eliminar(int anio, String asignacionId, String ifMatch) {
        Marco marco = marco(anio);
        HorarioBorrador borrador = borradorExistente(marco);
        Etags.verificar(borrador.getVersion(), ifMatch);

        AsignacionHorario actual = asignaciones.findByHorarioBorradorId(borrador.getId()).stream()
                .filter(a -> a.getId().equals(asignacionId)).findFirst().orElseThrow(() -> noExiste("asignación"));
        Map<String, DocenteResumen> docentes = docentes(marco, List.of(actual), List.of());

        guardar(borrador, () -> asignaciones.delete(actual));

        ContextoAuditoria.registrarCambio("sección", marco.seccionPorId().get(actual.getSeccionId()).nombre(), null);
        ContextoAuditoria.registrarCambio("curso", marco.cursoPorId().get(actual.getCursoId()).nombre(), null);
        ContextoAuditoria.registrarCambio("docente", docentes.get(actual.getDocenteId()).nombreCompleto(), null);
        ContextoAuditoria.registrarCambio("bloque", descripcion(marco.bloques().get(actual.getBloqueId())), null);
        return borrador.getVersion();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Apoyo
    // ---------------------------------------------------------------------------------------------------------

    private static void cambio(String campo, String antes, String despues) {
        if (!Objects.equals(antes, despues)) {
            ContextoAuditoria.registrarCambio(campo, antes, despues);
        }
    }

    /**
     * Aplica el cambio y sube la versión del borrador (una vez). Si dos ediciones llegan a la vez, la segunda choca
     * con la versión (409 CONFLICTO_CONCURRENCIA) o con un índice único (409 CONFLICTO_HORARIO).
     */
    private void guardar(HorarioBorrador borrador, Runnable cambio) {
        try {
            cambio.run();
            borrador.tocar(reloj.instant());
            // El flush ejecuta el UPDATE con la versión: si otra transacción ya la subió, falla aquí (409)
            em.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ExcepcionNegocio(CodigoError.CONFLICTO_HORARIO,
                    "Otra persona acaba de ocupar el docente o la sección en ese bloque; recarga la matriz");
        } catch (OptimisticLockException | OptimisticLockingFailureException e) {
            throw new ExcepcionNegocio(CodigoError.CONFLICTO_CONCURRENCIA,
                    "Otra persona modificó el horario mientras lo editabas; recarga la matriz");
        }
    }

    private HorarioBorrador obtenerOCrear(String anioId) {
        return borradores.findByAnioEscolarId(anioId).orElseGet(() -> {
            try {
                creador.crear(anioId);
            } catch (DataIntegrityViolationException e) {
                // Otro administrador lo creó justo ahora: se lee el suyo
            }
            return borradores.findByAnioEscolarId(anioId).orElseThrow();
        });
    }

    private HorarioBorrador borradorExistente(Marco marco) {
        return obtenerOCrear(marco.anioId());
    }

    private Marco marco(int anio) {
        String anioId = anios.obtenerPorAnio(anio).id();
        Map<String, BloqueHorario> bloquesPorId = bloques.findByAnioEscolarId(anioId).stream()
                .collect(Collectors.toMap(BloqueHorario::getId, Function.identity()));
        List<SeccionResumen> secciones = catalogo.secciones(anioId);
        List<CursoResumen> cursos = catalogo.cursos(anioId);
        List<VinculacionDocente> habilitadas = vinculaciones.findByAnioEscolarIdAndHabilitadoTrue(anioId);
        Map<String, String> docentePorVinculacion = habilitadas.stream()
                .collect(Collectors.toMap(VinculacionDocente::getId, VinculacionDocente::getDocenteId));
        Map<String, String> asociacionPorDocenteYCurso = new HashMap<>();
        if (!habilitadas.isEmpty()) {
            for (AsociacionDocenteCurso a : asociaciones.findByVinculacionIdIn(docentePorVinculacion.keySet())) {
                asociacionPorDocenteYCurso.put(clave(docentePorVinculacion.get(a.getVinculacionId()), a.getCursoId()),
                        a.getId());
            }
        }
        return new Marco(anioId, anio, bloquesPorId, secciones,
                secciones.stream().collect(Collectors.toMap(SeccionResumen::id, Function.identity())), cursos,
                cursos.stream().collect(Collectors.toMap(CursoResumen::id, Function.identity())), habilitadas,
                asociacionPorDocenteYCurso);
    }

    /** Nombres de los docentes habilitados, de las clases dadas y de los ids extra. */
    private Map<String, DocenteResumen> docentes(Marco marco, Collection<AsignacionHorario> clases,
                                                 Collection<String> extra) {
        Set<String> ids = new HashSet<>(extra);
        marco.vinculaciones().forEach(v -> ids.add(v.getDocenteId()));
        clases.forEach(a -> ids.add(a.getDocenteId()));
        ids.removeIf(Objects::isNull);
        return directorio.porIds(ids);
    }

    private static String nombreOrden(Map<String, DocenteResumen> docentes, String id) {
        DocenteResumen d = docentes.get(id);
        return d == null ? "" : d.apellidos() + " " + d.nombres();
    }

    private static String clave(String docenteId, String cursoId) {
        return docenteId + "|" + cursoId;
    }

    private ExcepcionNegocio noExiste(String que) {
        return new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe la " + que + " indicada");
    }

    private BloqueHorario bloque(Marco marco, String id) {
        BloqueHorario b = marco.bloques().get(id);
        if (b == null) {
            throw new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el bloque indicado en el año");
        }
        return b;
    }

    private SeccionResumen seccion(Marco marco, String id) {
        SeccionResumen s = marco.seccionPorId().get(id);
        if (s == null) {
            throw new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe la sección indicada en el año");
        }
        return s;
    }

    private CursoResumen curso(Marco marco, String id) {
        CursoResumen c = marco.cursoPorId().get(id);
        if (c == null) {
            throw new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el curso indicado en el año");
        }
        return c;
    }

    private List<Conflicto> conflictos(Marco marco, List<AsignacionHorario> todas, SolicitudAsignacion s) {
        BloqueHorario bloque = bloque(marco, s.bloqueId());
        seccion(marco, s.seccionId());
        curso(marco, s.cursoId());
        boolean asociado = marco.asociacionPorDocenteYCurso().containsKey(clave(s.docenteId(), s.cursoId()));
        boolean disponible = disponibles.docentesDisponiblesEn(marco.anioId(), bloque.getId())
                .contains(s.docenteId());
        List<Existente> existentes = todas.stream()
                .map(a -> new Existente(a.getId(), a.getBloqueId(), a.getSeccionId(), a.getCursoId(), a.getDocenteId()))
                .toList();
        return ValidadorConflictos.validar(
                new Candidata(s.bloqueId(), s.seccionId(), s.cursoId(), s.docenteId(), s.asignacionId()),
                existentes, asociado, disponible);
    }

    /** Si hay conflictos, rechaza el guardado: 422 por asociación o disponibilidad, 409 por un cruce. */
    private void rechazarSiHayConflictos(Marco marco, List<AsignacionHorario> todas, SolicitudAsignacion s) {
        List<Conflicto> conflictos = conflictos(marco, todas, s);
        if (conflictos.isEmpty()) {
            return;
        }
        Map<String, DocenteResumen> docentes = docentes(marco, todas, List.of(s.docenteId()));
        List<ConflictoDto> dtos = conflictos.stream().map(c -> aDto(c, s, marco, docentes)).toList();
        Conflicto primero = conflictos.getFirst();
        CodigoError codigo = switch (primero.tipo()) {
            case DOCENTE_NO_ASOCIADO -> CodigoError.DOCENTE_NO_ASOCIADO;
            case DOCENTE_NO_DISPONIBLE -> CodigoError.DOCENTE_NO_DISPONIBLE;
            case CRUCE_DOCENTE, CRUCE_SECCION -> CodigoError.CONFLICTO_HORARIO;
        };
        throw new ExcepcionNegocio(codigo, dtos.getFirst().mensaje()).con("conflictos", dtos);
    }

    private ConflictoDto aDto(Conflicto c, SolicitudAsignacion s, Marco marco, Map<String, DocenteResumen> docentes) {
        BloqueHorario bloque = marco.bloques().get(s.bloqueId());
        String profesor = profesor(docentes, s.docenteId());
        String mensaje = switch (c.tipo()) {
            case DOCENTE_NO_ASOCIADO -> profesor + " no está asociado al curso "
                    + marco.cursoPorId().get(s.cursoId()).nombre();
            case DOCENTE_NO_DISPONIBLE -> profesor + " no está disponible el " + descripcion(bloque);
            case CRUCE_DOCENTE -> profesor + " ya tiene clase en "
                    + marco.seccionPorId().get(c.con().seccionId()).nombre() + " el " + descripcion(bloque);
            case CRUCE_SECCION -> marco.seccionPorId().get(s.seccionId()).nombre() + " ya tiene "
                    + marco.cursoPorId().get(c.con().cursoId()).nombre() + " el " + descripcion(bloque);
        };
        Existente con = c.con();
        return new ConflictoDto(c.tipo().name(), mensaje, con == null ? null : con.id(),
                con == null ? null : con.seccionId(), con == null ? null : con.docenteId());
    }

    private static String profesor(Map<String, DocenteResumen> docentes, String docenteId) {
        DocenteResumen d = docentes.get(docenteId);
        return d == null ? "El docente" : "Prof. " + d.nombreCorto();
    }

    private AsignacionDto aDto(AsignacionHorario a, Marco marco, Map<String, DocenteResumen> docentes) {
        DocenteResumen docente = docentes.get(a.getDocenteId());
        return new AsignacionDto(a.getId(), a.getBloqueId(), a.getSeccionId(),
                marco.seccionPorId().get(a.getSeccionId()).nombre(), a.getCursoId(),
                marco.cursoPorId().get(a.getCursoId()).nombre(), a.getDocenteId(),
                docente == null ? "" : docente.nombreCompleto());
    }

    private List<AsignacionHorario> ordenadas(List<AsignacionHorario> clases, Marco marco) {
        return clases.stream()
                .sorted(Comparator.comparing((AsignacionHorario a) -> marco.bloques().get(a.getBloqueId()),
                        POR_DIA_Y_HORA).thenComparing(AsignacionHorario::getSeccionId))
                .toList();
    }

    /** Horas (bloques) asignadas y requeridas de cada curso de la sección (RF-43, C-10). */
    private CargaSeccionDto carga(Marco marco, SeccionResumen seccion, List<AsignacionHorario> todas) {
        Map<String, Integer> requeridasDelGrado = cargas.findByGradoIdIn(List.of(seccion.gradoId())).stream()
                .collect(Collectors.toMap(c -> c.getCursoId(), c -> c.getBloquesSemanales()));
        Map<String, Long> asignadas = todas.stream().filter(a -> a.getSeccionId().equals(seccion.id()))
                .collect(Collectors.groupingBy(AsignacionHorario::getCursoId, Collectors.counting()));
        List<CargaCursoDto> cursos = new ArrayList<>();
        for (CursoResumen curso : marco.cursos()) {
            int requeridas = requeridasDelGrado.getOrDefault(curso.id(), curso.horasLectivas());
            cursos.add(new CargaCursoDto(curso.id(), curso.nombre(), asignadas.getOrDefault(curso.id(), 0L).intValue(),
                    requeridas));
        }
        return new CargaSeccionDto(cursos, cursos.stream().mapToInt(CargaCursoDto::horasAsignadas).sum(),
                cursos.stream().mapToInt(CargaCursoDto::horasRequeridas).sum());
    }

    /** «martes de 9:15 a 10:00» */
    private static String descripcion(BloqueHorario b) {
        return nombreDia(b.getDiaSemana()) + " de " + HORA.format(b.getHoraInicio()) + " a " + HORA.format(b.getHoraFin());
    }

    private static String nombreDia(DiaSemana dia) {
        return switch (dia) {
            case LUNES -> "lunes";
            case MARTES -> "martes";
            case MIERCOLES -> "miércoles";
            case JUEVES -> "jueves";
            case VIERNES -> "viernes";
            case SABADO -> "sábado";
            case DOMINGO -> "domingo";
        };
    }
}
