package pe.edu.gesedu.transversal.notificaciones;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de correo ({@code gesedu.notificaciones.*}).
 *
 * @param tipo        {@code smtp} (local, Mailpit) o {@code ses} (perfil aws)
 * @param remitente   dirección «De» (en SES debe estar verificada)
 * @param reintentoMs espera entre intentos fallidos; el envío se intenta 3 veces como máximo (S-16)
 * @param region      región de SES
 */
@Validated
@ConfigurationProperties("gesedu.notificaciones")
public record PropiedadesNotificaciones(
        @NotBlank String tipo,
        @NotBlank String remitente,
        @Min(0) long reintentoMs,
        String region) {
}
