package pe.edu.gesedu.seguridad.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.seguridad.aplicacion.AutenticacionService;
import pe.edu.gesedu.seguridad.aplicacion.SesionConRefresco;
import pe.edu.gesedu.seguridad.aplicacion.SesionIniciada;
import pe.edu.gesedu.seguridad.aplicacion.TokenRefresco;
import pe.edu.gesedu.seguridad.aplicacion.UsuarioAutenticado;

/**
 * Inicio, renovación y cierre de sesión (RF-52). Los endpoints públicos los protegen el bloqueo por intentos
 * fallidos (RNF-06) y el límite de peticiones por IP. El refresh token viaja solo en una cookie HttpOnly,
 * Secure y SameSite=Strict, limitada a esta ruta (DA-04).
 */
@Tag(name = "Seguridad")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    static final String COOKIE_REFRESCO = "gesedu_refresh";
    private static final String RUTA_COOKIE = "/api/v1/auth";

    private final AutenticacionService autenticacion;

    AuthController(AutenticacionService autenticacion) {
        this.autenticacion = autenticacion;
    }

    @Operation(summary = "Inicia sesión y devuelve el token de acceso (JWT de 15 min); el refresh token va en una cookie. "
            + "Bloquea la cuenta tras 5 fallos")
    @SecurityRequirements
    @PostMapping("/inicio-sesion")
    SesionIniciada iniciarSesion(@Valid @RequestBody InicioSesionRequest solicitud, HttpServletResponse respuesta) {
        SesionConRefresco sesion = autenticacion.iniciarSesion(solicitud.correo(), solicitud.contrasena());
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie(sesion.refresco()).toString());
        return sesion.sesion();
    }

    @Operation(summary = "Renueva el token de acceso con el refresh token de la cookie y rota el refresh token")
    @SecurityRequirements
    @PostMapping("/refresco")
    SesionIniciada refrescar(@CookieValue(name = COOKIE_REFRESCO, required = false) String refresco,
                             HttpServletResponse respuesta) {
        try {
            SesionConRefresco sesion = autenticacion.refrescar(refresco);
            respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie(sesion.refresco()).toString());
            return sesion.sesion();
        } catch (ExcepcionNegocio e) {
            respuesta.addHeader(HttpHeaders.SET_COOKIE, cookieBorrada().toString());
            throw e;
        }
    }

    @Operation(summary = "Cierra la sesión: revoca el refresh token y borra la cookie")
    @PostMapping("/cierre-sesion")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cerrarSesion(@CookieValue(name = COOKIE_REFRESCO, required = false) String refresco,
                      HttpServletResponse respuesta) {
        autenticacion.cerrarSesion(refresco);
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookieBorrada().toString());
    }

    @Operation(summary = "Datos y permisos del usuario autenticado")
    @GetMapping("/yo")
    @PreAuthorize("isAuthenticated()")
    SesionIniciada.DatosUsuario yo(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return autenticacion.datosUsuarioActual(usuario.id());
    }

    private ResponseCookie cookie(TokenRefresco refresco) {
        return base(refresco.valor()).maxAge(refresco.vigencia()).build();
    }

    private ResponseCookie cookieBorrada() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(COOKIE_REFRESCO, valor)
                .httpOnly(true)
                .secure(autenticacion.cookieRefrescoSegura())
                .sameSite("Strict")
                .path(RUTA_COOKIE);
    }
}
