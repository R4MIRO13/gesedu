package pe.edu.gesedu.compartido.error;

import org.springframework.http.HttpStatus;

/**
 * Códigos de error de negocio y su estado HTTP (docs/PLAN.md § 5.2, docs/api/errores.md).
 * Viajan en la propiedad {@code codigo} de la respuesta {@code application/problem+json}.
 */
public enum CodigoError {

    VALIDACION_INVALIDA(HttpStatus.BAD_REQUEST, "La solicitud no es válida"),
    SOLICITUD_INVALIDA(HttpStatus.BAD_REQUEST, "La solicitud no puede procesarse"),

    NO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión"),
    CREDENCIALES_INVALIDAS(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos"),
    CUENTA_BLOQUEADA(HttpStatus.UNAUTHORIZED, "La cuenta está bloqueada temporalmente"),
    CUENTA_INACTIVA(HttpStatus.UNAUTHORIZED, "La cuenta está desactivada"),
    SESION_EXPIRADA(HttpStatus.UNAUTHORIZED, "La sesión expiró; inicia sesión de nuevo"),

    ACCESO_DENEGADO(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción"),

    RECURSO_NO_ENCONTRADO(HttpStatus.NOT_FOUND, "El recurso solicitado no existe"),

    CONFLICTO_HORARIO(HttpStatus.CONFLICT, "El horario entra en conflicto con otro existente"),

    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado");

    private final HttpStatus estado;
    private final String titulo;

    CodigoError(HttpStatus estado, String titulo) {
        this.estado = estado;
        this.titulo = titulo;
    }

    public HttpStatus estado() {
        return estado;
    }

    public String titulo() {
        return titulo;
    }
}
