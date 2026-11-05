package pe.edu.gesedu.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.apoyo.DatosMatricula;
import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;
import pe.edu.gesedu.apoyo.RelojPrueba;
import pe.edu.gesedu.apoyo.RelojPruebaConfig;

/**
 * UC-15, Registrar evaluación por competencias (RF-22, RF-31): el docente califica con AD, A, B o C a los alumnos de su
 * sección en las competencias de su curso, en el bimestre vigente; el guardado es atómico, un logro por
 * (alumno, competencia, bimestre), auditado con el valor anterior y el nuevo, y editar un bimestre ya consolidado lo marca
 * como desactualizado (S-68). Reloj fijo en la zona de Lima.
 * <p>
 * Escenario: año ACTIVO propio con cuatro bimestres; Matemática (C1, C2, C3) y Comunicación (C1); Rosa dicta Matemática en
 * 3° A (Alva, Bravo, Castro y un matriculado anulado que no cuenta) y Luis en 3° B (Ramos).
 */
@Import(RelojPruebaConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistroLogrosIT extends BaseIT {

    @Autowired
    private RelojPrueba reloj;

    private Escenario e;
    private String rosa;
    private String luis;
    private String asignacionRosa;
    private String asignacionLuis;
    private String bimestre1;
    private String bimestre2;
    private String c1;
    private String c2;
    private String c3;
    private String comunicacionC1;
    private String alva;
    private String bravo;
    private String castro;
    private String ramos;
    private final Map<String, String> tokens = new HashMap<>();

    @BeforeAll
    void escenario() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE estado = 'ACTIVO'");
        DatosMatricula datos = new DatosMatricula(jdbc);
        e = datos.crearEscenario(30);
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        String[][] bimestres = {{"1", "02-15", "04-30"}, {"2", "05-01", "07-15"}, {"3", "07-16", "09-30"}, {"4", "10-01", "12-20"}};
        for (String[] b : bimestres) {
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin) "
                    + "VALUES (?, ?, ?, ?::date, ?::date)", id, e.anioId(), Integer.parseInt(b[0]),
                    e.anio() + "-" + b[1], e.anio() + "-" + b[2]);
            if (b[0].equals("1")) {
                bimestre1 = id;
            } else if (b[0].equals("2")) {
                bimestre2 = id;
            }
        }
        String matematica = curso("Matemática", "matematica");
        c1 = competencia(matematica, "C1", "Resuelve problemas de cantidad", 1);
        c2 = competencia(matematica, "C2", "Resuelve problemas de regularidad", 2);
        c3 = competencia(matematica, "C3", "Resuelve problemas de forma", 3);
        String comunicacion = curso("Comunicación", "comunicacion");
        comunicacionC1 = competencia(comunicacion, "C1", "Se comunica oralmente", 1);

        DatosHorario docentes = new DatosHorario(jdbc);
        rosa = docentes.docente("Rosa", "Quispe");
        luis = docentes.docente("Luis", "Castro");
        asignacionRosa = asignacion(rosa, matematica, e.seccion3A());
        asignacionLuis = asignacion(luis, matematica, e.seccion3B());

        // Se insertan desordenados a propósito: la rejilla sale en orden alfabético de apellidos
        castro = alumno(e.seccion3A(), "Castro", "Mateo");
        alva = alumno(e.seccion3A(), "Alva", "Lucía");
        bravo = alumno(e.seccion3A(), "Bravo", "Tomás");
        String anulado = alumno(e.seccion3A(), "Zeta", "Anulado");
        jdbc.update("UPDATE matricula.matricula SET estado = 'ANULADA' WHERE alumno_id = ?", anulado);
        ramos = alumno(e.seccion3B(), "Ramos", "Pedro");
    }

    @AfterAll
    void devolverElAnio() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
    }

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM evaluacion.registro_logro WHERE asignacion_id IN (?, ?)", asignacionRosa, asignacionLuis);
        jdbc.update("DELETE FROM evaluacion.consolidacion_bimestral WHERE bimestre_id IN (?, ?)", bimestre1, bimestre2);
        reloj.fijar(LocalDate.of(e.anio(), 3, 10), "10:00");
    }

    // ---- datos y ayudas ----

    private String curso(String nombre, String slug) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) VALUES (?, ?, ?, ?, 4)",
                id, e.anioId(), nombre, slug);
        return id;
    }

    private String competencia(String cursoId, String codigo, String nombre, int orden) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.competencia (id, curso_id, codigo, nombre, nivel_esperado, orden) VALUES (?, ?, ?, ?, ?, ?)",
                id, cursoId, codigo, nombre, "Nivel esperado de " + codigo, orden);
        return id;
    }

    private String asignacion(String docenteId, String cursoId, String seccionId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO evaluacion.asignacion_docente (id, docente_id, curso_id, seccion_id) VALUES (?, ?, ?, ?)",
                id, docenteId, cursoId, seccionId);
        return id;
    }

    private String alumno(String seccionId, String apellidos, String nombres) {
        DatosMatricula datos = new DatosMatricula(jdbc);
        DatosMatricula.Solicitud s = datos.solicitud(e, false);
        datos.matricularYa(e, s, seccionId);
        jdbc.update("UPDATE seguridad.usuario SET apellidos = ?, nombres = ? WHERE id = "
                + "(SELECT usuario_id FROM seguridad.alumno WHERE id = ?)", apellidos, nombres, s.alumnoId());
        return s.alumnoId();
    }

    private String token(String docenteId) throws Exception {
        String cacheado = tokens.get(docenteId);
        if (cacheado != null) {
            return cacheado;
        }
        String usuario = jdbc.queryForObject("SELECT usuario_id FROM seguridad.docente WHERE id = ?", String.class, docenteId);
        String correo = "logros-" + usuario.substring(0, 8) + "@test.gesedu.pe";
        jdbc.update("UPDATE seguridad.usuario SET correo = ?, hash_contrasena = ?, activo = TRUE WHERE id = ?", correo,
                codificador.encode(CONTRASENA), usuario);
        invalidarCuentas();
        String token = tokenDe(correo);
        tokens.put(docenteId, token);
        return token;
    }

    private static Map<String, String> celda(String alumnoId, String competenciaId, String nivel) {
        Map<String, String> celda = new HashMap<>();
        celda.put("alumnoId", alumnoId);
        celda.put("competenciaId", competenciaId);
        celda.put("nivel", nivel);
        return celda;
    }

    @SafeVarargs
    private Respuesta guardar(String asignacion, String token, String parametros, Map<String, String>... celdas) throws Exception {
        return enviar("PUT", "/api/v1/asignaciones-docentes/" + asignacion + "/registros" + parametros,
                json.writeValueAsString(Map.of("logros", List.of(celdas))), token, null);
    }

    private Respuesta rejilla(String asignacion, String token, String parametros) throws Exception {
        return get("/api/v1/asignaciones-docentes/" + asignacion + "/registros" + parametros, token);
    }

    private int filas() {
        return jdbc.queryForObject("SELECT count(*) FROM evaluacion.registro_logro WHERE asignacion_id = ?", Integer.class,
                asignacionRosa);
    }

    private String nivelGuardado(String alumnoId, String competenciaId, String bimestreId) {
        return jdbc.queryForObject("SELECT nivel_logro FROM evaluacion.registro_logro WHERE alumno_id = ? AND competencia_id = ? "
                + "AND bimestre_id = ?", String.class, alumnoId, competenciaId, bimestreId);
    }

    private String estadoConsolidacion(String bimestreId) {
        List<String> estados = jdbc.queryForList("SELECT estado FROM evaluacion.consolidacion_bimestral WHERE bimestre_id = ?",
                String.class, bimestreId);
        return estados.isEmpty() ? null : estados.get(0);
    }

    private void consolidar(String bimestreId, String estado) {
        jdbc.update("INSERT INTO evaluacion.consolidacion_bimestral (id, bimestre_id, fecha_consolidacion, estado) "
                + "VALUES (?, ?, now(), ?)", UUID.randomUUID().toString(), bimestreId, estado);
    }

    private int auditorias() {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'REGISTRAR_LOGROS' "
                + "AND id_entidad_afectada = ?", Integer.class, asignacionRosa);
    }

    // ---- RF-31: mis cursos ----

    @Test
    void misAsignacionesListaLosCursosYSeccionesDelDocente() throws Exception {
        Respuesta r = get("/api/v1/asignaciones-docentes/mias", token(rosa));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().size()).isEqualTo(1);
        assertThat(r.cuerpo().get(0).get("id").asString()).isEqualTo(asignacionRosa);
        assertThat(r.cuerpo().get(0).get("curso").asString()).isEqualTo("Matemática");
        assertThat(r.cuerpo().get(0).get("cursoSlug").asString()).isEqualTo("matematica");
        assertThat(r.cuerpo().get(0).get("bimestreVigente").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get(0).get("seccion").asString()).isEqualTo("3° A");
        assertThat(r.cuerpo().get(0).get("seccionCodigo").asString()).isEqualTo("3A");
        assertThat(r.cuerpo().get(0).get("regla").asString()).isEqualTo("PROMEDIO_PONDERADO");
        assertThat(r.cuerpo().get(0).get("alumnos").asInt()).isEqualTo(3);
        assertThat(r.cuerpo().get(0).get("competencias").asInt()).isEqualTo(3);
        assertThat(get("/api/v1/asignaciones-docentes/mias", token(luis)).cuerpo().get(0).get("seccion").asString())
                .isEqualTo("3° B");
    }

    // ---- pasos 1 y 2: la rejilla ----

    @Test
    void laRejillaMuestraLosAlumnosEnOrdenAlfabeticoYLasCompetenciasDelCurso() throws Exception {
        Respuesta r = rejilla(asignacionRosa, token(rosa), "");

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("curso").asString()).isEqualTo("Matemática");
        assertThat(r.cuerpo().get("seccion").asString()).isEqualTo("3° A");
        assertThat(r.cuerpo().get("bimestre").get("numero").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get("editable").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("mensaje").isNull()).isTrue();
        assertThat(r.cuerpo().get("bimestres").size()).isEqualTo(4);
        List<String> competencias = new ArrayList<>();
        r.cuerpo().get("competencias").forEach(c -> competencias.add(c.get("codigo").asString()));
        assertThat(competencias).containsExactly("C1", "C2", "C3");
        assertThat(r.cuerpo().get("competencias").get(0).get("nivelEsperado").asString()).isEqualTo("Nivel esperado de C1");
        List<String> apellidos = new ArrayList<>();
        r.cuerpo().get("alumnos").forEach(a -> apellidos.add(a.get("apellidos").asString()));
        assertThat(apellidos).containsExactly("Alva", "Bravo", "Castro");
        assertThat(r.cuerpo().get("celdasTotales").asInt()).isEqualTo(9);
        assertThat(r.cuerpo().get("celdasCompletas").asInt()).isZero();
        assertThat(r.cuerpo().get("aviso").asString()).isEqualTo("Hay 3 alumnos con competencias sin calificar");
    }

    // ---- pasos 3 a 7: guardar ----

    @Test
    void guardarAsociaLosLogrosAlCursoLaSeccionYElBimestreVigente() throws Exception {
        Respuesta r = guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "AD"), celda(alva, c2, "A"), celda(bravo, c1, "C"));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("cambios").asInt()).isEqualTo(3);
        assertThat(r.cuerpo().get("celdasCompletas").asInt()).isEqualTo(3);
        assertThat(r.cuerpo().get("alumnos").get(0).get("logros").get(c1).asString()).isEqualTo("AD");
        assertThat(nivelGuardado(alva, c1, bimestre1)).isEqualTo("AD");
        assertThat(nivelGuardado(alva, c2, bimestre1)).isEqualTo("A");
        assertThat(nivelGuardado(bravo, c1, bimestre1)).isEqualTo("C");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluacion.registro_logro WHERE asignacion_id = ? AND bimestre_id = ?",
                Integer.class, asignacionRosa, bimestre1)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT fecha_registro IS NOT NULL FROM evaluacion.registro_logro LIMIT 1", Boolean.class))
                .isTrue();
        // La consulta posterior muestra lo guardado
        Respuesta consulta = rejilla(asignacionRosa, token(rosa), "");
        assertThat(consulta.cuerpo().get("celdasCompletas").asInt()).isEqualTo(3);
        assertThat(consulta.cuerpo().get("alumnos").get(1).get("logros").get(c1).asString()).isEqualTo("C");
    }

    @Test
    void fa2RegistroIncompletoSeGuardaConElAvisoYLosPendientes() throws Exception {
        // Alva completa (C1 a C3) y Bravo con C1 y C2: faltan Bravo (C3) y Castro (todo)
        Respuesta r = guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "A"), celda(alva, c2, "A"), celda(alva, c3, "B"),
                celda(bravo, c1, "AD"), celda(bravo, c2, "B"));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("aviso").asString()).isEqualTo("Hay 2 alumnos con competencias sin calificar");
        assertThat(r.cuerpo().get("celdasTotales").asInt()).isEqualTo(9);
        assertThat(r.cuerpo().get("celdasCompletas").asInt()).isEqualTo(5);
        assertThat(r.cuerpo().get("incompletos").size()).isEqualTo(2);
        assertThat(r.cuerpo().get("incompletos").get(0).get("alumno").asString()).startsWith("Bravo");
        assertThat(r.cuerpo().get("incompletos").get(0).get("competencias").get(0).asString()).isEqualTo("C3");
        assertThat(r.cuerpo().get("incompletos").get(1).get("competencias").size()).isEqualTo(3);
        assertThat(filas()).as("lo registrado se guardó").isEqualTo(5);
    }

    @Test
    void conTodasLasCeldasNoHayAviso() throws Exception {
        List<Map<String, String>> celdas = new ArrayList<>();
        for (String alumno : List.of(alva, bravo, castro)) {
            for (String competencia : List.of(c1, c2, c3)) {
                celdas.add(celda(alumno, competencia, "A"));
            }
        }
        Respuesta r = enviar("PUT", "/api/v1/asignaciones-docentes/" + asignacionRosa + "/registros",
                json.writeValueAsString(Map.of("logros", celdas)), token(rosa), null);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("aviso").isNull()).isTrue();
        assertThat(r.cuerpo().get("incompletos").size()).isZero();
        assertThat(r.cuerpo().get("celdasCompletas").asInt()).isEqualTo(9);
    }

    @Test
    void guardarDeNuevoLaMismaCeldaLaActualizaSinDuplicarla() throws Exception {
        guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "B"));
        String id = jdbc.queryForObject("SELECT id FROM evaluacion.registro_logro WHERE alumno_id = ?", String.class, alva);
        long version = jdbc.queryForObject("SELECT version FROM evaluacion.registro_logro WHERE id = ?", Long.class, id);

        Respuesta r = guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "AD"));

        assertThat(r.cuerpo().get("cambios").asInt()).isEqualTo(1);
        assertThat(filas()).isEqualTo(1);
        assertThat(nivelGuardado(alva, c1, bimestre1)).isEqualTo("AD");
        assertThat(jdbc.queryForObject("SELECT id FROM evaluacion.registro_logro WHERE alumno_id = ?", String.class, alva))
                .isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT version FROM evaluacion.registro_logro WHERE id = ?", Long.class, id))
                .isGreaterThan(version);
    }

    @Test
    void guardarElMismoNivelNoCambiaNadaNiSeAudita() throws Exception {
        guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "A"));
        int antes = auditorias();
        long version = jdbc.queryForObject("SELECT version FROM evaluacion.registro_logro WHERE alumno_id = ?", Long.class, alva);

        Respuesta r = guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "A"));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("cambios").asInt()).isZero();
        assertThat(auditorias()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT version FROM evaluacion.registro_logro WHERE alumno_id = ?", Long.class, alva))
                .isEqualTo(version);
    }

    // ---- FA-1: logro inválido ----

    @Test
    void fa1UnNivelFueraDeLaEscalaSeRechazaYNoSeGuardaNada() throws Exception {
        for (String invalido : new String[] {"E", "ad", "a", "AD ", "5", "", null}) {
            Respuesta r = guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "AD"), celda(bravo, c2, invalido));

            assertThat(r.estado()).as("nivel «%s»", invalido).isEqualTo(422);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("NIVEL_LOGRO_INVALIDO");
            assertThat(r.cuerpo().get("errores").get(0).get("campo").asString()).isEqualTo("logros." + bravo + "." + c2);
            assertThat(r.cuerpo().get("errores").get(0).get("mensaje").asString()).contains("AD, A, B o C");
            assertThat(filas()).as("el lote es atómico: la celda válida tampoco se guardó").isZero();
        }
    }

    @Test
    void fa1ReintentarConElNivelCorregidoGuarda() throws Exception {
        assertThat(guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "Z")).estado()).isEqualTo(422);

        Respuesta r = guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "B"));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(nivelGuardado(alva, c1, bimestre1)).isEqualTo("B");
    }

    @Test
    void celdasQueNoCorrespondenSeRechazanConLaLista() throws Exception {
        // Alumno de otra sección, competencia de otro curso y celda repetida
        Respuesta ajeno = guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "A"), celda(ramos, c1, "A"));
        Respuesta otroCurso = guardar(asignacionRosa, token(rosa), "", celda(alva, comunicacionC1, "A"));
        Respuesta repetida = guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "A"), celda(alva, c1, "B"));
        Respuesta inexistente = guardar(asignacionRosa, token(rosa), "", celda("no-existe", c1, "A"));

        for (Respuesta r : List.of(ajeno, otroCurso, repetida, inexistente)) {
            assertThat(r.estado()).isEqualTo(400);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
            assertThat(r.cuerpo().get("errores").size()).isEqualTo(1);
        }
        assertThat(ajeno.cuerpo().get("errores").get(0).get("campo").asString()).isEqualTo("logros." + ramos + "." + c1);
        assertThat(filas()).isZero();
    }

    @Test
    void unCuerpoSinLogrosEsInvalido() throws Exception {
        Respuesta vacio = enviar("PUT", "/api/v1/asignaciones-docentes/" + asignacionRosa + "/registros",
                "{\"logros\":[]}", token(rosa), null);
        Respuesta sinCampo = enviar("PUT", "/api/v1/asignaciones-docentes/" + asignacionRosa + "/registros", "{}",
                token(rosa), null);

        assertThat(vacio.estado()).isEqualTo(400);
        assertThat(sinCampo.estado()).isEqualTo(400);
    }

    // ---- solo el bimestre vigente ----

    @Test
    void soloSeRegistraEnElBimestreVigente() throws Exception {
        // Hoy es 10/03: el bimestre I está vigente; el II no
        Respuesta otro = guardar(asignacionRosa, token(rosa), "?bimestre=2", celda(alva, c1, "A"));
        assertThat(otro.estado()).isEqualTo(422);
        assertThat(otro.cuerpo().get("codigo").asString()).isEqualTo("BIMESTRE_NO_VIGENTE");
        assertThat(filas()).isZero();

        // Pedir expresamente el vigente funciona
        assertThat(guardar(asignacionRosa, token(rosa), "?bimestre=1", celda(alva, c1, "A")).estado()).isEqualTo(200);
    }

    @Test
    void fueraDeTodoBimestreNoSeRegistra() throws Exception {
        reloj.fijar(LocalDate.of(e.anio(), 1, 20), "10:00");

        Respuesta r = guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "A"));

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("BIMESTRE_NO_VIGENTE");
        assertThat(filas()).isZero();
        // La consulta sigue disponible, sin edición: el bimestre I todavía no empezó
        Respuesta consulta = rejilla(asignacionRosa, token(rosa), "");
        assertThat(consulta.estado()).isEqualTo(200);
        assertThat(consulta.cuerpo().get("editable").asBoolean()).isFalse();
        assertThat(consulta.cuerpo().get("mensaje").asString()).contains("aún no empieza");
    }

    @Test
    void unBimestreTerminadoSePuedeConsultarPeroNoEditar() throws Exception {
        guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "AD"));
        reloj.fijar(LocalDate.of(e.anio(), 5, 20), "10:00");

        Respuesta consulta = rejilla(asignacionRosa, token(rosa), "?bimestre=1");
        Respuesta edicion = guardar(asignacionRosa, token(rosa), "?bimestre=1", celda(alva, c1, "C"));

        assertThat(consulta.cuerpo().get("editable").asBoolean()).isFalse();
        assertThat(consulta.cuerpo().get("mensaje").asString()).isEqualTo("El Bimestre I ya terminó: solo puedes consultar sus logros.");
        assertThat(consulta.cuerpo().get("alumnos").get(0).get("logros").get(c1).asString()).isEqualTo("AD");
        assertThat(edicion.estado()).isEqualTo(422);
        assertThat(edicion.cuerpo().get("codigo").asString()).isEqualTo("BIMESTRE_NO_VIGENTE");
        assertThat(nivelGuardado(alva, c1, bimestre1)).isEqualTo("AD");
        // Y el bimestre II, ya vigente, es el que se propone por omisión
        assertThat(rejilla(asignacionRosa, token(rosa), "").cuerpo().get("bimestre").get("numero").asInt()).isEqualTo(2);
    }

    @Test
    void elUltimoDiaDelBimestreAunSeRegistra() throws Exception {
        reloj.fijar(LocalDate.of(e.anio(), 4, 30), "23:59");
        assertThat(guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "A")).estado()).isEqualTo(200);

        reloj.fijar(LocalDate.of(e.anio(), 5, 1), "00:00");
        assertThat(guardar(asignacionRosa, token(rosa), "?bimestre=1", celda(alva, c1, "B")).estado()).isEqualTo(422);
        assertThat(guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "B")).estado()).isEqualTo(200);
        assertThat(nivelGuardado(alva, c1, bimestre2)).isEqualTo("B");
    }

    @Test
    void unBimestreInexistenteEs404() throws Exception {
        assertThat(rejilla(asignacionRosa, token(rosa), "?bimestre=9").estado()).isEqualTo(404);
    }

    // ---- S-68: editar tras consolidar ----

    @Test
    void editarUnBimestreConsolidadoLoMarcaDesactualizadoYAvisa() throws Exception {
        guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "A"));
        consolidar(bimestre1, "COMPLETADA");

        Respuesta r = guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "AD"));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("consolidacion").asString()).isEqualTo("DESACTUALIZADA");
        assertThat(r.cuerpo().get("avisoConsolidacion").asString()).contains("Bimestre I").contains("volver a consolidarlo");
        assertThat(estadoConsolidacion(bimestre1)).isEqualTo("DESACTUALIZADA");
        // La consulta lo sigue mostrando
        assertThat(rejilla(asignacionRosa, token(rosa), "").cuerpo().get("consolidacion").asString()).isEqualTo("DESACTUALIZADA");
        // Y queda auditado: el cambio del logro y el de la consolidación
        List<Map<String, Object>> cambios = jdbc.queryForList("SELECT c.campo, c.valor_anterior, c.valor_nuevo FROM "
                + "auditoria.cambio_auditoria c JOIN auditoria.registro_auditoria r ON r.id = c.registro_auditoria_id "
                + "WHERE r.accion = 'REGISTRAR_LOGROS' AND r.id_entidad_afectada = ? ORDER BY c.campo", asignacionRosa);
        assertThat(cambios).anySatisfy(c -> {
            assertThat((String) c.get("campo")).startsWith("B1 ").endsWith(" C1");
            assertThat(c.get("valor_anterior")).isEqualTo("A");
            assertThat(c.get("valor_nuevo")).isEqualTo("AD");
        });
        assertThat(cambios).anySatisfy(c -> {
            assertThat(c.get("campo")).isEqualTo("consolidacion.bimestre1");
            assertThat(c.get("valor_anterior")).isEqualTo("COMPLETADA");
            assertThat(c.get("valor_nuevo")).isEqualTo("DESACTUALIZADA");
        });
    }

    @Test
    void unGuardadoSinCambiosNoDesactualizaLaConsolidacion() throws Exception {
        guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "A"));
        consolidar(bimestre1, "COMPLETADA");

        Respuesta r = guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "A"));

        assertThat(r.cuerpo().get("cambios").asInt()).isZero();
        assertThat(r.cuerpo().get("consolidacion").asString()).isEqualTo("COMPLETADA");
        assertThat(r.cuerpo().get("avisoConsolidacion").isNull()).isTrue();
        assertThat(estadoConsolidacion(bimestre1)).isEqualTo("COMPLETADA");
    }

    @Test
    void calificarUnaCeldaNuevaTrasConsolidarTambienDesactualiza() throws Exception {
        guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "A"));
        consolidar(bimestre1, "COMPLETADA");

        guardar(asignacionRosa, token(rosa), "", celda(bravo, c1, "B"));

        assertThat(estadoConsolidacion(bimestre1)).isEqualTo("DESACTUALIZADA");
    }

    @Test
    void unBimestreTerminadoYConsolidadoNoSeEditaNiSeDesactualiza() throws Exception {
        guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "A"));
        consolidar(bimestre1, "COMPLETADA");
        reloj.fijar(LocalDate.of(e.anio(), 5, 20), "10:00");

        Respuesta r = guardar(asignacionRosa, token(rosa), "?bimestre=1", celda(alva, c1, "AD"));

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("BIMESTRE_NO_VIGENTE");
        assertThat(nivelGuardado(alva, c1, bimestre1)).isEqualTo("A");
        assertThat(estadoConsolidacion(bimestre1)).isEqualTo("COMPLETADA");
    }

    // ---- auditoría ----

    @Test
    void cadaCambioQuedaAuditadoConElValorAnteriorYElNuevo() throws Exception {
        String usuarioRosa = jdbc.queryForObject("SELECT usuario_id FROM seguridad.docente WHERE id = ?", String.class, rosa);
        List<String> previos = jdbc.queryForList("SELECT id FROM auditoria.registro_auditoria WHERE accion = 'REGISTRAR_LOGROS' "
                + "AND id_entidad_afectada = ?", String.class, asignacionRosa);
        guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "B"), celda(bravo, c2, "A"));
        guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "AD"));

        // La auditoría es de solo inserción y el reloj de la prueba está fijo: se identifican los dos registros nuevos
        List<Map<String, Object>> nuevos = jdbc.queryForList("SELECT * FROM auditoria.registro_auditoria WHERE accion = "
                + "'REGISTRAR_LOGROS' AND id_entidad_afectada = ?", asignacionRosa).stream()
                .filter(r -> !previos.contains((String) r.get("id"))).toList();
        assertThat(nuevos).hasSize(2);
        for (Map<String, Object> registro : nuevos) {
            assertThat(registro.get("modulo")).isEqualTo("EVALUACION");
            assertThat(registro.get("tipo_entidad")).isEqualTo("RegistroLogro");
            assertThat(registro.get("usuario_id")).isEqualTo(usuarioRosa);
        }
        List<List<Map<String, Object>>> cambios = nuevos.stream().map(r -> jdbc.queryForList(
                "SELECT campo, valor_anterior, valor_nuevo FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ?",
                r.get("id"))).sorted(java.util.Comparator.comparingInt(List::size)).toList();
        // Un guardado cambió dos celdas (alta: sin valor anterior) y el otro, una (B → AD)
        assertThat(cambios.get(0)).hasSize(1);
        assertThat(cambios.get(0).get(0).get("valor_anterior")).isEqualTo("B");
        assertThat(cambios.get(0).get(0).get("valor_nuevo")).isEqualTo("AD");
        assertThat(cambios.get(1)).hasSize(2);
        assertThat(cambios.get(1)).allSatisfy(c -> {
            assertThat(c.get("valor_anterior")).isNull();
            assertThat((String) c.get("campo")).startsWith("B1 ");
        });
    }

    @Test
    void unRechazoNoDejaAuditoria() throws Exception {
        int antes = auditorias();

        guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "Z"));
        guardar(asignacionRosa, token(rosa), "?bimestre=3", celda(alva, c1, "A"));

        assertThat(auditorias()).isEqualTo(antes);
    }

    // ---- alcance y permisos ----

    @Test
    void soloElDocenteDeLaAsignacionVeYRegistra() throws Exception {
        // Luis dicta Matemática en 3° B, no en 3° A
        Respuesta ver = rejilla(asignacionRosa, token(luis), "");
        Respuesta guardar = guardar(asignacionRosa, token(luis), "", celda(alva, c1, "A"));
        Respuesta inexistente = rejilla("no-existe", token(rosa), "");
        Respuesta guardarInexistente = guardar("no-existe", token(rosa), "", celda(alva, c1, "A"));

        for (Respuesta r : List.of(ver, guardar, inexistente, guardarInexistente)) {
            assertThat(r.estado()).isEqualTo(403);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
        }
        assertThat(filas()).isZero();
        // Cada uno ve la suya
        assertThat(rejilla(asignacionLuis, token(luis), "").estado()).isEqualTo(200);
        assertThat(rejilla(asignacionLuis, token(luis), "").cuerpo().get("alumnos").get(0).get("apellidos").asString())
                .isEqualTo("Ramos");
    }

    @Test
    void sinElPermisoNotaRegistrarNoSeAccede() throws Exception {
        String alumno = tokenDe(crearUsuario("ALUMNO", true));
        String apoderado = tokenDe(crearUsuario("APODERADO", true));
        String admin = tokenDe(crearUsuario("ADMINISTRADOR", true));

        for (String token : List.of(alumno, apoderado, admin)) {
            assertThat(get("/api/v1/asignaciones-docentes/mias", token).estado()).isEqualTo(403);
            assertThat(rejilla(asignacionRosa, token, "").cuerpo().get("codigo").asString()).isEqualTo("ACCESO_DENEGADO");
            assertThat(guardar(asignacionRosa, token, "", celda(alva, c1, "A")).estado()).isEqualTo(403);
        }
        assertThat(get("/api/v1/asignaciones-docentes/mias", null).estado()).isEqualTo(401);
        assertThat(filas()).isZero();
    }

    @Test
    void unDocenteSinAsignacionesNoVeNada() throws Exception {
        String sinAsignaciones = new DatosHorario(jdbc).docente("Marta", "Ibáñez");

        Respuesta r = get("/api/v1/asignaciones-docentes/mias", token(sinAsignaciones));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().size()).isZero();
    }

    // ---- concurrencia ----

    @Test
    void ocho_guardadosSimultaneosDeLasMismasCeldasDejanUnSoloRegistroPorCelda() throws Exception {
        String token = token(rosa);
        String[] niveles = {"AD", "A", "B", "C"};
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Respuesta>> tareas = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String nivel = niveles[i % niveles.length];
                tareas.add(() -> guardar(asignacionRosa, token, "", celda(alva, c1, nivel), celda(bravo, c1, nivel),
                        celda(castro, c1, nivel)));
            }
            for (Future<Respuesta> f : hilos.invokeAll(tareas)) {
                assertThat(f.get().estado()).isEqualTo(200);
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(filas()).as("una sola fila por (alumno, competencia, bimestre)").isEqualTo(3);
        // Las tres celdas quedan con el mismo nivel: el último guardado completo gana, nunca una mezcla
        List<String> guardados = jdbc.queryForList("SELECT DISTINCT nivel_logro FROM evaluacion.registro_logro WHERE asignacion_id = ?",
                String.class, asignacionRosa);
        assertThat(guardados).hasSize(1);
    }

    @Test
    void guardarYConsolidarSimultaneosNoPierdenLaMarca() throws Exception {
        guardar(asignacionRosa, token(rosa), "", celda(alva, c1, "A"));
        consolidar(bimestre1, "COMPLETADA");

        ExecutorService hilos = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Respuesta>> tareas = new ArrayList<>();
            String[] niveles = {"AD", "B", "C", "AD"};
            for (String nivel : niveles) {
                tareas.add(() -> guardar(asignacionRosa, token(rosa), "", celda(alva, c1, nivel)));
            }
            for (Future<Respuesta> f : hilos.invokeAll(tareas)) {
                assertThat(f.get().estado()).isEqualTo(200);
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(estadoConsolidacion(bimestre1)).isEqualTo("DESACTUALIZADA");
        assertThat(filas()).isEqualTo(1);
    }
}
