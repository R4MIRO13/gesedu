package pe.edu.gesedu.matricula;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosMatricula;
import pe.edu.gesedu.pagos.aplicacion.PlanDeCobros;
import pe.edu.gesedu.transversal.notificaciones.Notificador;

/**
 * Base de las pruebas de UC-02: el correo sale por un {@code Notificador} falso y {@code PlanDeCobros} es un espía
 * (para forzar un fallo en la generación de obligaciones). Las subclases comparten el contexto de Spring.
 */
public abstract class BaseMatriculaIT extends BaseIT {

    @MockitoBean
    protected Notificador notificador;

    @MockitoSpyBean
    protected PlanDeCobros planDeCobros;

    protected DatosMatricula datos;

    @Autowired
    void inicializarDatos(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        datos = new DatosMatricula(jdbcTemplate);
    }

    /** Token de un administrador con los permisos del rol (evaluar y registrar matrículas). */
    protected String tokenAdministrador() throws Exception {
        return tokenDe(crearUsuario("ADMINISTRADOR", true));
    }

    protected String ruta(String codigo) {
        return "/api/v1/solicitudes-matricula/" + codigo;
    }

    protected String cuerpoConfirmacion(String seccionId) throws Exception {
        return json.writeValueAsString(java.util.Map.of("seccionId", seccionId, "costosAceptados", true));
    }

    protected String cuerpoRechazo(String motivo, String observacion) throws Exception {
        return json.writeValueAsString(java.util.Map.of("motivo", motivo, "observacion", observacion));
    }

    protected long contar(String sql, Object... parametros) {
        return jdbc.queryForObject(sql, Long.class, parametros);
    }

    /** Un apoderado con cuenta activa (contraseña {@link #CONTRASENA}) y su perfil de apoderado. */
    public record ApoderadoConCuenta(String correo, String usuarioId, String apoderadoId, String token) {
    }

    protected ApoderadoConCuenta crearApoderadoConCuenta() throws Exception {
        String correo = crearUsuario("APODERADO", true);
        String usuarioId = idDe(correo);
        String apoderadoId = java.util.UUID.randomUUID().toString();
        jdbc.update("INSERT INTO seguridad.apoderado (id, usuario_id) VALUES (?, ?)", apoderadoId, usuarioId);
        return new ApoderadoConCuenta(correo, usuarioId, apoderadoId, tokenDe(correo));
    }

    /**
     * Sube un documento como lo hace el navegador: pide la URL prefirmada a la API y hace el PUT directo. Devuelve el
     * documento ya listo para declararse en la solicitud.
     */
    protected java.util.Map<String, Object> subirDocumento(String token, String tipo, String nombre) throws Exception {
        byte[] contenido = ("%PDF-1.4 " + nombre).getBytes();
        Respuesta url = post("/api/v1/solicitudes-matricula/archivos", json.writeValueAsString(java.util.Map.of(
                "nombreArchivo", nombre, "contentType", "application/pdf", "tamanoBytes", contenido.length)), token);
        if (url.estado() != 200) {
            throw new IllegalStateException("No se pudo pedir la URL de subida: " + url.estado() + " " + url.cuerpo());
        }
        var peticion = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + puerto
                        + url.cuerpo().get("subida").get("url").asString()))
                .header("Content-Type", "application/pdf")
                .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(contenido)).build();
        int estado = java.net.http.HttpClient.newHttpClient()
                .send(peticion, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
        if (estado != 200) {
            throw new IllegalStateException("No se pudo subir el archivo: " + estado);
        }
        return java.util.Map.of("tipo", tipo, "claveObjeto", url.cuerpo().get("claveObjeto").asString(),
                "nombreArchivo", nombre, "contentType", "application/pdf", "tamanoBytes", contenido.length);
    }
}
