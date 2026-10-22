package pe.edu.gesedu.seguridad.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.seguridad.aplicacion.AutenticacionService;
import pe.edu.gesedu.seguridad.aplicacion.SesionIniciada;

/** Inicio de sesión (RF-52). Público: lo protege el bloqueo por intentos fallidos (RNF-06). */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AutenticacionService autenticacion;

    AuthController(AutenticacionService autenticacion) {
        this.autenticacion = autenticacion;
    }

    @PostMapping("/inicio-sesion")
    SesionIniciada iniciarSesion(@Valid @RequestBody InicioSesionRequest solicitud) {
        return autenticacion.iniciarSesion(solicitud.correo(), solicitud.contrasena());
    }
}
