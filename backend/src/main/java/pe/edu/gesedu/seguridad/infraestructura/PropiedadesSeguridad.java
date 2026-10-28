package pe.edu.gesedu.seguridad.infraestructura;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de seguridad ({@code gesedu.seguridad.*}). Sin secretos en el código: la clave llega por
 * {@code GESEDU_JWT_SECRET}.
 *
 * @param jwtSecreto         clave HS256 del JWT, mínimo 32 caracteres (256 bits)
 * @param jwtMinutos         vigencia del token de acceso (arquitectura DA-04: 15)
 * @param maxIntentos        intentos fallidos consecutivos que bloquean la cuenta (RNF-06: 5)
 * @param bloqueoMinutos     duración del bloqueo temporal (S-03: 15)
 * @param refreshDias        vigencia del refresh token rotativo (S-06: 7)
 * @param cookieSecure       marca la cookie de refresh como {@code Secure} (activa también en localhost)
 * @param limiteAuthPorMinuto peticiones por minuto y por IP a {@code /auth/*} (inicio, refresco y recuperación)
 */
@Validated
@ConfigurationProperties("gesedu.seguridad")
public record PropiedadesSeguridad(
        @NotBlank @Size(min = 32, message = "debe tener al menos 32 caracteres (256 bits)") String jwtSecreto,
        @Min(1) int jwtMinutos,
        @Min(1) int maxIntentos,
        @Min(1) int bloqueoMinutos,
        @Min(1) int refreshDias,
        boolean cookieSecure,
        @Min(1) int limiteAuthPorMinuto) {
}
