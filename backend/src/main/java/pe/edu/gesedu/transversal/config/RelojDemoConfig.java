package pe.edu.gesedu.transversal.config;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Reloj de la DEMO (perfil {@code demo}; nunca en producción): el año escolar de la semilla es 2027, así que el reloj
 * institucional arranca en {@code gesedu.demo.reloj-inicio} (por omisión, el lunes 15/03/2027 a las 7:40 a. m.) y desde ahí
 * avanza en tiempo real. Es un desfase fijo sobre el reloj del sistema: toda la aplicación sigue leyendo un único
 * {@link Clock} y la hora de la demo nunca retrocede. Para mostrar el cierre de la jornada se reinicia el backend con
 * {@code GESEDU_DEMO_RELOJ_INICIO} unos segundos antes de las 15:30.
 */
@Configuration
@Profile("demo")
public class RelojDemoConfig {

    @Bean
    Clock reloj(@Value("${gesedu.asistencia.time-zone}") String zonaHoraria,
                @Value("${gesedu.demo.reloj-inicio}") OffsetDateTime inicio) {
        return relojDesde(Clock.system(ZoneId.of(zonaHoraria)), inicio);
    }

    /** El reloj del sistema desplazado para que «ahora» sea {@code inicio} en el momento de crearlo. */
    static Clock relojDesde(Clock sistema, OffsetDateTime inicio) {
        return Clock.offset(sistema, Duration.between(sistema.instant(), inicio.toInstant()));
    }
}
