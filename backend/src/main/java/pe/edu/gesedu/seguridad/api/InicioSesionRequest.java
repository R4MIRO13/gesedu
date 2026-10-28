package pe.edu.gesedu.seguridad.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/auth/inicio-sesion}. BCrypt solo considera los primeros 72 caracteres. */
public record InicioSesionRequest(
        @Schema(description = "Correo de la cuenta (el inicio de sesión es solo con correo, C-02)",
                example = "admin@demo.gesedu.pe")
        @NotBlank @Size(max = 254) String correo,
        @Schema(description = "Contraseña de la cuenta", example = "Gesedu2027!", format = "password")
        @NotBlank @Size(max = 72) String contrasena) {
}
