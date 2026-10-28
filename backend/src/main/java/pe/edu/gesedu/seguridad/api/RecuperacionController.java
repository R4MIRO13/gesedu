package pe.edu.gesedu.seguridad.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.seguridad.aplicacion.RecuperacionContrasenaService;

/**
 * Recuperación de contraseña (UC-24 FA-5, RF-52). Públicos: los protege el límite de peticiones por IP y que
 * la respuesta de la solicitud sea siempre la misma.
 */
@Tag(name = "Seguridad")
@RestController
@RequestMapping("/api/v1/auth/recuperacion")
class RecuperacionController {

    static final String MENSAJE = "Si el correo está registrado, recibirás un enlace para restablecer tu contraseña. "
            + "El enlace vale 30 minutos.";

    record SolicitudRecuperacion(
            @Schema(example = "apoderado@demo.gesedu.pe") @NotBlank @Size(max = 254) String correo) {
    }

    record RespuestaSolicitud(String mensaje) {
    }

    record ConfirmacionRecuperacion(
            @Schema(description = "El token que viene en el enlace del correo") @NotBlank @Size(max = 200) String token,
            @Schema(description = "Mínimo 8 caracteres, con una letra y un número", format = "password")
            @NotBlank @Pattern(regexp = RecuperacionContrasenaService.POLITICA,
                    message = RecuperacionContrasenaService.MENSAJE_POLITICA) String nuevaContrasena) {
    }

    private final RecuperacionContrasenaService recuperacion;

    RecuperacionController(RecuperacionContrasenaService recuperacion) {
        this.recuperacion = recuperacion;
    }

    @Operation(summary = "Pide el enlace de recuperación. Responde 202 con el mismo cuerpo exista o no la cuenta")
    @SecurityRequirements
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    RespuestaSolicitud solicitar(@Valid @RequestBody SolicitudRecuperacion solicitud) {
        recuperacion.solicitar(solicitud.correo());
        return new RespuestaSolicitud(MENSAJE);
    }

    @Operation(summary = "Define la contraseña nueva con el enlace (un solo uso, 30 min). Revoca las sesiones abiertas. "
            + "400 TOKEN_RECUPERACION_INVALIDO si el enlace no sirve")
    @SecurityRequirements
    @PostMapping("/confirmacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmar(@Valid @RequestBody ConfirmacionRecuperacion solicitud) {
        recuperacion.confirmar(solicitud.token(), solicitud.nuevaContrasena());
    }
}
