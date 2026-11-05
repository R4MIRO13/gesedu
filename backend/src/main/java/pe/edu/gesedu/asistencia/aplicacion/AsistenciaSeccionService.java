package pe.edu.gesedu.asistencia.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.SeccionResumen;
import pe.edu.gesedu.asistencia.aplicacion.AsistenciaSeccionDtos.AlumnoAsistenciaDto;
import pe.edu.gesedu.asistencia.aplicacion.AsistenciaSeccionDtos.AsistenciaSeccionDto;
import pe.edu.gesedu.asistencia.aplicacion.AsistenciaSeccionDtos.ItemAsistencia;
import pe.edu.gesedu.asistencia.aplicacion.AsistenciaSeccionDtos.MiSeccionDto;
import pe.edu.gesedu.asistencia.aplicacion.AsistenciaSeccionDtos.TotalesAsistenciaDto;
import pe.edu.gesedu.asistencia.dominio.AsistenciaAlumno;
import pe.edu.gesedu.asistencia.dominio.EstadoAsistencia;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaAlumnoRepository;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaProperties;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.horarios.aplicacion.ClasesDocenteService;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService.AlumnoDeSeccion;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;

/**
 * UC-08, Registrar asistencia de alumnos (RF-14). El tutor pasa lista al inicio de la jornada: cada alumno de su sección
 * queda PUNTUAL (presente al pasar lista), TARDANZA (llegó después de pasar lista pero antes de confirmar) o FALTA. El
 * estado lo decide el tutor, sin umbral automático. El lote se guarda completo o no se guarda (atómico) y hay un solo
 * registro por alumno y fecha, también ante solicitudes simultáneas. Solo el tutor de la sección registra; la hora y la
 * fecha son las del servidor.
 */
@Service
public class AsistenciaSeccionService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Todo lo que hace falta saber para operar sobre la sección del tutor en este instante. */
    private record Contexto(LocalDate fecha, LocalTime hora, SeccionResumen seccion, List<AlumnoDeSeccion> alumnos) {

        boolean registroHabilitado(LocalTime cierre) {
            return hora.isBefore(cierre);
        }
    }

    private final AsistenciaProperties propiedades;
    private final AsistenciaAlumnoRepository registros;
    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final ConsultaMatriculaService matriculas;
    private final ClasesDocenteService clasesDocente;
    private final DirectorioDocentes directorio;
    private final ContextoUsuario contexto;
    private final Clock reloj;

    AsistenciaSeccionService(AsistenciaProperties propiedades, AsistenciaAlumnoRepository registros, AnioEscolarService anios,
                             CatalogoAcademicoService catalogo, ConsultaMatriculaService matriculas,
                             ClasesDocenteService clasesDocente, DirectorioDocentes directorio, ContextoUsuario contexto,
                             Clock reloj) {
        this.propiedades = propiedades;
        this.registros = registros;
        this.anios = anios;
        this.catalogo = catalogo;
        this.matriculas = matriculas;
        this.clasesDocente = clasesDocente;
        this.directorio = directorio;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    /** La sección que el docente tiene a cargo como tutor este año (paso 3). Sin tutoría: 403 (FA-3). */
    @Transactional(readOnly = true)
    public MiSeccionDto miSeccion() {
        DocenteResumen docente = docenteActual();
        ZonedDateTime local = ahora();
        AnioEscolarResumen anio = anioLectivo(local.toLocalDate());
        SeccionResumen seccion = catalogo.secciones(anio.id()).stream()
                .filter(s -> docente.id().equals(s.tutorDocenteId())).findFirst()
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE,
                        "No tienes una sección a cargo como tutor este año."));
        return new MiSeccionDto(codigo(seccion), seccion.nombre(), matriculas.alumnosDeSeccion(seccion.id()).size());
    }

    /** Pasos 2 a 4: la sección con sus alumnos y lo ya registrado hoy; con la jornada cerrada queda en consulta. */
    @Transactional(readOnly = true)
    public AsistenciaSeccionDto consultar(String codigoSeccion) {
        Contexto c = resolver(codigoSeccion);
        return aDto(c, existentes(c));
    }

    /**
     * Pasos 5 a 8: guarda el estado inicial de todos los alumnos pendientes, o ninguno. Faltan alumnos o uno no es de la
     * sección: 400 con la lista (FA-1) y no se guarda nada; la jornada cerrada: 422 (FA-4); un alumno que ya tiene
     * registro, por ejemplo porque otra solicitud ganó la carrera: 409 con los registros vigentes, sin sobrescribir ni
     * guardar nada del lote (FA-2, FA-5).
     */
    @Transactional
    public AsistenciaSeccionDto registrar(String codigoSeccion, List<ItemAsistencia> items) {
        Contexto c = resolver(codigoSeccion);
        if (!c.registroHabilitado(propiedades.schoolDayClosingTime())) {
            throw new ExcepcionNegocio(CodigoError.JORNADA_CERRADA, mensajeCierre());
        }
        if (c.alumnos().isEmpty()) {
            throw new ExcepcionNegocio(CodigoError.SECCION_SIN_ALUMNOS, "La sección " + c.seccion().nombre()
                    + " no tiene alumnos matriculados: no se crean registros.");
        }
        Map<String, AlumnoDeSeccion> deLaSeccion = c.alumnos().stream()
                .collect(Collectors.toMap(AlumnoDeSeccion::alumnoId, Function.identity()));
        Map<String, AsistenciaAlumno> yaRegistrados = existentes(c);

        validarLote(items, deLaSeccion, yaRegistrados);

        // Un alumno del lote que ya tiene registro: no se sobrescribe y no se guarda nada (FA-2, FA-5)
        List<ItemAsistencia> conRegistro = items.stream().filter(i -> yaRegistrados.containsKey(i.alumnoId())).toList();
        if (!conRegistro.isEmpty()) {
            throw conflicto(c, yaRegistrados);
        }
        for (ItemAsistencia item : items) {
            if (registros.insertarSiNoExiste(item.alumnoId(), c.fecha(), item.estado().name()) == 0) {
                // Otra solicitud lo registró entre la lectura y este insert: se revierte el lote completo
                throw conflicto(c, existentes(c));
            }
        }
        return aDto(c, existentes(c));
    }

    /** FA-1: todos los pendientes con un estado, ninguno repetido y todos de la sección. Si no, 400 con la lista. */
    private void validarLote(List<ItemAsistencia> items, Map<String, AlumnoDeSeccion> deLaSeccion,
                             Map<String, AsistenciaAlumno> yaRegistrados) {
        List<Map<String, String>> errores = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (ItemAsistencia item : items) {
            if (!deLaSeccion.containsKey(item.alumnoId())) {
                errores.add(Map.of("campo", "asistencias." + item.alumnoId(), "mensaje", "El alumno no pertenece a la sección"));
            } else if (!vistos.add(item.alumnoId())) {
                errores.add(Map.of("campo", "asistencias." + item.alumnoId(), "mensaje", "El alumno aparece más de una vez"));
            }
        }
        if (errores.isEmpty()) {
            for (AlumnoDeSeccion alumno : deLaSeccion.values()) {
                if (!yaRegistrados.containsKey(alumno.alumnoId()) && !vistos.contains(alumno.alumnoId())) {
                    errores.add(Map.of("campo", "asistencias." + alumno.alumnoId(),
                            "mensaje", "Indica el estado de " + alumno.apellidos() + ", " + alumno.nombres()));
                }
            }
        }
        if (!errores.isEmpty()) {
            boolean incompleto = errores.stream().anyMatch(e -> e.get("mensaje").startsWith("Indica"));
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, incompleto
                    ? "Faltan " + errores.size() + " alumnos por registrar: no se guardó nada"
                    : "El registro contiene alumnos que no corresponden: no se guardó nada").con("errores", errores);
        }
    }

    private ExcepcionNegocio conflicto(Contexto c, Map<String, AsistenciaAlumno> vigentes) {
        return new ExcepcionNegocio(CodigoError.ASISTENCIA_YA_REGISTRADA,
                "Otra solicitud ya registró la asistencia de algunos alumnos. No se guardó nada: revisa los registros vigentes.")
                .con("registro", aDto(c, vigentes));
    }

    // ---- contexto, alcance y armado ----

    /** Día lectivo, sección y alcance del tutor; los errores salen en el orden en que la ERS los valida. */
    private Contexto resolver(String codigoSeccion) {
        DocenteResumen docente = docenteActual();
        ZonedDateTime local = ahora();
        AnioEscolarResumen anio = anioLectivo(local.toLocalDate());
        SeccionResumen seccion = catalogo.secciones(anio.id()).stream()
                .filter(s -> codigo(s).equalsIgnoreCase(codigoSeccion)).findFirst()
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO,
                        "No existe la sección " + codigoSeccion + " en el año escolar " + anio.anio()));
        if (!docente.id().equals(seccion.tutorDocenteId())) {
            throw new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE,
                    "Solo el tutor de la sección " + seccion.nombre() + " registra su asistencia");
        }
        return new Contexto(local.toLocalDate(), local.toLocalTime(), seccion, matriculas.alumnosDeSeccion(seccion.id()));
    }

    /** Hoy debe ser un día lectivo: dentro de un bimestre del año ACTIVO y con bloques ese día de la semana (S-57). */
    private AnioEscolarResumen anioLectivo(LocalDate fecha) {
        return anios.anioLectivoEn(fecha)
                .filter(a -> clasesDocente.hayJornada(a.anio(), fecha.getDayOfWeek()))
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.DIA_NO_LECTIVO,
                        "Hoy no es un día lectivo: el registro de asistencia no está habilitado para la fecha."));
    }

    private Map<String, AsistenciaAlumno> existentes(Contexto c) {
        List<String> ids = c.alumnos().stream().map(AlumnoDeSeccion::alumnoId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return registros.findByFechaAndAlumnoIdIn(c.fecha(), ids).stream()
                .collect(Collectors.toMap(AsistenciaAlumno::getAlumnoId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private AsistenciaSeccionDto aDto(Contexto c, Map<String, AsistenciaAlumno> existentes) {
        List<AlumnoAsistenciaDto> alumnos = c.alumnos().stream().map(a -> {
            AsistenciaAlumno r = existentes.get(a.alumnoId());
            return new AlumnoAsistenciaDto(a.alumnoId(), a.codigo(), a.apellidos(), a.nombres(),
                    r == null ? null : r.getId(), r == null ? null : r.getEstado());
        }).toList();
        int puntual = contar(alumnos, EstadoAsistencia.PUNTUAL);
        int tardanza = contar(alumnos, EstadoAsistencia.TARDANZA);
        int falta = contar(alumnos, EstadoAsistencia.FALTA);
        TotalesAsistenciaDto totales = new TotalesAsistenciaDto(puntual, tardanza, falta, alumnos.size() - puntual - tardanza - falta);
        boolean habilitado = c.registroHabilitado(propiedades.schoolDayClosingTime());
        String mensaje = !habilitado ? mensajeCierre()
                : c.alumnos().isEmpty() ? "La sección " + c.seccion().nombre() + " no tiene alumnos matriculados." : null;
        return new AsistenciaSeccionDto(codigo(c.seccion()), c.seccion().nombre(), c.fecha(),
                reloj.instant().atZone(reloj.getZone()).toOffsetDateTime(), habilitado, mensaje,
                propiedades.studentLateCutoffTime(), alumnos, totales);
    }

    private static int contar(List<AlumnoAsistenciaDto> alumnos, EstadoAsistencia estado) {
        return (int) alumnos.stream().filter(a -> a.estado() == estado).count();
    }

    private String mensajeCierre() {
        return "La jornada cerró a las " + HORA.format(propiedades.schoolDayClosingTime())
                + ": el registro de asistencia ya no está habilitado.";
    }

    /** «3° A» → «3A»: el código que lleva la URL de la sección. */
    private static String codigo(SeccionResumen s) {
        return s.numeroGrado() + s.letra();
    }

    private ZonedDateTime ahora() {
        return reloj.instant().atZone(reloj.getZone());
    }

    private DocenteResumen docenteActual() {
        String usuarioId = contexto.idUsuarioActual().orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO));
        return directorio.docenteDeUsuario(usuarioId).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo un docente tutor registra la asistencia de alumnos"));
    }
}
