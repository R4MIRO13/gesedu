package pe.edu.gesedu.compartido.error;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manejador global de errores: todas las respuestas de error salen como RFC 9457
 * ({@code application/problem+json}) con la propiedad {@code codigo} (docs/PLAN.md § 5.1).
 */
@RestControllerAdvice
public class ManejadorProblemas extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorProblemas.class);

    @ExceptionHandler(ExcepcionNegocio.class)
    ResponseEntity<Object> alManejarNegocio(ExcepcionNegocio ex, WebRequest peticion) {
        ProblemDetail problema = ProblemaHttp.crear(ex.codigo(), ex.getMessage(), ex.propiedades());
        return handleExceptionInternal(ex, problema, new HttpHeaders(), ex.codigo().estado(), peticion);
    }

    /** 401 y 403 los resuelve Spring Security con sus propios manejadores (mismo formato); aquí solo se relanzan. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void alManejarSeguridad(Exception ex) throws Exception {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> alManejarInesperado(Exception ex, WebRequest peticion) {
        log.error("Error no controlado", ex);
        ProblemDetail problema = ProblemaHttp.crear(CodigoError.ERROR_INTERNO, CodigoError.ERROR_INTERNO.titulo());
        return handleExceptionInternal(ex, problema, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, peticion);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest peticion) {
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("campo", e.getField(), "mensaje", String.valueOf(e.getDefaultMessage())))
                .toList();
        ProblemDetail problema = ProblemaHttp.crear(CodigoError.VALIDACION_INVALIDA,
                CodigoError.VALIDACION_INVALIDA.titulo(), Map.of("errores", errores));
        return handleExceptionInternal(ex, problema, headers, status, peticion);
    }

    /** Los errores del propio framework (400, 404, 405, 415…) también llevan {@code codigo}. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object cuerpo, HttpHeaders headers,
            HttpStatusCode status, WebRequest peticion) {
        // Las excepciones del framework (ErrorResponse) llegan sin cuerpo: su ProblemDetail se crea aquí dentro
        ProblemDetail problema = cuerpo instanceof ProblemDetail pd ? pd
                : ex instanceof ErrorResponse respuesta ? respuesta.getBody() : null;
        if (problema != null && (problema.getProperties() == null || !problema.getProperties().containsKey("codigo"))) {
            CodigoError codigo = switch (status.value()) {
                case 400 -> CodigoError.VALIDACION_INVALIDA;
                case 404 -> CodigoError.RECURSO_NO_ENCONTRADO;
                default -> status.is5xxServerError() ? CodigoError.ERROR_INTERNO : CodigoError.SOLICITUD_INVALIDA;
            };
            problema.setType(java.net.URI.create("https://gesedu.pe/problemas/" + codigo.name()));
            problema.setProperty("codigo", codigo.name());
        }
        return super.handleExceptionInternal(ex, cuerpo, headers, status, peticion);
    }
}
