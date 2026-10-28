package pe.edu.gesedu.transversal.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj institucional. Toda fecha y hora de negocio se obtiene de este {@link Clock} (zona America/Lima),
 * nunca de la hora del dispositivo del usuario ni de {@code Instant.now()} sin reloj.
 */
@Configuration
public class RelojConfig {

    @Bean
    Clock reloj(@Value("${gesedu.asistencia.time-zone}") String zonaHoraria) {
        return Clock.system(ZoneId.of(zonaHoraria));
    }
}
