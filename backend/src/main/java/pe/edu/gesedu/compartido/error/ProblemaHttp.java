package pe.edu.gesedu.compartido.error;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/**
 * Construye las respuestas RFC 9457 ({@code application/problem+json}) con la propiedad {@code codigo}.
 * Las usan el manejador global y los manejadores de Spring Security (401 y 403), que actúan antes de
 * que la petición llegue a un controlador.
 */
public final class ProblemaHttp {

    private static final String BASE_TIPO = "https://gesedu.pe/problemas/";

    private ProblemaHttp() {
    }

    public static ProblemDetail crear(CodigoError codigo, String detalle, Map<String, Object> propiedades) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(codigo.estado(), detalle);
        problema.setType(URI.create(BASE_TIPO + codigo.name()));
        problema.setTitle(codigo.titulo());
        problema.setProperty("codigo", codigo.name());
        propiedades.forEach(problema::setProperty);
        return problema;
    }

    public static ProblemDetail crear(CodigoError codigo, String detalle) {
        return crear(codigo, detalle, Map.of());
    }

    /** Escribe el problema en la respuesta; se usa donde no hay un controlador que devuelva el ProblemDetail. */
    public static void escribir(HttpServletRequest peticion, HttpServletResponse respuesta, JsonMapper json,
                                ProblemDetail problema) throws IOException {
        problema.setInstance(URI.create(peticion.getRequestURI()));
        respuesta.setStatus(problema.getStatus());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // Se aplana a un Map: no depende de cómo estén registrados los mixins de ProblemDetail en el JsonMapper
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("type", problema.getType());
        cuerpo.put("title", problema.getTitle());
        cuerpo.put("status", problema.getStatus());
        cuerpo.put("detail", problema.getDetail());
        cuerpo.put("instance", problema.getInstance());
        if (problema.getProperties() != null) {
            cuerpo.putAll(problema.getProperties());
        }
        json.writeValue(respuesta.getOutputStream(), cuerpo);
    }
}
