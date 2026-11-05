package pe.edu.gesedu.transversal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/** El reloj de la demo: arranca en la fecha configurada y avanza en tiempo real (perfil demo). */
class RelojDemoConfigTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2027-03-15T07:40:00-05:00");

    @Test
    void arrancaEnLaFechaDeLaDemoEnLaZonaInstitucional() {
        Clock reloj = new RelojDemoConfig().reloj("America/Lima", INICIO);

        assertThat(reloj.getZone()).isEqualTo(LIMA);
        assertThat(Duration.between(INICIO.toInstant(), reloj.instant()).abs()).isLessThan(Duration.ofSeconds(2));
        assertThat(reloj.instant().atZone(LIMA).toLocalDate().toString()).isEqualTo("2027-03-15");
    }

    @Test
    void esUnDesfaseFijoSobreElRelojDelSistemaYAvanzaConElTiempo() throws Exception {
        Clock sistema = Clock.fixed(Instant.parse("2026-11-04T20:00:00Z"), LIMA);
        Clock reloj = RelojDemoConfig.relojDesde(sistema, INICIO);

        assertThat(reloj.instant()).isEqualTo(INICIO.toInstant());

        // Con un reloj de sistema que avanza, el de la demo avanza igual (nunca se queda fijo ni retrocede)
        Clock real = Clock.system(LIMA);
        Clock avanzando = RelojDemoConfig.relojDesde(real, INICIO);
        Instant antes = avanzando.instant();
        Thread.sleep(60);
        assertThat(Duration.between(antes, avanzando.instant())).isBetween(Duration.ofMillis(40), Duration.ofSeconds(2));
        assertThat(Duration.between(INICIO.toInstant(), antes).abs()).isLessThan(Duration.ofSeconds(2));
        assertThat(Duration.between(antes, avanzando.instant()).toMillis()).isGreaterThan(0).isCloseTo(60L, within(500L));
    }
}
