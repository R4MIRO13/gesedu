package pe.edu.gesedu;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import pe.edu.gesedu.apoyo.BaseIT;

/**
 * La semilla de la demo (perfil demo, docs/PLAN.md § 7.2) y el guion de UC-21 sobre ella. Corre en una base de datos
 * propia dentro del mismo contenedor, para que los datos de demo no se mezclen con las demás pruebas.
 */
class DatosDemoIT extends BaseIT {

    private static final String BASE_DE_DATOS = "gesedu_demo";

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

    // Mismo perfil que application-demo.yml (la propiedad solo existe en este contexto)
    @DynamicPropertySource
    static void perfilDemo(DynamicPropertyRegistry registro) {
        registro.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/demo");
    }

    /** El guion guarda una clase nueva en 3° A (martes de 9:15): se quita para que las pruebas no dependan del orden. */
    @AfterEach
    void quitarLaClaseDelGuion() {
        jdbc.update("DELETE FROM horarios.asignacion_horario WHERE bloque_id IN (SELECT id FROM horarios.bloque_horario "
                + "WHERE dia_semana = 'MARTES' AND hora_inicio = TIME '09:15') AND seccion_id IN (SELECT s.id FROM "
                + "academico.seccion s JOIN academico.grado g ON g.id = s.grado_id WHERE g.numero_nivel = 3 AND s.letra = 'A')");
    }

    private static final String ANIO_2027 = "(SELECT id FROM academico.anio_escolar WHERE anio = 2027)";

    private String admin() throws Exception {
        return tokenDe("admin@demo.gesedu.pe");
    }

    private long contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class);
    }

    // --- Cuentas y menús por rol ---

    @Test
    void lasSeisCuentasDeLaDemoEntranConLaContrasenaComun() throws Exception {
        Map<String, String> roles = Map.of(
                "admin@demo.gesedu.pe", "ADMINISTRADOR", "directivo@demo.gesedu.pe", "ADMINISTRADOR",
                "docente@demo.gesedu.pe", "DOCENTE", "alumno@demo.gesedu.pe", "ALUMNO",
                "apoderado@demo.gesedu.pe", "APODERADO", "bloqueo@demo.gesedu.pe", "DOCENTE");
        for (var cuenta : roles.entrySet()) {
            Respuesta r = iniciarSesion(cuenta.getKey(), CONTRASENA);
            assertThat(r.estado()).as(cuenta.getKey()).isEqualTo(200);
            assertThat(r.cuerpo().get("usuario").get("rol").asString()).isEqualTo(cuenta.getValue());
        }
    }

    @Test
    void cadaRolVeUnMenuDistintoYElDirectivoMuestraSuCargo() throws Exception {
        JsonNode admin = iniciarSesion("admin@demo.gesedu.pe", CONTRASENA).cuerpo().get("usuario");
        JsonNode directivo = iniciarSesion("directivo@demo.gesedu.pe", CONTRASENA).cuerpo().get("usuario");
        JsonNode alumno = iniciarSesion("alumno@demo.gesedu.pe", CONTRASENA).cuerpo().get("usuario");

        assertThat(admin.get("cargo").asString()).isEqualTo("Secretaría académica");
        assertThat(directivo.get("cargo").asString()).isEqualTo("Coordinación académica");
        assertThat(admin.get("permisos").valueStream().map(JsonNode::asString).toList())
                .contains("HORARIO_GESTIONAR", "AUDITORIA_CONSULTAR");
        assertThat(alumno.get("permisos").valueStream().map(JsonNode::asString).toList())
                .contains("HORARIO_CONSULTAR").doesNotContain("HORARIO_GESTIONAR", "AUDITORIA_CONSULTAR");
        assertThat(get("/api/v1/auditoria/registros", tokenDe("alumno@demo.gesedu.pe")).estado()).isEqualTo(403);
    }

    // --- Datos del año 2027 ---

    @Test
    void elAnio2027TieneCuatroBimestresDiezSeccionesSeisCursosYSusBloques() {
        assertThat(contar("academico.bimestre")).isEqualTo(4);
        assertThat(contar("academico.grado WHERE anio_escolar_id = " + ANIO_2027)).isEqualTo(5);
        assertThat(contar("academico.seccion WHERE anio_escolar_id = " + ANIO_2027)).isEqualTo(10);
        assertThat(contar("academico.curso")).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT sum(horas_lectivas) FROM academico.curso", Long.class)).isEqualTo(22);
        assertThat(contar("horarios.bloque_horario")).isEqualTo(45);
        assertThat(contar("academico.competencia")).isEqualTo(18);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academico.competencia c JOIN academico.curso cu ON "
                + "cu.id = c.curso_id WHERE cu.slug = 'matematica'", Long.class)).isEqualTo(4);
        assertThat(contar("horarios.carga_horaria_curso")).isEqualTo(30);
    }

    @Test
    void lasVacantesDeLaDemoSon4EnTercerAnioAY1EnSegundoB() {
        Map<String, Object> tercerA = jdbc.queryForMap("SELECT s.vacantes_disponibles, s.capacidad, "
                + "u.nombres FROM academico.seccion s JOIN academico.grado g ON g.id = s.grado_id "
                + "JOIN seguridad.docente d ON d.id = s.tutor_docente_id JOIN seguridad.usuario u ON u.id = d.usuario_id "
                + "WHERE g.numero_nivel = 3 AND s.letra = 'A' AND g.anio_escolar_id = " + ANIO_2027);
        assertThat(tercerA.get("vacantes_disponibles")).isEqualTo(4);
        assertThat(tercerA.get("capacidad")).isEqualTo(30);
        assertThat(tercerA.get("nombres")).as("tutora de 3° A").isEqualTo("Rosa");
        assertThat(jdbc.queryForObject("SELECT s.vacantes_disponibles FROM academico.seccion s JOIN academico.grado g "
                + "ON g.id = s.grado_id WHERE g.numero_nivel = 2 AND s.letra = 'B' AND g.anio_escolar_id = " + ANIO_2027, Integer.class)).isEqualTo(1);
    }

    @Test
    void luciaTieneSuMatriculaConfirmadaEn3A() {
        Map<String, Object> matricula = jdbc.queryForMap("SELECT m.estado, sm.codigo_seguimiento, g.numero_nivel, s.letra, "
                + "a.codigo_alumno FROM matricula.matricula m JOIN matricula.solicitud_matricula sm ON sm.id = m.solicitud_id "
                + "JOIN academico.seccion s ON s.id = m.seccion_id JOIN academico.grado g ON g.id = s.grado_id "
                + "JOIN seguridad.alumno a ON a.id = m.alumno_id WHERE a.codigo_alumno = '2026-0315'");
        assertThat(matricula).containsEntry("estado", "CONFIRMADA").containsEntry("numero_nivel", 3)
                .containsEntry("letra", "A").containsEntry("codigo_alumno", "2026-0315")
                .containsEntry("codigo_seguimiento", "MAT-2027-00478");
    }

    // --- Semilla P2: la familia Torres para probar pagos (UC-03, UC-04, UC-05) ---

    @Test
    void laFamiliaTorresEntraYTieneAlHijoMatriculadoConSusOnceCompromisos() throws Exception {
        Respuesta sesion = iniciarSesion("familia.torres@demo.gesedu.pe", CONTRASENA);
        assertThat(sesion.estado()).isEqualTo(200);
        assertThat(sesion.cuerpo().get("usuario").get("rol").asString()).isEqualTo("APODERADO");
        String token = sesion.cuerpo().get("tokenAcceso").asString();

        JsonNode hijos = get("/api/v1/matriculas/mias", token).cuerpo();
        assertThat(hijos.valueStream().map(h -> h.get("alumno").asString()).toList())
                .containsExactly("Tomás Torres Gil", "Valeria Torres Gil");
        JsonNode tomas = hijos.get(0);
        assertThat(tomas.get("matricula").get("estado").asString()).isEqualTo("Confirmada");
        assertThat(tomas.get("matricula").get("seccion").asString()).isEqualTo("1° A");
        assertThat(tomas.get("matricula").get("anio").asInt()).isEqualTo(2027);
        assertThat(hijos.get(1).get("matricula").isNull()).as("Valeria sigue con su solicitud pendiente").isTrue();

        JsonNode obligaciones = get("/api/v1/alumnos/" + tomas.get("alumnoId").asString() + "/obligaciones", token).cuerpo();
        assertThat(obligaciones.get("obligaciones")).hasSize(11);
        assertThat(obligaciones.get("resumen").get("totalPendiente").asString()).isEqualTo("3850.00");
        assertThat(obligaciones.get("obligaciones").get(0).get("puedePresentarComprobante").asBoolean()).isTrue();
    }

    @Test
    void laMatriculaDeTomasNoAlteraLasVacantesNiLasSolicitudesDelGuion() throws Exception {
        assertThat(jdbc.queryForObject("SELECT s.vacantes_disponibles FROM academico.seccion s JOIN academico.grado g ON g.id = s.grado_id "
                + "WHERE g.numero_nivel = 1 AND s.letra = 'A' AND g.anio_escolar_id = " + ANIO_2027, Integer.class)).isEqualTo(29);
        assertThat(jdbc.queryForObject("SELECT estado FROM matricula.solicitud_matricula WHERE codigo_seguimiento = 'MAT-2027-00482'",
                String.class)).isEqualTo("PENDIENTE_REVISION");
    }

    // --- Semilla P2: UC-02 (docs/PLAN.md § 11, paso 7) ---

    @Test
    void laBandejaTieneLasOchoSolicitudesPendientesDeLaDemo() throws Exception {
        JsonNode bandeja = get("/api/v1/solicitudes-matricula?estado=PENDIENTE_REVISION&size=50", admin()).cuerpo();

        assertThat(bandeja.get("contenido").valueStream().map(f -> f.get("codigo").asString()).toList())
                .containsExactly("MAT-2027-00479", "MAT-2027-00480", "MAT-2027-00481", "MAT-2027-00482",
                        "MAT-2027-00483", "MAT-2027-00484", "MAT-2027-00485", "MAT-2027-00486");
    }

    @Test
    void valeriaTorresPuedeConfirmarse3AConUnCostoDe3850() throws Exception {
        Respuesta r = get("/api/v1/solicitudes-matricula/MAT-2027-00482", admin());

        JsonNode detalle = r.cuerpo();
        assertThat(detalle.get("alumno").get("nombre").asString()).isEqualTo("Valeria Torres Gil");
        assertThat(detalle.get("puedeConfirmar").asBoolean()).isTrue();
        assertThat(detalle.get("costos").get("lineas")).hasSize(11);
        assertThat(detalle.get("costos").get("total").asString()).isEqualTo("3850.00");
        assertThat(detalle.get("secciones").valueStream().map(sec -> sec.get("nombre").asString() + ":" + sec.get("vacantes").asInt())
                .toList()).contains("3° A:4", "3° B:30");
    }

    @Test
    void cadaAlternativaDelGuionTieneSuSolicitudBloqueada() throws Exception {
        String admin = admin();
        // FA-3: Diego Paredes debe S/ 700.00 del 2026
        JsonNode deuda = get("/api/v1/solicitudes-matricula/MAT-2027-00479", admin).cuerpo();
        assertThat(deuda.get("puedeConfirmar").asBoolean()).isFalse();
        assertThat(deuda.get("validaciones").toString()).contains("S/ 700.00");
        // FA-1: Camila Soto no fue admitida
        assertThat(get("/api/v1/solicitudes-matricula/MAT-2027-00480", admin).cuerpo().get("validaciones").toString())
                .contains("NO_ADMITIDO");
        // FA-2: Mateo Ríos ya está matriculado en 2027
        assertThat(get("/api/v1/solicitudes-matricula/MAT-2027-00481", admin).cuerpo().get("validaciones").toString())
                .contains("DUPLICIDAD");
        // Joaquín Méndez tiene la documentación incompleta
        assertThat(get("/api/v1/solicitudes-matricula/MAT-2027-00485", admin).cuerpo().get("estadoDocumentacion").asString())
                .isEqualTo("INCOMPLETA");
    }

    @Test
    void lasSolicitudesQueCompitenPor2BVenLaUltimaVacante() throws Exception {
        for (String codigo : new String[] {"MAT-2027-00483", "MAT-2027-00484"}) {
            JsonNode secciones = get("/api/v1/solicitudes-matricula/" + codigo, admin()).cuerpo().get("secciones");
            assertThat(secciones.valueStream().map(sec -> sec.get("nombre").asString() + ":" + sec.get("vacantes").asInt()).toList())
                    .contains("2° B:1");
        }
    }

    // --- Guion de UC-21 sobre la semilla (docs/PLAN.md § 11, paso 4) ---

    private String id(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    @Test
    void elBorradorDeLaDemoTiene3ADe22HorasConDiecinueveAsignadas() throws Exception {
        String seccion3A = id("SELECT s.id FROM academico.seccion s JOIN academico.grado g ON g.id = s.grado_id "
                + "WHERE g.numero_nivel = 3 AND s.letra = 'A' AND g.anio_escolar_id = " + ANIO_2027);

        JsonNode borrador = get("/api/v1/horarios/2027/borrador?perspectiva=SECCION&seccionId=" + seccion3A, admin()).cuerpo();

        assertThat(borrador.get("carga").get("totalAsignadas").asInt()).isEqualTo(19);
        assertThat(borrador.get("carga").get("totalRequeridas").asInt()).isEqualTo(22);
        assertThat(borrador.get("asignaciones")).hasSize(19);
        assertThat(borrador.get("secciones")).hasSize(10);
        assertThat(borrador.get("docentes")).hasSize(8);
    }

    @Test
    void elGuionDeLaDemoMuestraElCruceDeRosaLaNoDisponibilidadDeCarlosYGuardaConFernando() throws Exception {
        String admin = admin();
        String seccion3A = id("SELECT s.id FROM academico.seccion s JOIN academico.grado g ON g.id = s.grado_id "
                + "WHERE g.numero_nivel = 3 AND s.letra = 'A' AND g.anio_escolar_id = " + ANIO_2027);
        String martes915 = id("SELECT id FROM horarios.bloque_horario WHERE dia_semana = 'MARTES' AND hora_inicio = TIME '09:15'");
        String matematica = id("SELECT id FROM academico.curso WHERE slug = 'matematica'");
        String rosa = id("SELECT id FROM seguridad.docente WHERE codigo_docente = 'DOC-001'");
        String carlos = id("SELECT id FROM seguridad.docente WHERE codigo_docente = 'DOC-003'");
        String fernando = id("SELECT id FROM seguridad.docente WHERE codigo_docente = 'DOC-002'");
        String base = "/api/v1/horarios/2027/borrador";
        String version = etag(get(base + "?seccionId=" + seccion3A, admin));

        // El panel marca a Rosa «tiene clase en 4° B» y a Carlos «No disponible»
        JsonNode docentes = get(base + "/opciones?bloqueId=" + martes915 + "&seccionId=" + seccion3A + "&cursoId=" + matematica,
                admin).cuerpo().get("docentes");
        assertThat(docentes.valueStream().map(d -> d.get("nombre").asString() + " | " + d.get("disponible").asBoolean()
                + " | " + d.get("ocupadoEn").asString("-")).toList())
                .containsExactlyInAnyOrder("Fernando Medina Soto | true | -", "Carlos Ríos Vega | false | -",
                        "Rosa Quispe Mendoza | true | 4° B");

        // Intentar guardar con Rosa: 409 con la causa
        String cuerpoRosa = json.writeValueAsString(Map.of("bloqueId", martes915, "seccionId", seccion3A,
                "cursoId", matematica, "docenteId", rosa));
        Respuesta conRosa = enviar("POST", base + "/asignaciones", cuerpoRosa, admin, version);
        assertThat(conRosa.estado()).isEqualTo(409);
        assertThat(conRosa.cuerpo().get("codigo").asString()).isEqualTo("CONFLICTO_HORARIO");
        assertThat(conRosa.cuerpo().get("detail").asString())
                .isEqualTo("Prof. R. Quispe ya tiene clase en 4° B el martes de 9:15 a 10:00");

        // Carlos no está disponible: 422
        Respuesta conCarlos = enviar("POST", base + "/asignaciones", json.writeValueAsString(Map.of("bloqueId", martes915,
                "seccionId", seccion3A, "cursoId", matematica, "docenteId", carlos)), admin, version);
        assertThat(conCarlos.estado()).isEqualTo(422);
        assertThat(conCarlos.cuerpo().get("codigo").asString()).isEqualTo("DOCENTE_NO_DISPONIBLE");

        // Fernando Medina se guarda
        Respuesta conFernando = enviar("POST", base + "/asignaciones", json.writeValueAsString(Map.of("bloqueId", martes915,
                "seccionId", seccion3A, "cursoId", matematica, "docenteId", fernando)), admin, version);
        assertThat(conFernando.estado()).isEqualTo(201);

        // Con la versión vieja (la segunda pestaña del navegador) el guardado recibe 409 por versión
        Respuesta pestanaVieja = enviar("POST", base + "/asignaciones", json.writeValueAsString(Map.of("bloqueId", martes915,
                "seccionId", seccion3A, "cursoId", matematica, "docenteId", fernando)), admin, version);
        assertThat(pestanaVieja.estado()).isEqualTo(409);
        assertThat(pestanaVieja.cuerpo().get("codigo").asString()).isEqualTo("CONFLICTO_CONCURRENCIA");

        // Desde la perspectiva Docente se ve el mismo borrador, con la clase nueva
        JsonNode porDocente = get(base + "?perspectiva=DOCENTE&docenteId=" + fernando, admin).cuerpo();
        assertThat(porDocente.get("asignaciones").valueStream().map(a -> a.get("seccion").asString()).distinct().toList())
                .containsExactly("3° A");
        assertThat(porDocente.get("asignaciones")).hasSize(6);
        // y la auditoría guarda el alta
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = "
                + "'CREAR_ASIGNACION_HORARIO'", Long.class)).isEqualTo(1);
        List<String> acciones = jdbc.queryForList("SELECT DISTINCT accion FROM auditoria.registro_auditoria", String.class);
        assertThat(acciones).contains("CREAR_ASIGNACION_HORARIO");
    }
}
