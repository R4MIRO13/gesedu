package pe.edu.gesedu.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.apoyo.DatosMatricula;
import pe.edu.gesedu.apoyo.DatosMatricula.AnioNuevo;
import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;
import pe.edu.gesedu.apoyo.DatosMatricula.Solicitud;

/**
 * UC-19, Consultar notas y libreta (RF-30, RF-32 a RF-37): el alumno y el apoderado ven los cursos de la sección con las
 * competencias, el nivel esperado y las notas ya consolidadas, y descargan el informe de progreso o la libreta; el alcance es
 * «yo» o «mis hijos» y los años anteriores se consultan con su libreta.
 * <p>
 * Escenario: año ACTIVO propio con Matemática (C1, C2) y Comunicación (C1) en 3° A (Alva y Bravo, hijos del mismo apoderado) y
 * Matemática en 3° B (Ramos, de otra familia); además un año anterior CERRADO con la libreta FINAL de Alva en Historia.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsultaNotasIT extends BaseIT {

    private Escenario e;
    private AnioNuevo anterior;
    private String rosa;
    private String marta;
    private String luis;
    private String matematica;
    private String comunicacion;
    private String historia;
    private String c1;
    private String c2;
    private String k1;
    private String h1;
    private String asignacionMatematica3A;
    private String asignacionComunicacion3A;
    private String asignacionMatematica3B;
    private String asignacionHistoria;
    private final String[] bimestres = new String[5];
    private final String[] bimestresAnterior = new String[5];
    private String alva;
    private String bravo;
    private String ramos;
    private String apoderadoDeAlva;
    private String apoderadoDeRamos;
    private String tokenAlva;
    private String tokenBravo;
    private String tokenApoderado;
    private String tokenApoderadoRamos;
    private String libretaAlva;

    @BeforeAll
    void escenario() throws Exception {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE estado = 'ACTIVO'");
        DatosMatricula datos = new DatosMatricula(jdbc);
        e = datos.crearEscenario(30);
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        crearBimestres(e.anioId(), e.anio(), bimestres);
        matematica = curso(e.anioId(), "Matemática", "matematica");
        c1 = competencia(matematica, "C1", "Resuelve problemas de cantidad", "Traduce cantidades a expresiones numéricas.", 1);
        c2 = competencia(matematica, "C2", "Resuelve problemas de regularidad", "Establece relaciones entre datos.", 2);
        comunicacion = curso(e.anioId(), "Comunicación", "comunicacion");
        k1 = competencia(comunicacion, "C1", "Se comunica oralmente", "Expresa ideas con claridad.", 1);
        DatosHorario docentes = new DatosHorario(jdbc);
        rosa = docentes.docente("Rosa", "Quispe");
        marta = docentes.docente("Marta", "Ibáñez");
        luis = docentes.docente("Luis", "Castro");
        asignacionMatematica3A = asignacion(rosa, matematica, e.seccion3A());
        asignacionComunicacion3A = asignacion(marta, comunicacion, e.seccion3A());
        asignacionMatematica3B = asignacion(luis, matematica, e.seccion3B());

        Solicitud sAlva = datos.solicitud(e, false);
        datos.matricularYa(e, sAlva, e.seccion3A());
        alva = sAlva.alumnoId();
        apoderadoDeAlva = sAlva.apoderadoId();
        Solicitud sBravo = datos.solicitud(e, false);
        datos.matricularYa(e, sBravo, e.seccion3A());
        bravo = sBravo.alumnoId();
        // Bravo es hermano de Alva: el mismo apoderado (FA-1, apoderado con más de un alumno)
        jdbc.update("UPDATE seguridad.alumno SET apoderado_id = ? WHERE id = ?", apoderadoDeAlva, bravo);
        Solicitud sRamos = datos.solicitud(e, false);
        datos.matricularYa(e, sRamos, e.seccion3B());
        ramos = sRamos.alumnoId();
        apoderadoDeRamos = sRamos.apoderadoId();
        nombre(alva, "García Rojas", "Lucía");
        nombre(bravo, "García Rojas", "Tomás");
        nombre(ramos, "Ramos Silva", "Pedro");

        // Año anterior cerrado: Historia, con la libreta FINAL de Alva (sin matrícula en ese año: sus cursos salen de sus notas)
        anterior = datos.crearAnioSinCobros();
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'CERRADO' WHERE id = ?", anterior.id());
        crearBimestres(anterior.id(), anterior.anio(), bimestresAnterior);
        String grado = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.grado (id, anio_escolar_id, numero_nivel) VALUES (?, ?, 2)", grado, anterior.id());
        String seccionAnterior = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.seccion (id, grado_id, anio_escolar_id, letra, capacidad, vacantes_disponibles) "
                + "VALUES (?, ?, ?, 'A', 30, 30)", seccionAnterior, grado, anterior.id());
        historia = curso(anterior.id(), "Historia", "historia");
        h1 = competencia(historia, "C1", "Construye interpretaciones históricas", "Explica procesos históricos.", 1);
        asignacionHistoria = asignacion(rosa, historia, seccionAnterior);

        tokenAlva = activar(alva, true);
        tokenBravo = activar(bravo, true);
        tokenApoderado = activar(apoderadoDeAlva, false);
        tokenApoderadoRamos = activar(apoderadoDeRamos, false);
    }

    @AfterAll
    void devolverElAnio() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
    }

    @BeforeEach
    void restablecer() {
        jdbc.update("DELETE FROM evaluacion.nota_consolidada WHERE alumno_id IN (?, ?, ?)", alva, bravo, ramos);
        jdbc.update("DELETE FROM evaluacion.libreta WHERE alumno_id IN (?, ?, ?)", alva, bravo, ramos);
        libretaAlva = null;
        // El año anterior de Alva: Historia con los cuatro bimestres y la nota anual, y su libreta FINAL
        String libreta = libreta(alva, anterior.id(), "FINAL");
        for (int b = 1; b <= 4; b++) {
            nota(alva, h1, asignacionHistoria, anterior.id(), bimestresAnterior[b], "BIMESTRAL", new String[] {"", "B", "A", "A", "AD"}[b], libreta);
        }
        nota(alva, h1, asignacionHistoria, anterior.id(), null, "ANUAL", "A", libreta);
    }

    // ---- datos y ayudas ----

    private void crearBimestres(String anioId, int anio, String[] destino) {
        String[][] fechas = {{"1", "02-15", "04-30"}, {"2", "05-01", "07-15"}, {"3", "07-16", "09-30"}, {"4", "10-01", "12-20"}};
        for (String[] b : fechas) {
            int numero = Integer.parseInt(b[0]);
            destino[numero] = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin) "
                    + "VALUES (?, ?, ?, ?::date, ?::date)", destino[numero], anioId, numero, anio + "-" + b[1], anio + "-" + b[2]);
        }
    }

    private String curso(String anioId, String nombre, String slug) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) VALUES (?, ?, ?, ?, 4)",
                id, anioId, nombre, slug);
        return id;
    }

    private String competencia(String cursoId, String codigo, String nombre, String esperado, int orden) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.competencia (id, curso_id, codigo, nombre, nivel_esperado, orden) VALUES (?, ?, ?, ?, ?, ?)",
                id, cursoId, codigo, nombre, esperado, orden);
        return id;
    }

    private String asignacion(String docenteId, String cursoId, String seccionId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO evaluacion.asignacion_docente (id, docente_id, curso_id, seccion_id) VALUES (?, ?, ?, ?)",
                id, docenteId, cursoId, seccionId);
        return id;
    }

    private void nombre(String alumnoId, String apellidos, String nombres) {
        jdbc.update("UPDATE seguridad.usuario SET apellidos = ?, nombres = ? WHERE id = (SELECT usuario_id FROM seguridad.alumno WHERE id = ?)",
                apellidos, nombres, alumnoId);
    }

    /** Activa la cuenta del alumno o del apoderado con la contraseña de las pruebas y devuelve su token. */
    private String activar(String id, boolean alumno) throws Exception {
        String usuario = jdbc.queryForObject("SELECT usuario_id FROM seguridad." + (alumno ? "alumno" : "apoderado") + " WHERE id = ?",
                String.class, id);
        String correo = (alumno ? "alumno-" : "apoderado-") + usuario.substring(0, 8) + "@test.gesedu.pe";
        jdbc.update("UPDATE seguridad.usuario SET correo = ?, hash_contrasena = ?, activo = TRUE WHERE id = ?", correo,
                codificador.encode(CONTRASENA), usuario);
        invalidarCuentas();
        return tokenDe(correo);
    }

    private String libreta(String alumnoId, String anioId, String estado) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO evaluacion.libreta (id, alumno_id, anio_escolar_id, estado, fecha_actualizacion) VALUES (?, ?, ?, ?, now())",
                id, alumnoId, anioId, estado);
        return id;
    }

    private void nota(String alumnoId, String competencia, String asignacion, String anioId, String bimestreId, String tipo, String nivel,
                      String libretaId) {
        jdbc.update("INSERT INTO evaluacion.nota_consolidada (id, alumno_id, competencia_id, asignacion_id, anio_escolar_id, bimestre_id, tipo, "
                + "nivel_logro, calculada_en, libreta_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), ?)", UUID.randomUUID().toString(), alumnoId,
                competencia, asignacion, anioId, bimestreId, tipo, nivel, libretaId);
    }

    /** Lo que deja consolidar los bimestres 1 y 2 en el año actual: notas de Alva en tres competencias y su libreta parcial. */
    private void consolidarDosBimestresDeAlva() {
        libretaAlva = libreta(alva, e.anioId(), "PARCIAL");
        String[][] niveles = {{"AD", "A"}, {"A", "B"}};
        for (int b = 1; b <= 2; b++) {
            nota(alva, c1, asignacionMatematica3A, e.anioId(), bimestres[b], "BIMESTRAL", niveles[b - 1][0], libretaAlva);
            nota(alva, c2, asignacionMatematica3A, e.anioId(), bimestres[b], "BIMESTRAL", niveles[b - 1][1], libretaAlva);
            nota(alva, k1, asignacionComunicacion3A, e.anioId(), bimestres[b], "BIMESTRAL", "A", libretaAlva);
        }
    }

    private List<String> textos(tools.jackson.databind.JsonNode lista, String campo) {
        List<String> resultado = new ArrayList<>();
        lista.forEach(x -> resultado.add(x.get(campo).asString()));
        return resultado;
    }

    private Respuesta cursos(String alumnoId, String token, String parametros) throws Exception {
        return get("/api/v1/alumnos/" + alumnoId + "/cursos" + parametros, token);
    }

    private HttpResponse<byte[]> descargarPdf(String ruta, String token) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Authorization", "Bearer " + token).GET().build();
        return HttpClient.newHttpClient().send(peticion, HttpResponse.BodyHandlers.ofByteArray());
    }

    // ---- FA-1 y alcance: a quién se consulta ----

    @Test
    void elAlumnoSoloSeVeASiMismo() throws Exception {
        Respuesta r = get("/api/v1/apoderados/yo/alumnos", tokenAlva);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().size()).isEqualTo(1);
        assertThat(r.cuerpo().get(0).get("alumnoId").asString()).isEqualTo(alva);
        assertThat(r.cuerpo().get(0).get("nombre").asString()).contains("Lucía");
        assertThat(r.cuerpo().get(0).get("seccion").asString()).isEqualTo("3° A");
        assertThat(r.cuerpo().get(0).get("anio").asInt()).isEqualTo(e.anio());
    }

    @Test
    void fa1UnApoderadoConVariosHijosLosVeTodos() throws Exception {
        Respuesta r = get("/api/v1/apoderados/yo/alumnos", tokenApoderado);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(textos(r.cuerpo(), "alumnoId")).containsExactlyInAnyOrder(alva, bravo);
        assertThat(textos(r.cuerpo(), "seccion")).containsOnly("3° A");
        // Y el de otra familia ve solo al suyo
        assertThat(textos(get("/api/v1/apoderados/yo/alumnos", tokenApoderadoRamos).cuerpo(), "alumnoId")).containsExactly(ramos);
    }

    @Test
    void nadieVeAUnAlumnoQueNoEsSuyoNiSuHijo() throws Exception {
        consolidarDosBimestresDeAlva();

        for (Respuesta r : List.of(cursos(bravo, tokenAlva, ""), cursos(alva, tokenApoderadoRamos, ""), cursos(ramos, tokenApoderado, ""),
                get("/api/v1/alumnos/" + bravo + "/libreta", tokenAlva), get("/api/v1/alumnos/" + alva + "/libretas", tokenBravo),
                get("/api/v1/alumnos/" + alva + "/cursos/" + matematica + "/notas", tokenApoderadoRamos),
                cursos("no-existe", tokenAlva, ""))) {
            assertThat(r.estado()).isEqualTo(403);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
        }
        // El apoderado sí ve a sus dos hijos
        assertThat(cursos(alva, tokenApoderado, "").estado()).isEqualTo(200);
        assertThat(cursos(bravo, tokenApoderado, "").estado()).isEqualTo(200);
    }

    @Test
    void sinElPermisoNotaConsultarPropiaNoSeAccede() throws Exception {
        for (String rol : List.of("ADMINISTRADOR", "DOCENTE")) {
            String token = tokenDe(crearUsuario(rol, true));

            assertThat(cursos(alva, token, "").cuerpo().get("codigo").asString()).isEqualTo("ACCESO_DENEGADO");
            assertThat(get("/api/v1/apoderados/yo/alumnos", token).estado()).isEqualTo(403);
            assertThat(get("/api/v1/alumnos/" + alva + "/libreta", token).estado()).isEqualTo(403);
        }
        assertThat(get("/api/v1/alumnos/" + alva + "/cursos", null).estado()).isEqualTo(401);
    }

    // ---- pasos 2 a 5: cursos, competencias y notas ----

    @Test
    void sinNotasConsolidadasSeVenLosCursosDeLaSeccionConSusCompetencias() throws Exception {
        Respuesta r = cursos(alva, tokenAlva, "");

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("seccion").asString()).isEqualTo("3° A");
        assertThat(r.cuerpo().get("anio").asInt()).isEqualTo(e.anio());
        assertThat(r.cuerpo().get("libreta").isNull()).isTrue();
        assertThat(r.cuerpo().get("bimestre").asInt()).isEqualTo(1);
        assertThat(textos(r.cuerpo().get("bimestres"), "consolidado")).containsOnly("false");
        // Los cursos de la sección 3° A (no Matemática de 3° B), por orden alfabético sin tildes
        assertThat(textos(r.cuerpo().get("cursos"), "curso")).containsExactly("Comunicación", "Matemática");
        assertThat(textos(r.cuerpo().get("cursos"), "docente")).containsExactly("Marta Ibáñez", "Rosa Quispe");
        var mate = r.cuerpo().get("cursos").get(1);
        assertThat(textos(mate.get("competencias"), "codigo")).containsExactly("C1", "C2");
        assertThat(mate.get("competencias").get(0).get("nivelEsperado").asString()).isEqualTo("Traduce cantidades a expresiones numéricas.");
        assertThat(mate.get("competencias").get(0).get("bimestres").size()).isZero();
        assertThat(mate.get("competencias").get(0).get("anual").isNull()).isTrue();
    }

    @Test
    void conBimestresConsolidadosSeVeLaNotaDeCadaCompetenciaPorBimestre() throws Exception {
        consolidarDosBimestresDeAlva();

        Respuesta r = cursos(alva, tokenAlva, "");

        assertThat(r.cuerpo().get("libreta").asString()).isEqualTo("PARCIAL");
        assertThat(r.cuerpo().get("bimestre").asInt()).as("el último consolidado").isEqualTo(2);
        assertThat(textos(r.cuerpo().get("bimestres"), "consolidado")).containsExactly("true", "true", "false", "false");
        var c1Mate = r.cuerpo().get("cursos").get(1).get("competencias").get(0);
        assertThat(c1Mate.get("bimestres").get("1").asString()).isEqualTo("AD");
        assertThat(c1Mate.get("bimestres").get("2").asString()).isEqualTo("A");
        assertThat(c1Mate.get("bimestres").get("3")).isNull();
        assertThat(r.cuerpo().get("cursos").get(1).get("competencias").get(1).get("bimestres").get("2").asString()).isEqualTo("B");
        // Un bimestre pedido se respeta
        assertThat(cursos(alva, tokenAlva, "?bimestre=1").cuerpo().get("bimestre").asInt()).isEqualTo(1);
    }

    @Test
    void lasNotasDeUnAlumnoNoSeMuestranAOtro() throws Exception {
        consolidarDosBimestresDeAlva();

        Respuesta hermano = cursos(bravo, tokenBravo, "");

        assertThat(hermano.cuerpo().get("libreta").isNull()).isTrue();
        assertThat(hermano.cuerpo().get("cursos").get(1).get("competencias").get(0).get("bimestres").size()).isZero();
    }

    @Test
    void elDetalleDeUnCursoTrae_ElNivelEsperadoYLasNotasPorBimestre() throws Exception {
        consolidarDosBimestresDeAlva();
        nota(alva, c1, asignacionMatematica3A, e.anioId(), null, "ANUAL", "A", libretaAlva);

        Respuesta r = get("/api/v1/alumnos/" + alva + "/cursos/" + matematica + "/notas", tokenAlva);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("curso").asString()).isEqualTo("Matemática");
        assertThat(r.cuerpo().get("docente").asString()).isEqualTo("Rosa Quispe");
        assertThat(r.cuerpo().get("competencias").get(0).get("nombre").asString()).isEqualTo("Resuelve problemas de cantidad");
        assertThat(r.cuerpo().get("competencias").get(0).get("anual").asString()).isEqualTo("A");
        assertThat(r.cuerpo().get("competencias").get(0).get("bimestres").get("1").asString()).isEqualTo("AD");
        // Un curso que no es de su sección no se muestra
        assertThat(get("/api/v1/alumnos/" + alva + "/cursos/" + historia + "/notas", tokenAlva).estado()).isEqualTo(404);
    }

    // ---- años anteriores ----

    @Test
    void laLibretaDelAnioAnteriorSeConsultaConSuNotaFinal() throws Exception {
        Respuesta r = cursos(alva, tokenAlva, "?anio=" + anterior.anio());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("anio").asInt()).isEqualTo(anterior.anio());
        assertThat(r.cuerpo().get("libreta").asString()).isEqualTo("FINAL");
        assertThat(r.cuerpo().get("bimestre").asInt()).isEqualTo(4);
        assertThat(textos(r.cuerpo().get("cursos"), "curso")).containsExactly("Historia");
        var historiaC1 = r.cuerpo().get("cursos").get(0).get("competencias").get(0);
        assertThat(historiaC1.get("anual").asString()).isEqualTo("A");
        assertThat(historiaC1.get("bimestres").get("4").asString()).isEqualTo("AD");
        assertThat(r.cuerpo().get("anios").get(0).asInt()).isGreaterThanOrEqualTo(anterior.anio());
    }

    @Test
    void laListaDeLibretasMuestraCadaAnioConSuEstado() throws Exception {
        consolidarDosBimestresDeAlva();

        Respuesta r = get("/api/v1/alumnos/" + alva + "/libretas", tokenAlva);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().size()).isEqualTo(2);
        Map<Integer, String> estados = new HashMap<>();
        r.cuerpo().forEach(l -> estados.put(l.get("anio").asInt(), l.get("estado").asString()));
        assertThat(estados).containsEntry(e.anio(), "PARCIAL").containsEntry(anterior.anio(), "FINAL");
        assertThat(r.cuerpo().get(0).get("anio").asInt()).as("del más reciente al más antiguo").isGreaterThan(r.cuerpo().get(1).get("anio").asInt());
        assertThat(get("/api/v1/alumnos/" + bravo + "/libretas", tokenBravo).cuerpo().size()).isZero();
    }

    // ---- pasos 6 y 7, FA-2: informe y libreta ----

    @Test
    void laLibretaCompletaTraeTodosLosBimestresConsolidadosYLaNotaFinal() throws Exception {
        consolidarDosBimestresDeAlva();
        nota(alva, c1, asignacionMatematica3A, e.anioId(), null, "ANUAL", "A", libretaAlva);

        Respuesta r = get("/api/v1/alumnos/" + alva + "/libreta", tokenAlva);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("alumno").asString()).contains("Lucía");
        assertThat(r.cuerpo().get("seccion").asString()).isEqualTo("3° A");
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("PARCIAL");
        assertThat(r.cuerpo().get("hastaBimestre").isNull()).isTrue();
        assertThat(r.cuerpo().get("bimestres").size()).isEqualTo(2);
        assertThat(r.cuerpo().get("cursos").get(1).get("competencias").get(0).get("anual").asString()).isEqualTo("A");
    }

    @Test
    void elInformeDeProgresoLlegaSoloHastaElBimestrePedido() throws Exception {
        consolidarDosBimestresDeAlva();
        nota(alva, c1, asignacionMatematica3A, e.anioId(), null, "ANUAL", "A", libretaAlva);

        Respuesta r = get("/api/v1/alumnos/" + alva + "/libreta?bimestre=1", tokenAlva);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("hastaBimestre").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get("bimestres").size()).isEqualTo(1);
        var c1Mate = r.cuerpo().get("cursos").get(1).get("competencias").get(0);
        assertThat(c1Mate.get("bimestres").get("1").asString()).isEqualTo("AD");
        assertThat(c1Mate.get("bimestres").get("2")).as("el II no entra al informe del I").isNull();
        assertThat(c1Mate.get("anual").isNull()).isTrue();
    }

    @Test
    void sinNotasConsolidadasNoHayLibreta() throws Exception {
        Respuesta sinLibreta = get("/api/v1/alumnos/" + bravo + "/libreta", tokenBravo);
        Respuesta sinInforme = get("/api/v1/alumnos/" + alva + "/libreta?bimestre=3", tokenAlva);
        consolidarDosBimestresDeAlva();
        Respuesta bimestreSinConsolidar = get("/api/v1/alumnos/" + alva + "/libreta?bimestre=3", tokenAlva);

        assertThat(sinLibreta.estado()).isEqualTo(422);
        assertThat(sinLibreta.cuerpo().get("codigo").asString()).isEqualTo("SIN_NOTAS_CONSOLIDADAS");
        // Por omisión es el año de su matrícula vigente: aunque tenga libreta de un año anterior, el actual aún no tiene notas
        assertThat(sinInforme.estado()).isEqualTo(422);
        assertThat(bimestreSinConsolidar.estado()).isEqualTo(422);
        assertThat(bimestreSinConsolidar.cuerpo().get("detail").asString()).contains("Bimestre III aún no está consolidado");
    }

    @Test
    void laLibretaYElInformeSeDescarganEnPdf() throws Exception {
        consolidarDosBimestresDeAlva();
        nota(alva, c1, asignacionMatematica3A, e.anioId(), null, "ANUAL", "A", libretaAlva);

        HttpResponse<byte[]> libreta = descargarPdf("/api/v1/alumnos/" + alva + "/libreta?formato=pdf", tokenAlva);
        HttpResponse<byte[]> informe = descargarPdf("/api/v1/alumnos/" + alva + "/libreta?formato=pdf&bimestre=2", tokenAlva);
        HttpResponse<byte[]> anteriorPdf = descargarPdf("/api/v1/alumnos/" + alva + "/libreta?formato=pdf&anio=" + anterior.anio(), tokenAlva);

        for (HttpResponse<byte[]> pdf : List.of(libreta, informe, anteriorPdf)) {
            assertThat(pdf.statusCode()).isEqualTo(200);
            assertThat(pdf.headers().firstValue("Content-Type")).hasValue("application/pdf");
            assertThat(new String(pdf.body(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        }
        assertThat(libreta.headers().firstValue("Content-Disposition").orElse("")).contains("libreta-").contains(e.anio() + ".pdf");
        assertThat(informe.headers().firstValue("Content-Disposition").orElse("")).contains("informe-progreso-b2-");
        assertThat(anteriorPdf.headers().firstValue("Content-Disposition").orElse("")).contains(anterior.anio() + ".pdf");
        // El hermano no puede bajar la libreta de Alva
        assertThat(descargarPdf("/api/v1/alumnos/" + alva + "/libreta?formato=pdf", tokenBravo).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/alumnos/" + alva + "/libreta?formato=docx", tokenAlva).estado()).isEqualTo(400);
    }

    @Test
    void elApoderadoDescargaLaLibretaDeCadaHijo() throws Exception {
        consolidarDosBimestresDeAlva();

        assertThat(descargarPdf("/api/v1/alumnos/" + alva + "/libreta?formato=pdf", tokenApoderado).statusCode()).isEqualTo(200);
        assertThat(descargarPdf("/api/v1/alumnos/" + bravo + "/libreta?formato=pdf", tokenApoderado).statusCode()).isEqualTo(422);
        assertThat(luis).isNotNull();
        assertThat(asignacionMatematica3B).isNotNull();
    }
}
