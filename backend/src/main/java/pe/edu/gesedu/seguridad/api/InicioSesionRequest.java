package pe.edu.gesedu.seguridad.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/auth/inicio-sesion}. BCrypt solo considera los primeros 72 caracteres. */
public record InicioSesionRequest(
        @NotBlank @Size(max = 254) String correo,
        @NotBlank @Size(max = 72) String contrasena) {
}
