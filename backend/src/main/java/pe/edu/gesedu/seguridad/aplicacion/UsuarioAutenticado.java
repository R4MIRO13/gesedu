package pe.edu.gesedu.seguridad.aplicacion;

/** Principal de una petición autenticada con un token de acceso válido. */
public record UsuarioAutenticado(String id, String nombres, String rol) {
}
