package pe.edu.gesedu.asistencia.infraestructura;

import java.time.LocalTime;
import java.time.ZoneId;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros institucionales de asistencia ({@code gesedu.asistencia.*}, DA-10). Si son inválidos, la
 * aplicación no arranca y Spring Boot explica qué propiedad falló (P-05: la institución define los valores
 * de producción; los de local salen de los wireframes, C-24).
 *
 * @param teacherLateThresholdMinutes minutos de tolerancia tras el inicio de la primera clase (RF-19)
 * @param studentLateCutoffTime       hora límite del pase de lista antes de la cual se puede registrar tardanza (RF-14)
 * @param schoolDayClosingTime        hora de cierre de la jornada; desde ella corre el cierre diario (UC-12, RF-20)
 * @param timeZone                    zona institucional (America/Lima); es también la del {@code Clock}
 */
@Validated
@ConfigurationProperties("gesedu.asistencia")
public record AsistenciaProperties(
        @NotNull @PositiveOrZero Integer teacherLateThresholdMinutes,
        @NotNull LocalTime studentLateCutoffTime,
        @NotNull LocalTime schoolDayClosingTime,
        @NotNull ZoneId timeZone) {

    @AssertTrue(message = "student-late-cutoff-time debe ser anterior a school-day-closing-time")
    public boolean isCorteAnteriorAlCierre() {
        return studentLateCutoffTime == null || schoolDayClosingTime == null
                || studentLateCutoffTime.isBefore(schoolDayClosingTime);
    }
}
