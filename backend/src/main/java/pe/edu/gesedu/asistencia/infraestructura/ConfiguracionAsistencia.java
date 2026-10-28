package pe.edu.gesedu.asistencia.infraestructura;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Activa y valida {@link AsistenciaProperties} al arrancar (DA-10). */
@Configuration
@EnableConfigurationProperties(AsistenciaProperties.class)
public class ConfiguracionAsistencia {
}
