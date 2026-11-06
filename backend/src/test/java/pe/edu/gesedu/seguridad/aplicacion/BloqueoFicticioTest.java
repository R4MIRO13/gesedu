package pe.edu.gesedu.seguridad.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.seguridad.infraestructura.PropiedadesSeguridad;

/** Bloqueo simulado de correos sin cuenta (S-84): misma regla que el real y memoria acotada. */
class BloqueoFicticioTest {

    private final Clock reloj = Clock.fixed(Instant.parse("2027-03-15T12:00:00Z"), ZoneOffset.UTC);

    private BloqueoFicticio nuevo() {
        return new BloqueoFicticio(new PropiedadesSeguridad("x".repeat(32), 15, 5, 15, 7, false, 100), reloj);
    }

    @Test
    void bloqueaAlQuintoIntentoPorCorreoSinDistinguirMayusculas() {
        BloqueoFicticio bloqueo = nuevo();
        for (int i = 1; i <= 4; i++) {
            assertThat(bloqueo.registrarIntento(i % 2 == 0 ? "Nadie@x.pe" : " nadie@x.pe ")).isEmpty();
        }
        assertThat(bloqueo.registrarIntento("NADIE@x.pe")).contains(reloj.instant().plusSeconds(15 * 60));
        // Bloqueado: sigue igual y no se alarga
        assertThat(bloqueo.registrarIntento("nadie@x.pe")).contains(reloj.instant().plusSeconds(15 * 60));
        assertThat(bloqueo.registrarIntento("otro@x.pe")).isEmpty();
    }

    @Test
    void laCacheRespetaElTopeAunqueSeProbenMuchosCorreos() {
        BloqueoFicticio bloqueo = nuevo();
        for (int i = 0; i < BloqueoFicticio.MAXIMO_CORREOS * 3; i++) {
            bloqueo.registrarIntento("correo" + i + "@x.pe");
        }
        assertThat(bloqueo.tamano()).isLessThanOrEqualTo(BloqueoFicticio.MAXIMO_CORREOS);
    }
}
