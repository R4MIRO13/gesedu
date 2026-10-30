package pe.edu.gesedu.transversal.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI en {@code /v3/api-docs} y Swagger UI en {@code /swagger-ui.html}, con el esquema bearer
 * (botón «Authorize»: pegar el {@code tokenAcceso} del inicio de sesión) y un grupo por módulo.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_BEARER = "bearer";

    @Bean
    OpenAPI gesedu() {
        return new OpenAPI()
                .info(new Info().title("GESEDU API").version("v1")
                        .description("Sistema web de gestión integral de un colegio de secundaria. "
                                + "Errores en formato RFC 9457 (application/problem+json) con la propiedad `codigo`."))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER));
    }

    @Bean
    GroupedOpenApi grupoSeguridad() {
        return GroupedOpenApi.builder().group("1-seguridad").displayName("Seguridad")
                .pathsToMatch("/api/v1/auth/**", "/api/v1/usuarios/**", "/api/v1/alumnos/*", "/api/v1/apoderados/*")
                .pathsToExclude("/api/v1/alumnos/busqueda").build();
    }

    @Bean
    GroupedOpenApi grupoHorarios() {
        return GroupedOpenApi.builder().group("2-horarios").displayName("Horarios")
                .pathsToMatch("/api/v1/anios-escolares/*/bloques/**", "/api/v1/horarios/**").build();
    }

    @Bean
    GroupedOpenApi grupoAcademico() {
        return GroupedOpenApi.builder().group("6-academico").displayName("Académico")
                .pathsToMatch("/api/v1/anios-escolares", "/api/v1/anios-escolares/*", "/api/v1/anios-escolares/validaciones",
                        "/api/v1/anios-escolares/*/cursos/**", "/api/v1/anios-escolares/*/grados/**",
                        "/api/v1/anios-escolares/*/docentes", "/api/v1/cursos/**", "/api/v1/grados/**",
                        "/api/v1/secciones/**")
                .build();
    }

    @Bean
    GroupedOpenApi grupoAuditoria() {
        return GroupedOpenApi.builder().group("3-auditoria").displayName("Auditoría")
                .pathsToMatch("/api/v1/auditoria/**").build();
    }

    @Bean
    GroupedOpenApi grupoMatricula() {
        return GroupedOpenApi.builder().group("4-matricula").displayName("Matrícula")
                .pathsToMatch("/api/v1/solicitudes-matricula/**", "/api/v1/matriculas/**").build();
    }

    @Bean
    GroupedOpenApi grupoPagos() {
        return GroupedOpenApi.builder().group("5-pagos").displayName("Pagos")
                .pathsToMatch("/api/v1/anios-escolares/*/cobros", "/api/v1/obligaciones/**",
                        "/api/v1/comprobantes/**", "/api/v1/alumnos/*/obligaciones",
                        "/api/v1/alumnos/*/historial-financiero")
                .build();
    }
}
