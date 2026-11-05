package pe.edu.gesedu;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import pe.edu.gesedu.apoyo.BaseIT;

/**
 * La semilla de la fase 3 (V1003) y el recorrido de cada caso de uso nuevo con los usuarios que deja listos: UC-13,
 * UC-14, UC-20, UC-22 y UC-23. Corre en su propia base de demo y en orden, porque publicar el horario es definitivo.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatosDemoFase3IT extends BaseIT {

    private static final String BASE_DE_DATOS = "gesedu_demo_fase3";

    @BeforeAll
    static void crearBaseDeDatosDeDemo() throws SQLException {
        try (Connection conexion = DriverManager.getConnection("jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/postgres", "postgres", "postgres");
             Statement sentencia = conexion.createStatement()) {
            sentencia.execute("DROP DATABASE IF EXISTS " + BASE_DE_DATOS);
            sentencia.execute("CREATE DATABASE " + BASE_DE_DATOS + " OWNER gesedu_migrador ENCODING 'UTF8'");
            sentencia.execute("GRANT CONNECT ON DATABASE " + BASE_DE_DATOS + " TO gesedu_app");
        }
        baseDeDatos = BASE_DE_DATOS;
    }

    @AfterAll
    static void volverALaBaseCompartida() {
        baseDeDatos = "gesedu";
    }

    @DynamicPropertySource
    static void perfilDemo(DynamicPropertyRegistry registro) {
        registro.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/demo");
    }

    private static final String H = "/api/v1/horarios/2027";

    private String token(String correo) throws Exception {
        return tokenDe(correo + "@demo.gesedu.pe");
    }

    private String id(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private JsonNode completitud(String token) throws Exception {
        return get(H + "/borrador/completitud", token).cuerpo();
    }

    @Test
    @Order(1)
    void lasCuentasNuevasYLasDeSiempreEntranConLaContrasenaComun() throws Exception {
        for (String cuenta : List.of("admin", "directivo", "docente", "docente2", "docente.nuevo", "alumno", "apoderado", "familia.torres")) {
            Respuesta r = iniciarSesion(cuenta + "@demo.gesedu.pe", CONTRASENA);
            assertThat(r.estado()).as(cuenta).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seguridad.docente", Long.class)).isEqualTo(10);
    }

    @Test
    @Order(2)
    void uc22LaDemoArrancaConDosSeccionesConObservacionesComoElWireframe() throws Exception {
        JsonNode c = completitud(token("admin"));

        assertThat(c.get("completas").asInt()).isEqualTo(8);
        assertThat(c.get("conObservaciones").asInt()).isEqualTo(2);
        assertThat(c.get("publicable").asBoolean()).isFalse();
        JsonNode tercero = null;
        JsonNode cuarto = null;
        for (JsonNode s : c.get("secciones")) {
            if (s.get("seccion").asString().equals("3° A")) {
                tercero = s;
            }
            if (s.get("seccion").asString().equals("4° B")) {
                cuarto = s;
            }
        }
        assertThat(tercero.get("estado").asString()).isEqualTo("FALTAN");
        assertThat(tercero.get("faltan").asInt()).isEqualTo(3);
        assertThat(tercero.get("horasAsignadas").asInt()).isEqualTo(19);
        assertThat(cuarto.get("estado").asString()).isEqualTo("EXCEDE");
        assertThat(cuarto.get("sobran").asInt()).isEqualTo(1);
        assertThat(c.get("advertencias").get(0).get("mensaje").asString())
                .isEqualTo("1 docente habilitado no confirmó su disponibilidad: Marta Ibáñez Ruiz.");
        Respuesta r = enviar("POST", H + "/publicaciones", null, token("admin"), etag(get(H + "/borrador/completitud", token("admin"))));
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("HORARIO_INCOMPLETO");
    }

    @Test
    @Order(3)
    void uc23FaltaPublicarAunNadaLoVe() throws Exception {
        for (String cuenta : List.of("alumno", "apoderado", "docente", "admin")) {
            Respuesta r = get(H + "/vigente", token(cuenta));
            assertThat(r.estado()).as(cuenta).isEqualTo(404);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("HORARIO_NO_PUBLICADO");
        }
    }

    @Test
    @Order(4)
    void uc20LaDocenteNuevaRegistraDesdeCeroConLaAdvertenciaYLaConfirma() throws Exception {
        String token = token("docente.nuevo");
        String ruta = "/api/v1/anios-escolares/2027/disponibilidad/mia";

        JsonNode antes = get(ruta, token).cuerpo();
        assertThat(antes.get("vinculado").asBoolean()).isTrue();
        assertThat(antes.get("periodo").get("abierto").asBoolean()).isTrue();
        assertThat(antes.get("bloqueIds")).isEmpty();
        assertThat(antes.get("cargaSemanalReferencia").asInt()).isEqualTo(10);
        List<String> bloques = antes.get("bloques").valueStream().map(b -> b.get("id").asString()).toList();
        assertThat(bloques).hasSize(45);

        JsonNode poca = post(ruta + "/validacion", json.writeValueAsString(Map.of("bloqueIds", bloques.subList(0, 6))), token).cuerpo();
        assertThat(poca.get("suficiente").asBoolean()).isFalse();
        assertThat(poca.get("faltan").asInt()).isEqualTo(4);

        Respuesta r = enviar("PUT", ruta, json.writeValueAsString(Map.of("bloqueIds", bloques.subList(0, 12),
                "observaciones", "Solo mañanas")), token, null);
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("bloqueIds")).hasSize(12);
        assertThat(completitud(token("admin")).get("advertencias")).isEmpty();
    }

    @Test
    @Order(5)
    void uc20RosaYaTieneTodosLosBloquesMarcadosYPuedeActualizar() throws Exception {
        String token = token("docente");
        String ruta = "/api/v1/anios-escolares/2027/disponibilidad/mia";

        JsonNode antes = get(ruta, token).cuerpo();
        assertThat(antes.get("bloqueIds")).hasSize(45);
        assertThat(antes.get("cargaSemanalReferencia").asInt()).isEqualTo(20);
        List<String> quince = antes.get("bloqueIds").valueStream().map(JsonNode::asString).limit(15).toList();
        Respuesta r = enviar("PUT", ruta, json.writeValueAsString(Map.of("bloqueIds", quince)), token, null);
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("validacion").get("faltan").asInt()).isEqualTo(5);
        // La demo se deja como estaba para quien la use después
        List<String> todos = antes.get("bloqueIds").valueStream().map(JsonNode::asString).toList();
        assertThat(enviar("PUT", ruta, json.writeValueAsString(Map.of("bloqueIds", todos)), token, null).estado()).isEqualTo(200);
    }

    @Test
    @Order(6)
    void uc13Uc14ElDirectivoCreaElAnio2028YCopiaLosCursosDe2027() throws Exception {
        String directivo = token("directivo");
        String bimestres = "[{\"numero\":1,\"fechaInicio\":\"2028-02-14\",\"fechaFin\":\"2028-04-28\"},"
                + "{\"numero\":2,\"fechaInicio\":\"2028-05-08\",\"fechaFin\":\"2028-07-14\"},"
                + "{\"numero\":3,\"fechaInicio\":\"2028-07-31\",\"fechaFin\":\"2028-10-06\"},"
                + "{\"numero\":4,\"fechaInicio\":\"2028-10-16\",\"fechaFin\":\"2028-12-19\"}]";
        Respuesta anio = post("/api/v1/anios-escolares", "{\"fechaInicio\":\"2028-02-14\",\"fechaFin\":\"2028-12-19\",\"bimestres\":" + bimestres + "}", directivo);
        assertThat(anio.estado()).isEqualTo(201);
        assertThat(anio.cuerpo().get("estado").asString()).isEqualTo("PLANIFICADO");

        JsonNode vista = get("/api/v1/anios-escolares/2028/cursos/copia-anterior", directivo).cuerpo();
        assertThat(vista.get("anioOrigen").asInt()).isEqualTo(2027);
        assertThat(vista.get("cursos")).hasSize(6);
        Respuesta copia = post("/api/v1/anios-escolares/2028/cursos/copia-anterior", "{}", directivo);
        assertThat(copia.estado()).isEqualTo(200);
        assertThat(copia.cuerpo().get("copiados")).hasSize(6);
        assertThat(get("/api/v1/anios-escolares/2028/cursos", directivo).cuerpo().get("totalHoras").asInt()).isEqualTo(22);
    }

    @Test
    @Order(7)
    void uc22ElAdministradorCorrigeLasDosSeccionesYPublicaLaVersion1() throws Exception {
        String admin = token("admin");
        String base = H + "/borrador";
        String seccion3A = id("SELECT s.id FROM academico.seccion s JOIN academico.grado g ON g.id = s.grado_id "
                + "WHERE g.numero_nivel = 3 AND s.letra = 'A' AND g.anio_escolar_id = (SELECT id FROM academico.anio_escolar WHERE anio = 2027)");
        String seccion4B = id("SELECT s.id FROM academico.seccion s JOIN academico.grado g ON g.id = s.grado_id "
                + "WHERE g.numero_nivel = 4 AND s.letra = 'B' AND g.anio_escolar_id = (SELECT id FROM academico.anio_escolar WHERE anio = 2027)");
        String ingles = id("SELECT id FROM academico.curso WHERE slug = 'ingles' AND anio_escolar_id = (SELECT id FROM academico.anio_escolar WHERE anio = 2027)");
        String ef = id("SELECT id FROM academico.curso WHERE slug = 'educacion-fisica' AND anio_escolar_id = (SELECT id FROM academico.anio_escolar WHERE anio = 2027)");
        String carmen = id("SELECT id FROM seguridad.docente WHERE codigo_docente = 'DOC-007'");
        String oscar = id("SELECT id FROM seguridad.docente WHERE codigo_docente = 'DOC-008'");

        // 4° B: se quita una clase de Educación Física (la que sobra)
        JsonNode cuarto = get(base + "?seccionId=" + seccion4B, admin).cuerpo();
        String sobrante = cuarto.get("asignaciones").valueStream().filter(a -> a.get("curso").asString().equals("Educación Física"))
                .findFirst().orElseThrow().get("id").asString();
        assertThat(enviar("DELETE", base + "/asignaciones/" + sobrante, null, admin, etag(get(base + "?seccionId=" + seccion4B, admin)))
                .estado()).isEqualTo(204);

        // 3° A: 1 h de Inglés (Carmen) y 2 h de Educación Física (Óscar) en bloques donde la sección y el docente estén libres
        agregar(admin, seccion3A, ingles, carmen);
        agregar(admin, seccion3A, ef, oscar);
        agregar(admin, seccion3A, ef, oscar);

        JsonNode revisada = completitud(admin);
        assertThat(revisada.get("conObservaciones").asInt()).isZero();
        assertThat(revisada.get("publicable").asBoolean()).isTrue();
        Respuesta r = enviar("POST", H + "/publicaciones", null, admin, etag(get(H + "/borrador/completitud", admin)));
        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("numeroVersion").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get("clases").asInt()).isEqualTo(220);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluacion.asignacion_docente a JOIN academico.seccion s ON s.id = a.seccion_id "
                + "JOIN academico.anio_escolar ae ON ae.id = s.anio_escolar_id WHERE ae.anio = 2027", Long.class)).isEqualTo(60);
    }

    /** Agrega una clase en el primer bloque donde la sección y el docente tienen libre el horario y él está disponible. */
    private void agregar(String admin, String seccionId, String cursoId, String docenteId) throws Exception {
        String bloque = id("SELECT b.id FROM horarios.bloque_horario b JOIN horarios.bloque_disponible bd ON bd.bloque_id = b.id "
                + "JOIN horarios.disponibilidad_docente dd ON dd.id = bd.disponibilidad_id JOIN horarios.vinculacion_docente v ON v.id = dd.vinculacion_id "
                + "WHERE v.docente_id = ? AND NOT EXISTS (SELECT 1 FROM horarios.asignacion_horario a WHERE a.bloque_id = b.id "
                + "AND (a.docente_id = ? OR a.seccion_id = ?)) ORDER BY b.dia_semana DESC, b.hora_inicio LIMIT 1",
                docenteId, docenteId, seccionId);
        String base = H + "/borrador";
        Respuesta r = enviar("POST", base + "/asignaciones", json.writeValueAsString(Map.of("bloqueId", bloque, "seccionId", seccionId,
                "cursoId", cursoId, "docenteId", docenteId)), token("admin"), etag(get(base + "?seccionId=" + seccionId, token("admin"))));
        assertThat(r.estado()).as(r.cuerpo() == null ? "" : r.cuerpo().toString()).isEqualTo(201);
    }

    @Test
    @Order(8)
    void uc23CadaUsuarioVeSoloLoSuyoDelHorarioPublicado() throws Exception {
        JsonNode lucia = get(H + "/vigente", token("alumno")).cuerpo();
        assertThat(lucia.get("secciones")).hasSize(1);
        assertThat(lucia.get("secciones").get(0).get("nombre").asString()).isEqualTo("3° A");
        assertThat(lucia.get("asignaciones")).hasSize(22);
        assertThat(lucia.get("numeroVersion").asInt()).isEqualTo(1);

        JsonNode maria = get(H + "/vigente", token("apoderado")).cuerpo();
        assertThat(maria.get("asignaciones")).hasSize(22);

        JsonNode torres = get(H + "/vigente", token("familia.torres")).cuerpo();
        assertThat(torres.get("secciones").get(0).get("nombre").asString()).isEqualTo("1° A");
        assertThat(torres.get("asignaciones")).hasSize(22);

        JsonNode rosa = get(H + "/vigente", token("docente")).cuerpo();
        assertThat(rosa.get("perspectiva").asString()).isEqualTo("DOCENTE");
        assertThat(rosa.get("asignaciones")).hasSize(20);
        assertThat(rosa.get("asignaciones").valueStream().map(a -> a.get("docente").asString()).distinct().toList())
                .containsExactly("Rosa Quispe Mendoza");

        // Fernando no ve las clases de Rosa ni secciones donde no dicta
        String docenteRosa = id("SELECT id FROM seguridad.docente WHERE codigo_docente = 'DOC-001'");
        assertThat(get(H + "/vigente?perspectiva=DOCENTE&docenteId=" + docenteRosa, token("docente2")).estado()).isEqualTo(403);
        assertThat(get(H + "/vigente", token("admin")).cuerpo().get("secciones")).hasSize(10);
    }
}
