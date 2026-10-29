package pe.edu.gesedu.seguridad.aplicacion;

/** Resultado de iniciar o renovar sesión: el cuerpo JSON y el refresh token que el controlador pone en la cookie. */
public record SesionConRefresco(SesionIniciada sesion, TokenRefresco refresco) {
}
