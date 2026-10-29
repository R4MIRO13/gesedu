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

    /** Un apoderado con un hijo matriculado y sus 11 obligaciones (matrícula y pensiones de marzo a diciembre). */
    public record Familia(ApoderadoConCuenta apoderado, String alumnoId, String matriculaId,
                          pe.edu.gesedu.apoyo.DatosMatricula.Escenario escenario) {

        /** Id de la obligación de la pensión del mes ({@code 3} = marzo). */
        public String pension(pe.edu.gesedu.apoyo.DatosMatricula datos, int mes) {
            return datos.obligacion(matriculaId, "%d-%02d".formatted(escenario.anio(), mes));
        }
    }

    protected Familia crearFamilia() throws Exception {
        var e = datos.crearEscenario(10);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String alumno = datos.alumnoDe(ap.apoderadoId(), "Lucía");
        return new Familia(ap, alumno, datos.matriculaConObligaciones(e, ap.apoderadoId(), alumno, e.seccion3A()), e);
    }

    /** Sube el voucher de una obligación como lo hace el navegador y devuelve el cuerpo del comprobante a presentar. */
    protected java.util.Map<String, Object> comprobante(String token, String obligacionId, String operacion,
                                                        String monto) throws Exception {
        byte[] contenido = ("%PDF-1.4 voucher " + operacion).getBytes();
        Respuesta url = post("/api/v1/obligaciones/" + obligacionId + "/comprobantes/archivo", json.writeValueAsString(
                java.util.Map.of("nombreArchivo", "voucher.pdf", "contentType", "application/pdf",
                        "tamanoBytes", contenido.length)), token);
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
            throw new IllegalStateException("No se pudo subir el voucher: " + estado);
        }
        java.util.Map<String, Object> cuerpo = new java.util.HashMap<>();
        cuerpo.put("numeroOperacion", operacion);
        cuerpo.put("fechaPago", java.time.LocalDate.now().toString());
        cuerpo.put("montoPagado", monto);
        cuerpo.put("medioPago", "Transferencia bancaria - BCP");
        cuerpo.put("claveObjeto", url.cuerpo().get("claveObjeto").asString());
        cuerpo.put("contentType", "application/pdf");
        cuerpo.put("tamanoBytes", contenido.length);
        return cuerpo;
    }

    protected Respuesta presentarComprobante(String token, String obligacionId, java.util.Map<String, Object> cuerpo)
            throws Exception {
        return post("/api/v1/obligaciones/" + obligacionId + "/comprobantes", json.writeValueAsString(cuerpo), token);
    }
}
