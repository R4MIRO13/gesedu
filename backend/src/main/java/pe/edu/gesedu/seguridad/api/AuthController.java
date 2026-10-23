package pe.edu.gesedu.seguridad.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.seguridad.aplicacion.AutenticacionService;
import pe.edu.gesedu.seguridad.aplicacion.SesionIniciada;
import pe.edu.gesedu.seguridad.aplicacion.UsuarioAutenticado;

/** Inicio de sesión (RF-52). Público: lo protege el bloqueo por intentos fallidos (RNF-06). */
@Tag(name = "Seguridad")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AutenticacionService autenticacion;

    AuthController(AutenticacionService autenticacion) {
        this.autenticacion = autenticacion;
    }

    @Operation(summary = "Inicia sesión y devuelve el token de acceso (JWT de 15 min). Bloquea la cuenta tras 5 fallos")
    @SecurityRequirements
    @PostMapping("/inicio-sesion")
    SesionIniciada iniciarSesion(@Valid @RequestBody InicioSesionRequest solicitud) {
        return autenticacion.iniciarSesion(solicitud.correo(), solicitud.contrasena());
    }

    @Operation(summary = "Datos y permisos del usuario autenticado")
    @GetMapping("/yo")
    @PreAuthorize("isAuthenticated()")
    SesionIniciada.DatosUsuario yo(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return autenticacion.datosUsuarioActual(usuario.id());
    }
}
