package pe.edu.gesedu.asistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import pe.edu.gesedu.asistencia.infraestructura.AsistenciaProperties;
import pe.edu.gesedu.asistencia.infraestructura.ConfiguracionAsistencia;

/** DA-10: con parámetros de asistencia inválidos el contexto no levanta y el error nombra la propiedad. */
class AsistenciaPropertiesTest {

    private final ApplicationContextRunner ejecutor = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracionAsistencia.class);

    private ApplicationContextRunner conValoresValidos() {
        return ejecutor.withPropertyValues(
                "gesedu.asistencia.time-zone=America/Lima",
                "gesedu.asistencia.teacher-late-threshold-minutes=5",
                "gesedu.asistencia.student-late-cutoff-time=08:30",
                "gesedu.asistencia.school-day-closing-time=15:30");
    }

    @Test
    void valoresValidosLevantanElContexto() {
        conValoresValidos().run(contexto -> {
            assertThat(contexto).hasNotFailed();
            AsistenciaProperties p = contexto.getBean(AsistenciaProperties.class);
            assertThat(p.teacherLateThresholdMinutes()).isEqualTo(5);
            assertThat(p.studentLateCutoffTime()).isEqualTo(LocalTime.of(8, 30));
            assertThat(p.schoolDayClosingTime()).isEqualTo(LocalTime.of(15, 30));
            assertThat(p.timeZone()).isEqualTo(ZoneId.of("America/Lima"));
        });
    }

    @Test
    void corteIgualOPosteriorAlCierreImpideElArranque() {
        conValoresValidos().withPropertyValues("gesedu.asistencia.student-late-cutoff-time=15:30")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).rootCause().hasMessageContaining(
                            "student-late-cutoff-time debe ser anterior a school-day-closing-time");
                });
        conValoresValidos().withPropertyValues("gesedu.asistencia.student-late-cutoff-time=16:00")
                .run(contexto -> assertThat(contexto).hasFailed());
    }

    @Test
    void umbralNegativoImpideElArranque() {
        conValoresValidos().withPropertyValues("gesedu.asistencia.teacher-late-threshold-minutes=-1")
                .run(contexto -> assertThat(contexto).hasFailed());
    }

    @Test
    void zonaHorariaInvalidaOFaltanteImpideElArranque() {
        conValoresValidos().withPropertyValues("gesedu.asistencia.time-zone=Marte/Olimpo")
                .run(contexto -> assertThat(contexto).hasFailed());
        ejecutor.withPropertyValues(
                "gesedu.asistencia.teacher-late-threshold-minutes=5",
                "gesedu.asistencia.student-late-cutoff-time=08:30",
                "gesedu.asistencia.school-day-closing-time=15:30")
                .run(contexto -> assertThat(contexto).hasFailed());
    }
}
