package pe.edu.gesedu.transversal.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * El reloj adelantado de la demo (2027) solo puede existir con el perfil {@code demo}: con cualquier otro perfil
 * (local, aws, sin perfil) el único {@link Clock} es el del sistema en la zona institucional.
 */
class RelojPorPerfilTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private ApplicationContextRunner conPerfil(String perfiles) {
        return new ApplicationContextRunner()
                // La aplicación real convierte las propiedades (OffsetDateTime) con el servicio de conversión de Boot
                .withInitializer(ctx -> ctx.getBeanFactory().setConversionService(new ApplicationConversionService()))
                .withUserConfiguration(RelojConfig.class, RelojDemoConfig.class)
                .withPropertyValues(
                        "gesedu.asistencia.time-zone=America/Lima",
                        "gesedu.demo.reloj-inicio=2027-03-15T07:40:00-05:00",
                        "spring.profiles.active=" + perfiles);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "aws", "local,aws", "prod", "test"})
    void sinElPerfilDemoElRelojEsElDelSistema(String perfiles) {
        conPerfil(perfiles).run(contexto -> {
            assertThat(contexto).hasSingleBean(Clock.class);
            assertThat(contexto).doesNotHaveBean(RelojDemoConfig.class);
            assertThat(contexto.getBean(Clock.class)).isEqualTo(Clock.system(LIMA));
        });
    }

    @Test
    void sinNingunPerfilElRelojEsElDelSistema() {
        new ApplicationContextRunner()
                .withUserConfiguration(RelojConfig.class, RelojDemoConfig.class)
                .withPropertyValues("gesedu.asistencia.time-zone=America/Lima",
                        "gesedu.demo.reloj-inicio=2027-03-15T07:40:00-05:00")
                .run(contexto -> {
                    assertThat(contexto).hasSingleBean(Clock.class);
                    assertThat(contexto.getBean(Clock.class)).isEqualTo(Clock.system(LIMA));
                });
    }

    @Test
    void conElPerfilDemoElRelojEsElDeLaDemo() {
        conPerfil("local,demo").run(contexto -> {
            assertThat(contexto).hasSingleBean(Clock.class);
            assertThat(contexto).hasSingleBean(RelojDemoConfig.class);
            Clock reloj = contexto.getBean(Clock.class);
            assertThat(reloj).isNotEqualTo(Clock.system(LIMA));
            assertThat(reloj.instant().atZone(LIMA).toLocalDate().toString()).isEqualTo("2027-03-15");
        });
    }
}
