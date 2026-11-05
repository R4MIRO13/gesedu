package pe.edu.gesedu.asistencia.aplicacion;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import pe.edu.gesedu.asistencia.dominio.EstadoAsistencia;

/** DTO de la marcación docente (UC-07). Las horas viajan en la zona institucional (-05:00). */
public final class AsistenciaDocenteDtos {

    private AsistenciaDocenteDtos() {
    }

    /** Una asistencia diaria del docente; {@code horaRegistro} es nula solo en la FALTA del cierre diario. */
    public record RegistroDto(String id, LocalDate fecha, OffsetDateTime horaRegistro, EstadoAsistencia estado) {
    }

    /**
     * Lo que el docente necesita para marcar: la hora del servidor (nunca la del dispositivo), su primera clase de hoy y
     * hasta cuándo es puntual. {@code motivo} es el código del error que daría marcar ahora ({@code null} si puede) y
     * {@code mensaje} su explicación.
     */
    public record EstadoHoyDto(LocalDate fecha, OffsetDateTime horaServidor, LocalTime primerBloque, LocalTime limitePuntual,
                               int toleranciaMinutos, LocalTime cierreJornada, boolean puedeMarcar, String motivo,
                               String mensaje, RegistroDto registro) {
    }
}
