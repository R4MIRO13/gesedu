package pe.edu.gesedu;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.RelojPrueba;
import pe.edu.gesedu.apoyo.RelojPruebaConfig;

/**
 * La semilla de la fase 5 (V1005) y el recorrido de cada caso de uso de evaluación con los usuarios que deja listos: UC-15
 * (Fernando completa Matemática de 3° A), UC-18 (el administrador consolida el bimestre I), UC-19 (Lucía, su apoderada y la
 * familia Torres con la libreta FINAL de 2026), UC-16 y UC-17. Rosa, tutora de 3° A, no dicta esa Matemática: la dicta Fernando
 * Medina (docente2@). Corre en su propia base de demo y en orden, porque consolidar
 * escribe notas. Reloj en el lunes 15/03/2027, bimestre I.
 */
@Import(RelojPruebaConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatosDemoFase5IT extends BaseIT {

    private static final String BASE_DE_DATOS = "gesedu_demo_fase5";
    private static final LocalDate LUNES = LocalDate.of(2027, 3, 15);

    @Autowired
    private RelojPrueba reloj;

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

    private String token(String cuenta) throws Exception {
        reloj.fijar(LUNES, "07:40");
        return tokenDe(cuenta + "@demo.gesedu.pe");
    }

    private String asignacionDeFernando() throws Exception {
        for (JsonNode a : get("/api/v1/asignaciones-docentes/mias", token("docente2")).cuerpo()) {
            if (a.get("curso").asString().equals("Matemática") && a.get("seccion").asString().equals("3° A")) {
                return a.get("id").asString();
            }
        }
        throw new AssertionError("Fernando no tiene Matemática en 3° A");
    }

    private static List<String> textos(JsonNode lista, String campo) {
        List<String> resultado = new ArrayList<>();
        lista.forEach(x -> resultado.add(x.get(campo).asString()));
        return resultado;
    }

    private HttpResponse<byte[]> pdf(String ruta, String token) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Authorization", "Bearer " + token).GET().build();
        return HttpClient.newHttpClient().send(peticion, HttpResponse.BodyHandlers.ofByteArray());
    }

    // ---- la semilla ----

    @Test
    @Order(1)
    void laSemillaDejaLasAsignacionesLosLogrosDelBimestreIYElAnioAnteriorCompleto() {
        Integer combinaciones = jdbc.queryForObject("SELECT count(*) FROM (SELECT DISTINCT curso_id, seccion_id FROM horarios.asignacion_horario) x", Integer.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluacion.asignacion_docente a JOIN academico.seccion s ON s.id = a.seccion_id "
                + "JOIN academico.anio_escolar ae ON ae.id = s.anio_escolar_id WHERE ae.anio = 2027", Integer.class))
                .as("una asignación por curso y sección del borrador").isEqualTo(combinaciones);
        assertThat(jdbc.queryForObject("SELECT regla_consolidacion FROM evaluacion.asignacion_docente a JOIN academico.curso c ON c.id = a.curso_id "
                + "JOIN academico.seccion s ON s.id = a.seccion_id JOIN academico.grado g ON g.id = s.grado_id "
                + "WHERE c.slug = 'matematica' AND g.numero_nivel = 3 AND s.letra = 'A'", String.class)).as("como el wireframe").isEqualTo("MODA");

        // Lucía tiene todo el bimestre I registrado; en Matemática de 3° A faltan dos celdas (C3 de dos alumnos)
        Integer competenciasMatematica = jdbc.queryForObject("SELECT count(*) FROM academico.competencia k JOIN academico.curso c ON c.id = k.curso_id "
                + "JOIN academico.anio_escolar ae ON ae.id = c.anio_escolar_id WHERE ae.anio = 2027 AND c.slug = 'matematica'", Integer.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluacion.registro_logro l JOIN evaluacion.asignacion_docente a ON a.id = l.asignacion_id "
                + "JOIN academico.curso c ON c.id = a.curso_id JOIN academico.seccion s ON s.id = a.seccion_id JOIN academico.grado g ON g.id = s.grado_id "
                + "WHERE c.slug = 'matematica' AND g.numero_nivel = 3 AND s.letra = 'A'", Integer.class))
                .as("6 alumnos × competencias − 2").isEqualTo(6 * competenciasMatematica - 2);

        // 2° A de 2026: cuatro bimestres consolidados, notas anuales y libreta FINAL de cada alumno
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluacion.consolidacion_bimestral cb JOIN academico.bimestre b ON b.id = cb.bimestre_id "
                + "JOIN academico.anio_escolar ae ON ae.id = b.anio_escolar_id WHERE ae.anio = 2026 AND cb.estado = 'COMPLETADA'", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluacion.libreta l JOIN academico.anio_escolar ae ON ae.id = l.anio_escolar_id "
                + "WHERE ae.anio = 2026 AND l.estado = 'FINAL'", Integer.class)).isEqualTo(2);
        Integer competencias2026 = jdbc.queryForObject("SELECT count(*) FROM academico.competencia k JOIN academico.curso c ON c.id = k.curso_id "
                + "JOIN academico.anio_escolar ae ON ae.id = c.anio_escolar_id WHERE ae.anio = 2026", Integer.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluacion.nota_consolidada n JOIN academico.anio_escolar ae ON ae.id = n.anio_escolar_id "
                + "WHERE ae.anio = 2026 AND n.tipo = 'ANUAL'", Integer.class)).isEqualTo(2 * competencias2026);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluacion.nota_consolidada n JOIN academico.anio_escolar ae ON ae.id = n.anio_escolar_id "
                + "WHERE ae.anio = 2026 AND n.tipo = 'BIMESTRAL'", Integer.class)).isEqualTo(2 * 4 * competencias2026);
    }

    // ---- UC-15 y UC-16: Fernando Medina, docente de Matemática de 3° A ----

    @Test
    @Order(2)
    void rosaTutoraDe3ADictaMatematicaEn3BYNoVeLaRejillaDeFernando() throws Exception {
        Respuesta mias = get("/api/v1/asignaciones-docentes/mias", token("docente"));

        assertThat(mias.estado()).isEqualTo(200);
        List<String> secciones = new ArrayList<>();
        for (JsonNode a : mias.cuerpo()) {
            if (a.get("curso").asString().equals("Matemática")) {
                secciones.add(a.get("seccion").asString());
            }
        }
        assertThat(secciones).contains("3° B").doesNotContain("3° A");
        assertThat(get("/api/v1/asignaciones-docentes/" + asignacionDeFernando() + "/registros", token("docente")).estado())
                .as("la rejilla de otro docente").isEqualTo(403);
    }

    @Test
    @Order(3)
    void fernandoVeSuRejillaIncompletaConElAvisoDeLosDosAlumnosPendientes() throws Exception {
        String asignacion = asignacionDeFernando();

        Respuesta r = get("/api/v1/asignaciones-docentes/" + asignacion + "/registros", token("docente2"));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("editable").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("bimestre").get("numero").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get("alumnos").size()).isEqualTo(6);
        assertThat(r.cuerpo().get("aviso").asString()).isEqualTo("Hay 2 alumnos con competencias sin calificar");
        assertThat(r.cuerpo().get("incompletos").size()).isEqualTo(2);
    }

    @Test
    @Order(4)
    void fernandoVeQueSuReglaEsLaModaYPuedeCambiarla() throws Exception {
        String asignacion = asignacionDeFernando();

        Respuesta r = get("/api/v1/asignaciones-docentes/" + asignacion + "/configuracion", token("docente2"));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("regla").asString()).isEqualTo("MODA");
        assertThat(r.cuerpo().get("reglas").size()).isEqualTo(3);
        assertThat(enviar("PUT", "/api/v1/asignaciones-docentes/" + asignacion + "/configuracion",
                json.writeValueAsString(Map.of("regla", "PROMEDIO_PONDERADO")), token("docente2"), null).estado()).isEqualTo(200);
        assertThat(enviar("PUT", "/api/v1/asignaciones-docentes/" + asignacion + "/configuracion",
                json.writeValueAsString(Map.of("regla", "MODA")), token("docente2"), null).estado()).isEqualTo(200);
    }

    // ---- UC-18: el administrador no puede consolidar hasta que Fernando complete ----

    @Test
    @Order(5)
    void elAdministradorVeQueSoloFaltaMatematicaDeFernandoYNoPuedeConsolidar() throws Exception {
        Respuesta avance = get("/api/v1/anios-escolares/2027/bimestres/1/avance", token("admin"));

        assertThat(avance.estado()).isEqualTo(200);
        assertThat(avance.cuerpo().get("totales").get("incompletos").asInt()).isEqualTo(1);
        assertThat(avance.cuerpo().get("puedeConsolidar").asBoolean()).isFalse();
        assertThat(avance.cuerpo().get("docentesPendientes").get(0).asString()).isEqualTo("Fernando Medina Soto");
        assertThat(avance.cuerpo().get("cursos").get(0).get("curso").asString()).isEqualTo("Matemática");
        assertThat(avance.cuerpo().get("cursos").get(0).get("seccion").asString()).isEqualTo("3° A");

        Respuesta bloqueo = post("/api/v1/anios-escolares/2027/bimestres/1/consolidacion", "", token("admin"));
        assertThat(bloqueo.estado()).isEqualTo(422);
        assertThat(bloqueo.cuerpo().get("codigo").asString()).isEqualTo("BIMESTRE_INCOMPLETO");
        assertThat(bloqueo.cuerpo().get("docentes").get(0).asString()).isEqualTo("Fernando Medina Soto");
        // El directivo (mismo rol, permiso NOTA_CONSOLIDAR) ve lo mismo
        assertThat(get("/api/v1/anios-escolares/2027/bimestres/1/avance", token("directivo")).estado()).isEqualTo(200);
    }

    @Test
    @Order(6)
    void fernandoCompletaLasDosCeldasYElAvisoDesaparece() throws Exception {
        String asignacion = asignacionDeFernando();
        JsonNode rejilla = get("/api/v1/asignaciones-docentes/" + asignacion + "/registros", token("docente2")).cuerpo();
        String c3 = null;
        for (JsonNode c : rejilla.get("competencias")) {
            if (c.get("codigo").asString().equals("C3")) {
                c3 = c.get("id").asString();
            }
        }
        List<Map<String, String>> logros = new ArrayList<>();
        for (JsonNode pendiente : rejilla.get("incompletos")) {
            logros.add(Map.of("alumnoId", pendiente.get("alumnoId").asString(), "competenciaId", c3, "nivel", "A"));
        }
        assertThat(logros).hasSize(2);

        Respuesta r = enviar("PUT", "/api/v1/asignaciones-docentes/" + asignacion + "/registros",
                json.writeValueAsString(Map.of("logros", logros)), token("docente2"), null);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("cambios").asInt()).isEqualTo(2);
        assertThat(r.cuerpo().get("aviso").isNull()).isTrue();
        assertThat(r.cuerpo().get("celdasCompletas").asInt()).isEqualTo(r.cuerpo().get("celdasTotales").asInt());
    }

    @Test
    @Order(7)
    void elAdministradorConsolidaElBimestreIYLaLibretaDeLuciaSeActualiza() throws Exception {
        Respuesta avance = get("/api/v1/anios-escolares/2027/bimestres/1/avance", token("admin"));
        assertThat(avance.cuerpo().get("puedeConsolidar").asBoolean()).isTrue();

        Respuesta r = post("/api/v1/anios-escolares/2027/bimestres/1/consolidacion", "", token("admin"));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("COMPLETADA");
        assertThat(r.cuerpo().get("resultado").get("actualizadas").asInt()).isZero();
        assertThat(r.cuerpo().get("resultado").get("creadas").asInt()).isEqualTo(
                jdbc.queryForObject("SELECT count(*) FROM evaluacion.nota_consolidada n JOIN academico.anio_escolar ae ON ae.id = n.anio_escolar_id "
                        + "WHERE ae.anio = 2027 AND n.tipo = 'BIMESTRAL'", Integer.class));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluacion.libreta l JOIN academico.anio_escolar ae ON ae.id = l.anio_escolar_id "
                + "WHERE ae.anio = 2027 AND l.estado = 'PARCIAL'", Integer.class)).isGreaterThanOrEqualTo(6);
        assertThat(post("/api/v1/anios-escolares/2027/bimestres/1/consolidacion", "", token("admin")).cuerpo().get("resultado").get("creadas").asInt())
                .as("consolidar otra vez no duplica").isZero();
    }

    // ---- UC-19: Lucía, su apoderada y la familia Torres ----

    @Test
    @Order(8)
    void luciaVeSusCursosConLasNotasDelBimestreIYDescargaSuInforme() throws Exception {
        String alumno = alumnoDe("alumno");

        Respuesta r = get("/api/v1/alumnos/" + alumno + "/cursos", token("alumno"));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("seccion").asString()).isEqualTo("3° A");
        assertThat(r.cuerpo().get("anio").asInt()).isEqualTo(2027);
        assertThat(r.cuerpo().get("libreta").asString()).isEqualTo("PARCIAL");
        assertThat(r.cuerpo().get("bimestre").asInt()).isEqualTo(1);
        // Los cursos de 3° A que tiene el borrador (al publicar se completa Inglés y Educación Física, UC-22)
        assertThat(textos(r.cuerpo().get("cursos"), "curso")).hasSize(jdbc.queryForObject("SELECT count(*) FROM evaluacion.asignacion_docente a "
                + "JOIN academico.seccion s ON s.id = a.seccion_id JOIN academico.grado g ON g.id = s.grado_id "
                + "JOIN academico.anio_escolar ae ON ae.id = s.anio_escolar_id WHERE ae.anio = 2027 AND g.numero_nivel = 3 AND s.letra = 'A'",
                Integer.class)).contains("Matemática");
        for (JsonNode curso : r.cuerpo().get("cursos")) {
            for (JsonNode competencia : curso.get("competencias")) {
                assertThat(competencia.get("bimestres").get("1")).as("%s %s", curso.get("curso").asString(), competencia.get("codigo").asString()).isNotNull();
                assertThat(competencia.get("nivelEsperado").isNull()).isFalse();
            }
        }
        HttpResponse<byte[]> informe = pdf("/api/v1/alumnos/" + alumno + "/libreta?formato=pdf&bimestre=1", token("alumno"));
        assertThat(informe.statusCode()).isEqualTo(200);
        assertThat(new String(informe.body(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    @Order(9)
    void laApoderadaDeLuciaVeLoMismoPeroNoAOtrosAlumnos() throws Exception {
        String alumno = alumnoDe("alumno");

        Respuesta hijos = get("/api/v1/apoderados/yo/alumnos", token("apoderado"));
        Respuesta cursos = get("/api/v1/alumnos/" + alumno + "/cursos", token("apoderado"));
        Respuesta ajeno = get("/api/v1/alumnos/" + alumnoDe("alumno") + "/cursos", token("familia.torres"));

        assertThat(textos(hijos.cuerpo(), "alumnoId")).contains(alumno);
        assertThat(cursos.estado()).isEqualTo(200);
        assertThat(cursos.cuerpo().get("libreta").asString()).isEqualTo("PARCIAL");
        assertThat(ajeno.estado()).isEqualTo(403);
    }

    @Test
    @Order(10)
    void laFamiliaTorresVeLaLibretaFinalDeValeriaDe2026() throws Exception {
        Respuesta hijos = get("/api/v1/apoderados/yo/alumnos", token("familia.torres"));
        assertThat(hijos.estado()).isEqualTo(200);
        assertThat(hijos.cuerpo().size()).as("FA-1: Tomás y Valeria").isEqualTo(2);
        String valeria = null;
        for (JsonNode h : hijos.cuerpo()) {
            if (h.get("nombre").asString().contains("Valeria")) {
                valeria = h.get("alumnoId").asString();
            }
        }
        assertThat(valeria).isNotNull();

        Respuesta libretas = get("/api/v1/alumnos/" + valeria + "/libretas", token("familia.torres"));
        assertThat(libretas.cuerpo().size()).isEqualTo(1);
        assertThat(libretas.cuerpo().get(0).get("anio").asInt()).isEqualTo(2026);
        assertThat(libretas.cuerpo().get(0).get("estado").asString()).isEqualTo("FINAL");

        // Sin matrícula 2027 todavía: lo que se ve por omisión es la libreta de 2026
        Respuesta cursos = get("/api/v1/alumnos/" + valeria + "/cursos", token("familia.torres"));
        assertThat(cursos.cuerpo().get("anio").asInt()).isEqualTo(2026);
        assertThat(cursos.cuerpo().get("seccion").asString()).isEqualTo("2° A");
        assertThat(cursos.cuerpo().get("libreta").asString()).isEqualTo("FINAL");
        assertThat(cursos.cuerpo().get("bimestre").asInt()).isEqualTo(4);
        assertThat(cursos.cuerpo().get("cursos").size()).isEqualTo(6);
        for (JsonNode curso : cursos.cuerpo().get("cursos")) {
            for (JsonNode competencia : curso.get("competencias")) {
                assertThat(competencia.get("bimestres").size()).isEqualTo(4);
                assertThat(competencia.get("anual").isNull()).isFalse();
            }
        }
        Respuesta libreta = get("/api/v1/alumnos/" + valeria + "/libreta", token("familia.torres"));
        assertThat(libreta.cuerpo().get("estado").asString()).isEqualTo("FINAL");
        assertThat(libreta.cuerpo().get("hastaBimestre").isNull()).isTrue();
        assertThat(pdf("/api/v1/alumnos/" + valeria + "/libreta?formato=pdf", token("familia.torres")).statusCode()).isEqualTo(200);
    }

    // ---- UC-17: el cálculo de Fernando ----

    @Test
    @Order(11)
    void fernandoCalculaLasNotasFinalesConLosLogrosDelBimestreI() throws Exception {
        String asignacion = asignacionDeFernando();

        Respuesta r = post("/api/v1/asignaciones-docentes/" + asignacion + "/notas-finales/calculo", "", token("docente2"));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("regla").asString()).isEqualTo("MODA");
        assertThat(r.cuerpo().get("calculadas").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("calculo").get("creadas").asInt()).isEqualTo(r.cuerpo().get("alumnos").size() * r.cuerpo().get("competencias").size());
        assertThat(post("/api/v1/asignaciones-docentes/" + asignacion + "/notas-finales/calculo", "", token("docente2"))
                .cuerpo().get("calculo").get("creadas").asInt()).isZero();
    }

    private String alumnoDe(String cuenta) {
        return jdbc.queryForObject("SELECT al.id FROM seguridad.alumno al JOIN seguridad.usuario u ON u.id = al.usuario_id WHERE u.correo = ?",
                String.class, cuenta + "@demo.gesedu.pe");
    }
}
