package pe.edu.gesedu.asistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.asistencia.dominio.ClasificadorAsistenciaDocente;
import pe.edu.gesedu.asistencia.dominio.EstadoAsistencia;

/** RF-13, RF-19, UC-07 paso 6: puntual hasta el inicio de la primera clase más la tolerancia, con el límite inclusivo. */
class ClasificadorAsistenciaDocenteTest {

    private static final LocalTime INICIO = LocalTime.of(7, 45);
    private static final int TOLERANCIA = 5;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static EstadoAsistencia clasificar(String hora) {
        return ClasificadorAsistenciaDocente.clasificar(LocalTime.parse(hora), INICIO, TOLERANCIA);
    }

    @Test
    void antesDelInicioDeLaClaseEsPuntual() {
        assertThat(clasificar("06:30")).isEqualTo(EstadoAsistencia.PUNTUAL);
        assertThat(clasificar("07:44:59")).isEqualTo(EstadoAsistencia.PUNTUAL);
    }

    @Test
    void elLimiteEsInclusivo() {
        assertThat(ClasificadorAsistenciaDocente.limitePuntual(INICIO, TOLERANCIA)).isEqualTo(LocalTime.of(7, 50));
        assertThat(clasificar("07:45")).isEqualTo(EstadoAsistencia.PUNTUAL);
        assertThat(clasificar("07:50:00")).isEqualTo(EstadoAsistencia.PUNTUAL);
    }

    @Test
    void unSegundoDespuesDelLimiteEsTardanza() {
        assertThat(clasificar("07:50:01")).isEqualTo(EstadoAsistencia.TARDANZA);
        assertThat(clasificar("08:30")).isEqualTo(EstadoAsistencia.TARDANZA);
    }

    @Test
    void conToleranciaCeroElLimiteEsElInicioDeLaClase() {
        assertThat(ClasificadorAsistenciaDocente.clasificar(LocalTime.of(7, 45), INICIO, 0)).isEqualTo(EstadoAsistencia.PUNTUAL);
        assertThat(ClasificadorAsistenciaDocente.clasificar(LocalTime.of(7, 45, 1), INICIO, 0)).isEqualTo(EstadoAsistencia.TARDANZA);
    }

    @Test
    void conUnRelojFijoLaHoraLocalEsLaDeLimaYNoLaDeUtc() {
        // 12:50 UTC = 07:50 en Lima (UTC-5): límite inclusivo, puntual. Con la hora UTC sería tardanza.
        Clock reloj = Clock.fixed(Instant.parse("2027-03-15T12:50:00Z"), ZoneOffset.UTC).withZone(LIMA);
        LocalTime hora = reloj.instant().atZone(reloj.getZone()).toLocalTime();

        assertThat(hora).isEqualTo(LocalTime.of(7, 50));
        assertThat(ClasificadorAsistenciaDocente.clasificar(hora, INICIO, TOLERANCIA)).isEqualTo(EstadoAsistencia.PUNTUAL);
        LocalTime horaUtc = reloj.instant().atZone(ZoneOffset.UTC).toLocalTime();
        assertThat(ClasificadorAsistenciaDocente.clasificar(horaUtc, INICIO, TOLERANCIA)).isEqualTo(EstadoAsistencia.TARDANZA);
    }
}
