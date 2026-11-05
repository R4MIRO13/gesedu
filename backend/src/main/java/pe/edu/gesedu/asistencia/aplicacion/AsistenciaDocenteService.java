package pe.edu.gesedu.asistencia.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.asistencia.aplicacion.AsistenciaDocenteDtos.EstadoHoyDto;
import pe.edu.gesedu.asistencia.aplicacion.AsistenciaDocenteDtos.RegistroDto;
import pe.edu.gesedu.asistencia.dominio.AsistenciaDocente;
import pe.edu.gesedu.asistencia.dominio.ClasificadorAsistenciaDocente;
import pe.edu.gesedu.asistencia.dominio.EstadoAsistencia;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaDocenteRepository;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaProperties;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.horarios.aplicacion.ClasesDocenteService;
import pe.edu.gesedu.horarios.aplicacion.ClasesDocenteService.ClasesEnDia;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;

/**
 * UC-07, Registrar asistencia de docente (RF-13, RF-18 a RF-20). El docente marca su propia llegada; el servidor toma el
 * instante del {@link Clock} institucional (nunca la hora del dispositivo), valida el día y la jornada, y la clasifica
 * contra el inicio de su primera clase del día más la tolerancia configurada. Un docente tiene como máximo una
 * asistencia por fecha, también ante solicitudes simultáneas. Quien tiene una sección como tutor marca igual: la tutoría
 * es una asignación, no un rol distinto.
 */
@Service
public class AsistenciaDocenteService {

    private static final int MAX_DIAS_HISTORIAL = 366;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Resultado de aplicar las reglas del paso 3 al 5 a un instante; {@code bloqueo} es nulo si se puede marcar. */
    private record Evaluacion(LocalDate fecha, LocalTime hora, ClasesEnDia clases, AsistenciaDocente existente,
                              CodigoError bloqueo, String mensaje) {
    }

    private final AsistenciaProperties propiedades;
    private final AsistenciaDocenteRepository registros;
    private final AnioEscolarService anios;
    private final ClasesDocenteService clasesDocente;
    private final DirectorioDocentes directorio;
    private final ContextoUsuario contexto;
    private final Clock reloj;

    AsistenciaDocenteService(AsistenciaProperties propiedades, AsistenciaDocenteRepository registros, AnioEscolarService anios,
                             ClasesDocenteService clasesDocente, DirectorioDocentes directorio, ContextoUsuario contexto,
                             Clock reloj) {
        this.propiedades = propiedades;
        this.registros = registros;
        this.anios = anios;
        this.clasesDocente = clasesDocente;
        this.directorio = directorio;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    /** Pantalla «Mi asistencia»: la hora del servidor, mi primera clase de hoy, el límite puntual y si puedo marcar. */
    @Transactional(readOnly = true)
    public EstadoHoyDto hoy() {
        DocenteResumen docente = docenteActual();
        Instant ahora = reloj.instant();
        Evaluacion e = evaluar(docente, ahora);
        LocalTime primero = e.clases() == null ? null : e.clases().primerBloque();
        int tolerancia = propiedades.teacherLateThresholdMinutes();
        LocalTime limite = primero == null ? null : ClasificadorAsistenciaDocente.limitePuntual(primero, tolerancia);
        return new EstadoHoyDto(e.fecha(), enZona(ahora), primero, limite, tolerancia, propiedades.schoolDayClosingTime(),
                e.bloqueo() == null, e.bloqueo() == null ? null : e.bloqueo().name(), e.mensaje(),
                e.existente() == null ? null : aDto(e.existente()));
    }

    /**
     * Pasos 2 a 8: registra la llegada del docente. Con un registro previo responde 409 con ese registro y no crea ni
     * sobrescribe nada (FA-1, C-39); sin clases o sin horario publicado, 422 {@code SIN_CLASES_PROGRAMADAS} (FA-2); en un
     * día no lectivo o con la jornada cerrada, 422 (FA-3).
     */
    @Transactional
    public RegistroDto marcar() {
        DocenteResumen docente = docenteActual();
        Instant ahora = reloj.instant();
        Evaluacion e = evaluar(docente, ahora);
        if (e.bloqueo() == CodigoError.ASISTENCIA_YA_REGISTRADA) {
            throw yaRegistrada(e.existente());
        }
        if (e.bloqueo() != null) {
            throw new ExcepcionNegocio(e.bloqueo(), e.mensaje());
        }
        EstadoAsistencia estado = ClasificadorAsistenciaDocente.clasificar(e.hora(), e.clases().primerBloque(),
                propiedades.teacherLateThresholdMinutes());
        int creadas = registros.insertarSiNoExiste(docente.id(), e.fecha(), ahora, estado.name());
        AsistenciaDocente registro = registros.findByDocenteIdAndFecha(docente.id(), e.fecha()).orElseThrow();
        if (creadas == 0) {
            // Otra solicitud del mismo docente ganó la carrera: se muestra su registro, sin sobrescribirlo (FA-1)
            throw yaRegistrada(registro);
        }
        return aDto(registro);
    }

    /** Mi historial en el rango (por omisión, los últimos 30 días), del más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public List<RegistroDto> mias(LocalDate desde, LocalDate hasta) {
        DocenteResumen docente = docenteActual();
        LocalDate fin = hasta != null ? hasta : ahoraLocal().toLocalDate();
        LocalDate inicio = desde != null ? desde : fin.minusDays(30);
        if (inicio.isAfter(fin)) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA, "La fecha inicial no puede ser posterior a la final");
        }
        if (inicio.plusDays(MAX_DIAS_HISTORIAL).isBefore(fin)) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA, "El rango no puede superar " + MAX_DIAS_HISTORIAL + " días");
        }
        return registros.findByDocenteIdAndFechaBetweenOrderByFechaDesc(docente.id(), inicio, fin).stream()
                .map(this::aDto).toList();
    }

    /** Pasos 3 a 5 en el orden de la ERS: día lectivo y jornada abierta, registro previo y luego clases del docente. */
    private Evaluacion evaluar(DocenteResumen docente, Instant ahora) {
        ZonedDateTime local = ahora.atZone(reloj.getZone());
        LocalDate fecha = local.toLocalDate();
        LocalTime hora = local.toLocalTime();
        Optional<AnioEscolarResumen> anio = anios.anioLectivoEn(fecha);
        ClasesEnDia clases = anio.map(a -> clasesDocente.clasesDelDocente(a.anio(), docente.id(), fecha.getDayOfWeek()))
                .orElse(null);
        AsistenciaDocente existente = registros.findByDocenteIdAndFecha(docente.id(), fecha).orElse(null);

        if (clases == null || !clases.diaConBloques()) {
            return new Evaluacion(fecha, hora, clases, existente, CodigoError.DIA_NO_LECTIVO,
                    "Hoy no es un día lectivo: no se registra asistencia.");
        }
        if (!hora.isBefore(propiedades.schoolDayClosingTime())) {
            return new Evaluacion(fecha, hora, clases, existente, CodigoError.JORNADA_CERRADA,
                    "La jornada cerró a las " + HORA.format(propiedades.schoolDayClosingTime())
                            + ": ya no se puede marcar la asistencia.");
        }
        if (existente != null) {
            return new Evaluacion(fecha, hora, clases, existente, CodigoError.ASISTENCIA_YA_REGISTRADA,
                    "Ya registraste tu asistencia de hoy.");
        }
        if (!clases.horarioPublicado()) {
            return new Evaluacion(fecha, hora, clases, null, CodigoError.SIN_CLASES_PROGRAMADAS,
                    "Aún no hay un horario publicado para este año: no se puede registrar asistencia.");
        }
        if (!clases.tieneClases()) {
            return new Evaluacion(fecha, hora, clases, null, CodigoError.SIN_CLASES_PROGRAMADAS,
                    "No tienes clases programadas hoy: no se registra asistencia.");
        }
        return new Evaluacion(fecha, hora, clases, null, null, null);
    }

    private ExcepcionNegocio yaRegistrada(AsistenciaDocente existente) {
        return new ExcepcionNegocio(CodigoError.ASISTENCIA_YA_REGISTRADA, "Ya registraste tu asistencia de hoy.")
                .con("registro", aDto(existente));
    }

    private RegistroDto aDto(AsistenciaDocente a) {
        return new RegistroDto(a.getId(), a.getFecha(), a.getHoraRegistro() == null ? null : enZona(a.getHoraRegistro()),
                a.getEstado());
    }

    private OffsetDateTime enZona(Instant instante) {
        return instante.atZone(reloj.getZone()).toOffsetDateTime();
    }

    private ZonedDateTime ahoraLocal() {
        return reloj.instant().atZone(reloj.getZone());
    }

    private DocenteResumen docenteActual() {
        String usuarioId = contexto.idUsuarioActual().orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO));
        return directorio.docenteDeUsuario(usuarioId).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo un docente marca su asistencia"));
    }
}
