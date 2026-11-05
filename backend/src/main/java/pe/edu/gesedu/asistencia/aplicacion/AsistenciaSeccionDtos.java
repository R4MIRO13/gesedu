package pe.edu.gesedu.asistencia.aplicacion;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import pe.edu.gesedu.asistencia.dominio.EstadoAsistencia;

/** DTO de la asistencia de alumnos de una sección (UC-08, UC-09). */
public final class AsistenciaSeccionDtos {

    private AsistenciaSeccionDtos() {
    }

    /** Un alumno de la sección y su asistencia de hoy; {@code estado} es nulo si aún no tiene registro (no es falta). */
    public record AlumnoAsistenciaDto(String alumnoId, String codigo, String apellidos, String nombres, String registroId,
                                      EstadoAsistencia estado) {
    }

    /** Totales de la vista del día, calculados al consultar (no se guardan). */
    public record TotalesAsistenciaDto(int puntual, int tardanza, int falta, int pendientes) {
    }

    /**
     * La sección del tutor con la asistencia del día. {@code registroHabilitado} es falso con la jornada cerrada: la
     * pantalla queda en consulta y {@code mensaje} lo explica. {@code limiteTardanza} es la hora hasta la que el tutor
     * puede cambiar una falta a tardanza (UC-09) y {@code actualizacionTardanzaHabilitada} dice si, a la hora del
     * servidor, todavía puede hacerlo.
     */
    public record AsistenciaSeccionDto(String codigo, String nombre, LocalDate fecha, OffsetDateTime horaServidor,
                                       boolean registroHabilitado, String mensaje, LocalTime limiteTardanza,
                                       boolean actualizacionTardanzaHabilitada, List<AlumnoAsistenciaDto> alumnos,
                                       TotalesAsistenciaDto totales) {
    }

    /** Una asistencia de alumno tras cambiarla (UC-09); {@code modificado} es falso si ya estaba en TARDANZA. */
    public record RegistroAlumnoDto(String id, String alumnoId, LocalDate fecha, EstadoAsistencia estado,
                                    OffsetDateTime actualizadoEn, boolean modificado) {
    }

    /** La sección que el docente tiene a cargo como tutor (para llegar a su pantalla de asistencia). */
    public record MiSeccionDto(String codigo, String nombre, int alumnos) {
    }

    /** Estado inicial de un alumno en el pase de lista. */
    public record ItemAsistencia(String alumnoId, EstadoAsistencia estado) {
    }
}
