package pe.edu.gesedu.apoyo;

import java.time.Instant;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Sustituye el {@code Clock} del sistema por un {@link RelojPrueba} en la prueba que la importe con {@code @Import}. */
@TestConfiguration(proxyBeanMethods = false)
public class RelojPruebaConfig {

    @Bean
    @Primary
    RelojPrueba relojDePrueba(@Value("${gesedu.asistencia.time-zone}") String zona) {
        return new RelojPrueba(ZoneId.of(zona), Instant.now());
    }
}
