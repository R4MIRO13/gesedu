package pe.edu.gesedu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
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
import pe.edu.gesedu.asistencia.aplicacion.JobCierreDiario;

/**
 * La semilla de la fase 4 (V1004) y el recorrido de cada caso de uso de asistencia con los usuarios que deja listos:
 * UC-07, UC-08, UC-09, UC-12, UC-10, UC-11 y el resumen del inicio del alumno, con el reloj en el lunes 15/03/2027 a las
 * 7:40 como en la demo. Corre en su propia base de demo y en orden, porque el cierre diario escribe faltas.
 */
@Import(RelojPruebaConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatosDemoFase4IT extends BaseIT {

    private static final String BASE_DE_DATOS = "gesedu_demo_fase4";
    private static final LocalDate LUNES = LocalDate.of(2027, 3, 15);

    @Autowired
    private RelojPrueba reloj;

    @Autowired
    private JobCierreDiario job;

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
        return tokenDe(cuenta + "@demo.gesedu.pe");
    }

    private void enLaDemo(String hora) {
        reloj.fijar(LUNES, hora);
    }

    private String lote(List<String> alumnos, List<String> estados) throws Exception {
        List<Map<String, String>> items = new ArrayList<>();
        for (int i = 0; i < alumnos.size(); i++) {
            items.add(Map.of("alumnoId", alumnos.get(i), "estado", estados.get(i)));
        }
        return json.writeValueAsString(Map.of("asistencias", items));
    }

    // ---- la semilla ----

    @Test
    @Order(1)
    void laSemillaDejaTresAlumnosHistorialYCierresDeLasDosSemanasAnteriores() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM matricula.matricula m JOIN academico.seccion s ON s.id = m.seccion_id "
                + "JOIN academico.grado g ON g.id = s.grado_id JOIN academico.anio_escolar ae ON ae.id = g.anio_escolar_id "
                + "WHERE ae.anio = 2027 AND s.letra = 'A' AND g.numero_nivel = 3 AND m.estado = 'CONFIRMADA'",
                Integer.class)).as("Lucía y los cinco nuevos").isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT fecha) FROM asistencia.asistencia_alumno", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asistencia.cierre_diario WHERE estado = 'EXITOSO'", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_docente WHERE fecha = ?", Integer.class,
                java.sql.Date.valueOf(LUNES))).as("tres docentes ya marcaron el lunes").isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_docente ad JOIN seguridad.docente d ON d.id = ad.docente_id "
                + "WHERE d.codigo_docente = 'DOC-001' AND ad.estado = 'FALTA' AND ad.hora_registro IS NULL", Integer.class))
                .as("la falta del cierre de Rosa, sin hora").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_docente ad JOIN seguridad.docente d ON d.id = ad.docente_id "
                + "WHERE d.codigo_docente = 'DOC-001' AND ad.fecha = ?", Integer.class, java.sql.Date.valueOf(LUNES)))
                .as("Rosa no ha marcado el lunes: lo hará en la demo").isZero();
    }

    @Test
    @Order(2)
    void laCuentaDeRosaVeLaSeccionConSusSeisAlumnosEnOrdenYSinRegistrosHoy() throws Exception {
        enLaDemo("07:40");

        Respuesta r = get("/api/v1/secciones/3A/asistencia", token("docente"));

        assertThat(r.estado()).isEqualTo(200);
        List<String> apellidos = new ArrayList<>();
        r.cuerpo().get("alumnos").forEach(a -> apellidos.add(a.get("apellidos").asString()));
        assertThat(apellidos).containsExactly("Bravo Díaz", "Castro Soto", "García Rojas", "Huamán León", "Mendoza Ruiz", "Paredes Vega");
        assertThat(r.cuerpo().get("totales").get("pendientes").asInt()).isEqualTo(6);
        assertThat(r.cuerpo().get("registroHabilitado").asBoolean()).isTrue();
        assertThat(get("/api/v1/asistencia-alumnos/mi-seccion", token("docente")).cuerpo().get("alumnos").asInt()).isEqualTo(6);
        assertThat(get("/api/v1/asistencia-alumnos/mi-seccion", token("docente2")).estado()).as("Fernando no es tutor").isEqualTo(403);
    }

    @Test
    @Order(3)
    void sinHorarioPublicadoLaMarcacionDeRosaSeRechaza() throws Exception {
        enLaDemo("07:40");

        Respuesta r = enviar("POST", "/api/v1/asistencia-docente/marcaciones", null, token("docente"), null);

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("SIN_CLASES_PROGRAMADAS");
    }

    @Test
    @Order(4)
    void elAtajoPublicaElHorarioSinRecorrerElPaso12() throws Exception {
        String sql = Files.readString(Path.of("../docs/demo/atajos/publicar-horario.sql"));

        jdbc.execute(sql);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.horario_publicado WHERE vigente", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.asignacion_publicada", Integer.class)).isGreaterThan(200);
    }

    // ---- el recorrido de la demo ----

    @Test
    @Order(5)
    void uc07RosaMarcaSuLlegadaAntesDeSuPrimeraClaseYQuedaPuntual() throws Exception {
        enLaDemo("07:40");

        Respuesta hoy = get("/api/v1/asistencia-docente/hoy", token("docente"));
        Respuesta marca = enviar("POST", "/api/v1/asistencia-docente/marcaciones", null, token("docente"), null);

        assertThat(hoy.cuerpo().get("puedeMarcar").asBoolean()).isTrue();
        assertThat(hoy.cuerpo().get("primerBloque").isNull()).isFalse();
        assertThat(marca.estado()).isEqualTo(201);
        assertThat(marca.cuerpo().get("estado").asString()).isEqualTo("PUNTUAL");
        assertThat(enviar("POST", "/api/v1/asistencia-docente/marcaciones", null, token("docente"), null).estado()).as("FA-1").isEqualTo(409);
        Respuesta historial = get("/api/v1/asistencia-docente/mias", token("docente"));
        assertThat(historial.cuerpo().size()).as("hoy y los diez días sembrados").isEqualTo(11);
    }

    @Test
    @Order(6)
    void uc08RosaPasaListaConLosTresEstadosYUc09CambiaUnaFaltaATardanza() throws Exception {
        enLaDemo("07:55");
        Respuesta seccion = get("/api/v1/secciones/3A/asistencia", token("docente"));
        List<String> ids = new ArrayList<>();
        seccion.cuerpo().get("alumnos").forEach(a -> ids.add(a.get("alumnoId").asString()));

        Respuesta r = enviar("POST", "/api/v1/secciones/3A/asistencia",
                lote(ids, List.of("PUNTUAL", "PUNTUAL", "PUNTUAL", "TARDANZA", "FALTA", "PUNTUAL")), token("docente"), null);

        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("totales").get("puntual").asInt()).isEqualTo(4);
        assertThat(r.cuerpo().get("totales").get("tardanza").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get("totales").get("falta").asInt()).isEqualTo(1);

        enLaDemo("08:10");
        String registroDeLaFalta = null;
        for (JsonNode a : get("/api/v1/secciones/3A/asistencia", token("docente")).cuerpo().get("alumnos")) {
            if ("FALTA".equals(a.get("estado").asString())) {
                registroDeLaFalta = a.get("registroId").asString();
            }
        }
        Respuesta cambio = enviar("PATCH", "/api/v1/asistencia-alumnos/" + registroDeLaFalta, "{\"estado\":\"TARDANZA\"}", token("docente"), null);
        assertThat(cambio.estado()).isEqualTo(200);
        assertThat(cambio.cuerpo().get("estado").asString()).isEqualTo("TARDANZA");

        enLaDemo("08:31");
        String otraFalta = jdbc.queryForObject("SELECT id FROM asistencia.asistencia_alumno WHERE fecha = ? AND estado = 'PUNTUAL' LIMIT 1",
                String.class, java.sql.Date.valueOf(LUNES));
        assertThat(enviar("PATCH", "/api/v1/asistencia-alumnos/" + otraFalta, "{\"estado\":\"TARDANZA\"}", token("docente"), null).estado())
                .as("después del límite o sobre un puntual no se modifica").isIn(400, 422);
    }

    @Test
    @Order(7)
    void elAdministradorVeQuienMarcoYAntesDelCierreLosDemasEstanSinRegistrar() throws Exception {
        enLaDemo("10:00");

        JsonNode dia = get("/api/v1/asistencia-docente", token("admin")).cuerpo();

        assertThat(dia.get("fecha").asString()).isEqualTo(LUNES.toString());
        assertThat(dia.get("diaLectivo").asBoolean()).isTrue();
        assertThat(dia.get("cierre").asString()).isEqualTo("PENDIENTE");
        assertThat(dia.get("totales").get("puntual").asInt()).as("Rosa y los tres sembrados (los que tienen clases el lunes)").isGreaterThanOrEqualTo(2);
        assertThat(dia.get("totales").get("sinRegistrar").asInt()).isGreaterThan(0);
        assertThat(dia.get("totales").get("falta").asInt()).isZero();
    }

    @Test
    @Order(8)
    void uc12ElCierreDeLas1530GeneraLasFaltasDeQuienNoMarcoYNoSeRepite() throws Exception {
        enLaDemo("15:30");
        int sinMarcar = jdbc.queryForObject("SELECT count(DISTINCT ap.docente_id) FROM horarios.asignacion_publicada ap "
                + "JOIN horarios.bloque_horario b ON b.id = ap.bloque_id WHERE b.dia_semana = 'LUNES' AND ap.docente_id NOT IN "
                + "(SELECT docente_id FROM asistencia.asistencia_docente WHERE fecha = ?)", Integer.class, java.sql.Date.valueOf(LUNES));
        assertThat(sinMarcar).isGreaterThan(0);

        job.ejecutar();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_docente WHERE fecha = ? AND estado = 'FALTA' "
                + "AND hora_registro IS NULL", Integer.class, java.sql.Date.valueOf(LUNES))).isEqualTo(sinMarcar);
        job.ejecutar();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_docente WHERE fecha = ? AND estado = 'FALTA'",
                Integer.class, java.sql.Date.valueOf(LUNES))).as("el segundo disparo no duplica").isEqualTo(sinMarcar);
        JsonNode dia = get("/api/v1/asistencia-docente", token("admin")).cuerpo();
        assertThat(dia.get("cierre").asString()).isEqualTo("PROCESADO");
        assertThat(dia.get("totales").get("falta").asInt()).isEqualTo(sinMarcar);
        boolean algunaGenerada = false;
        for (JsonNode f : dia.get("docentes")) {
            algunaGenerada |= f.get("generadaAlCierre").asBoolean();
        }
        assertThat(algunaGenerada).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_alumno WHERE fecha = ? AND alumno_id NOT IN "
                + "(SELECT al.id FROM seguridad.alumno al JOIN matricula.matricula m ON m.alumno_id = al.id)", Integer.class,
                java.sql.Date.valueOf(LUNES))).as("el cierre no crea registros de alumnos").isZero();
    }

    @Test
    @Order(9)
    void uc10UC11ElTutorYElAdministradorObtienenElReporteDeMarzoEnLosTresFormatos() throws Exception {
        reloj.fijar(LUNES.plusDays(1), "10:00");

        JsonNode alumnos = get("/api/v1/reportes/asistencia?ambito=ALUMNOS&mes=3&anio=2027", token("docente")).cuerpo();

        assertThat(alumnos.get("alcance").asString()).isEqualTo("3° A");
        assertThat(alumnos.get("parcial").asBoolean()).isTrue();
        assertThat(alumnos.get("fechaCorte").asString()).isEqualTo(LUNES.toString());
        assertThat(alumnos.get("diasLectivos").asInt()).as("del 01/03 al 15/03: lunes a viernes").isEqualTo(11);
        assertThat(alumnos.get("filas").size()).isEqualTo(6);
        for (JsonNode f : alumnos.get("filas")) {
            assertThat(f.get("porcentaje").isNull()).as(f.get("nombre").asString()).isFalse();
            assertThat(f.get("registrados").asInt() + f.get("noRegistrados").asInt()).isGreaterThanOrEqualTo(0);
        }
        assertThat(get("/api/v1/reportes/asistencia?ambito=DOCENTES&mes=3&anio=2027", token("docente")).estado()).isEqualTo(403);
        JsonNode docentes = get("/api/v1/reportes/asistencia?ambito=DOCENTES&mes=3&anio=2027", token("admin")).cuerpo();
        assertThat(docentes.get("alcance").asString()).isEqualTo("Personal docente");
        assertThat(docentes.get("filas").size()).isGreaterThanOrEqualTo(8);
        assertThat(descargar("/api/v1/reportes/asistencia?ambito=ALUMNOS&mes=3&anio=2027&seccion=3A&formato=xlsx", token("admin")).estado()).isEqualTo(200);
        assertThat(descargar("/api/v1/reportes/asistencia?ambito=DOCENTES&mes=3&anio=2027&formato=pdf", token("admin")).estado()).isEqualTo(200);
    }

    @Test
    @Order(10)
    void elInicioDeLuciaMuestraSuResumenDeMarzoIgualAlDelReporte() throws Exception {
        reloj.fijar(LUNES.plusDays(1), "10:00");
        String lucia = jdbc.queryForObject("SELECT al.id FROM seguridad.alumno al JOIN seguridad.usuario u ON u.id = al.usuario_id "
                + "WHERE u.correo = 'alumno@demo.gesedu.pe'", String.class);

        Respuesta r = get("/api/v1/alumnos/" + lucia + "/asistencia/resumen?mes=2027-03", token("alumno"));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("porcentaje").asString()).isNotBlank();
        assertThat(r.cuerpo().get("registrados").asInt()).isGreaterThan(5);
        JsonNode reporte = get("/api/v1/reportes/asistencia?ambito=ALUMNOS&mes=3&anio=2027&seccion=3A", token("admin")).cuerpo();
        String esperado = null;
        for (JsonNode f : reporte.get("filas")) {
            if (f.get("nombre").asString().startsWith("García Rojas")) {
                esperado = f.get("porcentaje").asString();
            }
        }
        assertThat(r.cuerpo().get("porcentaje").asString()).isEqualTo(esperado);
        assertThat(get("/api/v1/alumnos/" + lucia + "/asistencia/resumen", token("apoderado")).estado()).as("el apoderado no tiene este permiso").isEqualTo(403);
    }
}
