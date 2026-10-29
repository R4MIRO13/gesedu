package pe.edu.gesedu.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.matricula.BaseMatriculaIT;

/** RF-02 (C-43): el administrador edita los datos del estudiante y del apoderado con If-Match y auditoría. */
class EdicionDatosPersonalesIT extends BaseMatriculaIT {

    private static final AtomicInteger DOCUMENTOS = new AtomicInteger(50_000_000);

    private String documentoNuevo() {
        return String.valueOf(DOCUMENTOS.getAndIncrement());
    }

    private Map<String, Object> alumno(String documento) {
        Map<String, Object> a = new HashMap<>();
        a.put("nombres", "Lucía Beatriz");
        a.put("apellidos", "García Rojas");
        a.put("documento", documento);
        a.put("correo", null);
        a.put("fechaNacimiento", "2012-05-14");
        a.put("parentesco", "MADRE");
        return a;
    }

    private Map<String, Object> apoderado(String documento) {
        Map<String, Object> a = new HashMap<>();
        a.put("nombres", "María");
        a.put("apellidos", "Rojas Salazar");
        a.put("documento", documento);
        a.put("correo", "maria." + documento + "@test.gesedu.pe");
        a.put("telefono", "987654321");
        return a;
    }

    private Respuesta put(String ruta, Map<String, Object> cuerpo, String token, String ifMatch) throws Exception {
        return enviar("PUT", ruta, json.writeValueAsString(cuerpo), token, ifMatch);
    }

    private String documentoDe(String alumnoId) {
        return jdbc.queryForObject("SELECT u.documento_identidad FROM seguridad.alumno a JOIN seguridad.usuario u ON u.id = a.usuario_id "
                + "WHERE a.id = ?", String.class, alumnoId);
    }

    @Test
    void editarElEstudianteActualizaSusDatosSubeLaVersionYQuedaAuditado() throws Exception {
        Familia f = crearFamilia();
        String admin = tokenAdministrador();
        String ruta = "/api/v1/alumnos/" + f.alumnoId();
        Respuesta ficha = get(ruta, admin);
        String version = etag(ficha);
        String documento = documentoNuevo();

        Respuesta r = put(ruta, alumno(documento), admin, version);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("nombres").asString()).isEqualTo("Lucía Beatriz");
        assertThat(r.cuerpo().get("documento").asString()).isEqualTo(documento);
        assertThat(r.cuerpo().get("fechaNacimiento").asString()).isEqualTo("2012-05-14");
        assertThat(r.cuerpo().get("parentesco").asString()).isEqualTo("MADRE");
        assertThat(etag(r)).isNotEqualTo(version);
        assertThat(r.cuerpo().get("version").asLong()).isEqualTo(ficha.cuerpo().get("version").asLong() + 1);
        assertThat(get(ruta, admin).cuerpo().get("documento").asString()).isEqualTo(documento);
        assertThat(jdbc.queryForObject("SELECT fecha_nacimiento::text FROM seguridad.alumno WHERE id = ?", String.class, f.alumnoId()))
                .isEqualTo("2012-05-14");
        // Auditoría con el valor anterior y el nuevo de lo que cambió
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'EDITAR_DATOS_ESTUDIANTE' "
                + "AND id_entidad_afectada = ?", f.alumnoId())).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM auditoria.cambio_auditoria c JOIN auditoria.registro_auditoria r "
                + "ON r.id = c.registro_auditoria_id WHERE r.id_entidad_afectada = ? AND c.campo = 'nombres' "
                + "AND c.valor_anterior = 'Lucía' AND c.valor_nuevo = 'Lucía Beatriz'", f.alumnoId())).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM auditoria.cambio_auditoria c JOIN auditoria.registro_auditoria r "
                + "ON r.id = c.registro_auditoria_id WHERE r.id_entidad_afectada = ? AND c.campo = 'apellidos'",
                f.alumnoId())).as("lo que no cambió no se registra").isEqualTo(1);
    }

    @Test
    void conservarElPropioDocumentoSinCambioEstaPermitido() throws Exception {
        Familia f = crearFamilia();
        String admin = tokenAdministrador();
        String ruta = "/api/v1/alumnos/" + f.alumnoId();
        String propio = documentoDe(f.alumnoId());

        Respuesta r = put(ruta, alumno(propio), admin, etag(get(ruta, admin)));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(documentoDe(f.alumnoId())).isEqualTo(propio);
    }

    @Test
    void unDocumentoOCorreoDeOtraCuentaSeRechazaSinCambiarNada() throws Exception {
        Familia f = crearFamilia();
        Familia otra = crearFamilia();
        String admin = tokenAdministrador();
        String ruta = "/api/v1/alumnos/" + f.alumnoId();
        String version = etag(get(ruta, admin));
        String antes = documentoDe(f.alumnoId());

        Respuesta documento = put(ruta, alumno(documentoDe(otra.alumnoId())), admin, version);
        Map<String, Object> conCorreoAjeno = alumno(documentoNuevo());
        conCorreoAjeno.put("correo", otra.apoderado().correo().toUpperCase());
        Respuesta correo = put(ruta, conCorreoAjeno, admin, version);

        assertThat(documento.estado()).isEqualTo(409);
        assertThat(documento.cuerpo().get("codigo").asString()).isEqualTo("DOCUMENTO_DUPLICADO");
        assertThat(correo.estado()).isEqualTo(409);
        assertThat(correo.cuerpo().get("codigo").asString()).isEqualTo("CORREO_DUPLICADO");
        assertThat(documentoDe(f.alumnoId())).isEqualTo(antes);
        assertThat(etag(get(ruta, admin))).as("la versión no cambió").isEqualTo(version);
    }

    @Test
    void sinIfMatchOConUnaVersionVencidaLaEdicionNoSeAplica() throws Exception {
        Familia f = crearFamilia();
        String admin = tokenAdministrador();
        String ruta = "/api/v1/alumnos/" + f.alumnoId();
        String version = etag(get(ruta, admin));
        assertThat(put(ruta, alumno(documentoNuevo()), admin, version).estado()).isEqualTo(200);

        Respuesta sinCabecera = put(ruta, alumno(documentoNuevo()), admin, null);
        Respuesta vencida = put(ruta, alumno(documentoNuevo()), admin, version);

        assertThat(sinCabecera.estado()).isEqualTo(400);
        assertThat(sinCabecera.cuerpo().get("codigo").asString()).isEqualTo("VERSION_REQUERIDA");
        assertThat(vencida.estado()).isEqualTo(409);
        assertThat(vencida.cuerpo().get("codigo").asString()).isEqualTo("CONFLICTO_CONCURRENCIA");
    }

    @Test
    void dosEdicionesQuePartieronDeLaMismaVersionSoloAplicaUna() throws Exception {
        Familia f = crearFamilia();
        String admin = tokenAdministrador();
        String ruta = "/api/v1/alumnos/" + f.alumnoId();
        String version = etag(get(ruta, admin));
        CountDownLatch salida = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Map<String, Object> cuerpo = alumno(documentoNuevo());
                futuros.add(pool.submit(() -> {
                    salida.await();
                    return put(ruta, cuerpo, admin, version).estado();
                }));
            }
            salida.countDown();
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> futuro : futuros) {
                estados.add(futuro.get());
            }
            assertThat(estados).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aunSiSoloCambiaUnDatoDelPerfilLaVersionDeLaCuentaSube() throws Exception {
        Familia f = crearFamilia();
        String admin = tokenAdministrador();
        String ruta = "/api/v1/alumnos/" + f.alumnoId();
        Respuesta ficha = get(ruta, admin);
        Map<String, Object> cuerpo = alumno(ficha.cuerpo().get("documento").asString());
        cuerpo.put("nombres", ficha.cuerpo().get("nombres").asString());
        cuerpo.put("apellidos", ficha.cuerpo().get("apellidos").asString());
        cuerpo.put("fechaNacimiento", "2011-01-01");

        Respuesta r = put(ruta, cuerpo, admin, etag(ficha));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("version").asLong()).isGreaterThan(ficha.cuerpo().get("version").asLong());
        assertThat(put(ruta, cuerpo, admin, etag(ficha)).estado()).as("la versión anterior ya no sirve").isEqualTo(409);
    }

    @Test
    void editarElApoderadoActualizaNombreCorreoYTelefono() throws Exception {
        Familia f = crearFamilia();
        String admin = tokenAdministrador();
        String ruta = "/api/v1/apoderados/" + f.apoderado().apoderadoId();
        Respuesta ficha = get(ruta, admin);
        String documento = documentoNuevo();

        Respuesta r = put(ruta, apoderado(documento), admin, etag(ficha));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("nombres").asString()).isEqualTo("María");
        assertThat(r.cuerpo().get("telefono").asString()).isEqualTo("987654321");
        assertThat(r.cuerpo().get("correo").asString()).isEqualTo("maria." + documento + "@test.gesedu.pe");
        assertThat(jdbc.queryForObject("SELECT numero_telefono FROM seguridad.apoderado WHERE id = ?", String.class,
                f.apoderado().apoderadoId())).isEqualTo("987654321");
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'EDITAR_DATOS_APODERADO' "
                + "AND id_entidad_afectada = ?", f.apoderado().apoderadoId())).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM auditoria.cambio_auditoria c JOIN auditoria.registro_auditoria r "
                + "ON r.id = c.registro_auditoria_id WHERE r.id_entidad_afectada = ? AND c.campo = 'telefono' "
                + "AND c.valor_anterior IS NULL AND c.valor_nuevo = '987654321'", f.apoderado().apoderadoId())).isEqualTo(1);
        // El apoderado inicia sesión con el correo nuevo (la caché de la cuenta se descartó)
        assertThat(iniciarSesion("maria." + documento + "@test.gesedu.pe", CONTRASENA).estado()).isEqualTo(200);
        assertThat(iniciarSesion(f.apoderado().correo(), CONTRASENA).estado()).isEqualTo(401);
    }

    @Test
    void unaCuentaActivaNoPuedeQuedarseSinCorreo() throws Exception {
        Familia f = crearFamilia();
        String admin = tokenAdministrador();
        String ruta = "/api/v1/apoderados/" + f.apoderado().apoderadoId();
        Map<String, Object> cuerpo = apoderado(documentoNuevo());
        cuerpo.put("correo", "");

        Respuesta r = put(ruta, cuerpo, admin, etag(get(ruta, admin)));

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("detail").asString()).contains("necesita un correo");
    }

    @Test
    void losDatosInvalidosSeRechazanConElNombreDelCampo() throws Exception {
        Familia f = crearFamilia();
        String admin = tokenAdministrador();
        String ruta = "/api/v1/alumnos/" + f.alumnoId();
        Map<String, Object> cuerpo = alumno("AB#");
        cuerpo.put("nombres", " ");
        cuerpo.put("fechaNacimiento", "2999-01-01");
        cuerpo.put("correo", "no-es-un-correo");

        Respuesta r = put(ruta, cuerpo, admin, etag(get(ruta, admin)));

        assertThat(r.estado()).isEqualTo(400);
        List<String> campos = new ArrayList<>();
        r.cuerpo().get("errores").forEach(x -> campos.add(x.get("campo").asString()));
        assertThat(campos).contains("documento", "nombres", "fechaNacimiento", "correo");
    }

    @Test
    void unDocumentoNuevoDebeSerUnDniDeOchoDigitos() throws Exception {
        Familia f = crearFamilia();
        String admin = tokenAdministrador();
        String ruta = "/api/v1/alumnos/" + f.alumnoId();

        Respuesta r = put(ruta, alumno("ABC12345"), admin, etag(get(ruta, admin)));

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("errores").get(0).get("campo").asString()).isEqualTo("documento");
        assertThat(r.cuerpo().get("errores").get(0).get("mensaje").asString()).contains("8 dígitos");
    }

    @Test
    void soloElAdministradorEditaYUnIdInexistenteEs404() throws Exception {
        Familia f = crearFamilia();
        String ruta = "/api/v1/alumnos/" + f.alumnoId();
        String rutaApoderado = "/api/v1/apoderados/" + f.apoderado().apoderadoId();

        for (String token : List.of(f.apoderado().token(), tokenDe(crearUsuario("DOCENTE", true)), tokenDe(crearUsuario("ALUMNO", true)))) {
            assertThat(get(ruta, token).estado()).isEqualTo(403);
            assertThat(put(ruta, alumno(documentoNuevo()), token, "\"0\"").estado()).isEqualTo(403);
            assertThat(get(rutaApoderado, token).estado()).isEqualTo(403);
            assertThat(put(rutaApoderado, apoderado(documentoNuevo()), token, "\"0\"").estado()).isEqualTo(403);
        }
        assertThat(get(ruta, null).estado()).isEqualTo(401);
        String admin = tokenAdministrador();
        assertThat(get("/api/v1/alumnos/00000000-0000-0000-0000-000000000000", admin).estado()).isEqualTo(404);
        assertThat(put("/api/v1/apoderados/00000000-0000-0000-0000-000000000000", apoderado(documentoNuevo()), admin, "\"0\"").estado())
                .isEqualTo(404);
    }
}
