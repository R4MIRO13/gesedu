package pe.edu.gesedu.asistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.asistencia.dominio.PlazoTardanza;

/** UC-09, RF-13, RF-15: el cambio de FALTA a TARDANZA se admite hasta StudentLateCutoffTime, inclusive. */
class PlazoTardanzaTest {

    private static final LocalTime LIMITE = LocalTime.of(8, 30);

    @Test
    void antesDelLimiteSeAdmite() {
        assertThat(PlazoTardanza.admite(LocalTime.of(7, 50), LIMITE)).isTrue();
        assertThat(PlazoTardanza.admite(LocalTime.of(8, 29, 59), LIMITE)).isTrue();
    }

    @Test
    void enElLimiteExactoSeAdmite() {
        assertThat(PlazoTardanza.admite(LIMITE, LIMITE)).isTrue();
    }

    @Test
    void unSegundoDespuesDelLimiteNoSeAdmite() {
        assertThat(PlazoTardanza.admite(LocalTime.of(8, 30, 1), LIMITE)).isFalse();
        assertThat(PlazoTardanza.admite(LocalTime.of(15, 30), LIMITE)).isFalse();
    }

    @Test
    void conUnRelojFijoSeUsaLaHoraDeLimaYNoLaDeUtc() {
        // 13:30 UTC = 08:30 en Lima (UTC-5): justo en el límite. En UTC ya habría pasado.
        Clock reloj = Clock.fixed(Instant.parse("2027-03-15T13:30:00Z"), ZoneOffset.UTC).withZone(ZoneId.of("America/Lima"));
        LocalTime horaLima = reloj.instant().atZone(reloj.getZone()).toLocalTime();

        assertThat(PlazoTardanza.admite(horaLima, LIMITE)).isTrue();
        assertThat(PlazoTardanza.admite(reloj.instant().atZone(ZoneOffset.UTC).toLocalTime(), LIMITE)).isFalse();
    }
}
