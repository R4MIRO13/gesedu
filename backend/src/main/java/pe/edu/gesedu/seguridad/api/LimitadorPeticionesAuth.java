package pe.edu.gesedu.seguridad.api;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ProblemaHttp;

/**
 * Límite de peticiones por IP a los POST de {@code /api/v1/auth/*} (inicio de sesión, refresco y recuperación):
 * frena la fuerza bruta y el abuso del correo de recuperación (RNF-06). Ventana fija de un minuto, en memoria
 * (Caffeine); con varias instancias cada una cuenta por separado. El cierre de sesión no se limita.
 * Es un filtro de la cadena de seguridad, no un bean, para que el contenedor no lo registre dos veces.
 */
class LimitadorPeticionesAuth extends OncePerRequestFilter {

    private static final String PREFIJO = "/api/v1/auth/";
    private static final String CIERRE_SESION = "/api/v1/auth/cierre-sesion";

    private final Cache<String, AtomicInteger> contadores = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(50_000)
            .build();
    private final int limite;
    private final JsonMapper json;

    LimitadorPeticionesAuth(int limitePorMinuto, JsonMapper json) {
        this.limite = limitePorMinuto;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI();
        return !"POST".equals(peticion.getMethod()) || !ruta.startsWith(PREFIJO) || ruta.equals(CIERRE_SESION);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        // getRemoteAddr ya refleja X-Forwarded-For cuando se usa forward-headers-strategy=framework
        int cuenta = contadores.get(peticion.getRemoteAddr(), ip -> new AtomicInteger()).incrementAndGet();
        if (cuenta > limite) {
            respuesta.setHeader(HttpHeaders.RETRY_AFTER, "60");
            ProblemaHttp.escribir(peticion, respuesta, json, ProblemaHttp.crear(CodigoError.LIMITE_PETICIONES_EXCEDIDO,
                    CodigoError.LIMITE_PETICIONES_EXCEDIDO.titulo()));
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }
}
