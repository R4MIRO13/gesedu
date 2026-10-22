package pe.edu.gesedu.seguridad.api;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ProblemaHttp;

/**
 * 401 y 403 de Spring Security con el mismo formato RFC 9457 que el resto de la API (docs/PLAN.md § 5.1):
 * actúan en el filtro, antes de llegar a un controlador.
 */
@Component
class ManejadoresSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper json;

    ManejadoresSeguridad(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException ex)
            throws IOException {
        CodigoError codigo = codigoDe(ex);
        respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        // El detalle no copia el mensaje de la excepción: no se filtran datos internos
        ProblemaHttp.escribir(peticion, respuesta, json, ProblemaHttp.crear(codigo, codigo.titulo()));
    }

    @Override
    public void handle(HttpServletRequest peticion, HttpServletResponse respuesta, AccessDeniedException ex)
            throws IOException {
        CodigoError codigo = CodigoError.ACCESO_DENEGADO;
        ProblemaHttp.escribir(peticion, respuesta, json, ProblemaHttp.crear(codigo, codigo.titulo()));
    }

    private static CodigoError codigoDe(AuthenticationException ex) {
        if (ex instanceof DisabledException) {
            return CodigoError.CUENTA_INACTIVA;
        }
        if (ex instanceof LockedException) {
            return CodigoError.CUENTA_BLOQUEADA;
        }
        if (ex instanceof OAuth2AuthenticationException oauth && oauth.getError().getDescription() != null
                && oauth.getError().getDescription().contains("expired")) {
            return CodigoError.SESION_EXPIRADA;
        }
        return CodigoError.NO_AUTENTICADO;
    }
}
