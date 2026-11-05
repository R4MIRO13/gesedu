package pe.edu.gesedu.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
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

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.apoyo.DatosMatricula;
import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;

/**
 * UC-17, Cálculo de notas finales (RF-28, RF-29, RF-38): el docente calcula la nota anual de cada competencia aplicando la
 * regla de su curso a los logros de los bimestres, con los pesos que ajustó. El cálculo es idempotente, auditado y solo lo
 * hace el docente de la asignación.
 * <p>
 * Escenario: año ACTIVO propio con cuatro bimestres; Matemática (C1, C2) que Rosa dicta en 3° A (Alva, Bravo, Castro y un
 * matriculado anulado) y Luis en 3° B (Ramos, sin logros).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotasFinalesIT extends BaseIT {

    private Escenario e;
    private String rosa;
    private String luis;
    private String asignacionRosa;
    private String asignacionLuis;
    private final String[] bimestres = new String[5];
    private String c1;
    private String c2;
    private String alva;
    private String bravo;
    private String castro;
    private String anulado;
    private final Map<String, String> tokens = new HashMap<>();

    @BeforeAll
    void escenario() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE estado = 'ACTIVO'");
        e = new DatosMatricula(jdbc).crearEscenario(30);
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        String[][] fechas = {{"1", "02-15", "04-30"}, {"2", "05-01", "07-15"}, {"3", "07-16", "09-30"}, {"4", "10-01", "12-20"}};
        for (String[] b : fechas) {
            int numero = Integer.parseInt(b[0]);
            bimestres[numero] = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin) "
                    + "VALUES (?, ?, ?, ?::date, ?::date)", bimestres[numero], e.anioId(), numero, e.anio() + "-" + b[1],
                    e.anio() + "-" + b[2]);
        }
        String curso = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) VALUES (?, ?, 'Matemática', "
                + "'matematica', 4)", curso, e.anioId());
        c1 = competencia(curso, "C1", 1);
        c2 = competencia(curso, "C2", 2);
        DatosHorario docentes = new DatosHorario(jdbc);
        rosa = docentes.docente("Rosa", "Quispe");
        luis = docentes.docente("Luis", "Castro");
        asignacionRosa = asignacion(rosa, curso, e.seccion3A());
        asignacionLuis = asignacion(luis, curso, e.seccion3B());
        alva = alumno(e.seccion3A(), "Alva", "Lucía");
        bravo = alumno(e.seccion3A(), "Bravo", "Tomás");
        castro = alumno(e.seccion3A(), "Castro", "Mateo");
        anulado = alumno(e.seccion3A(), "Zeta", "Anulado");
        jdbc.update("UPDATE matricula.matricula SET estado = 'ANULADA' WHERE alumno_id = ?", anulado);
    }

    @AfterAll
    void devolverElAnio() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
    }

    @BeforeEach
    void restablecer() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        jdbc.update("DELETE FROM evaluacion.nota_consolidada WHERE asignacion_id IN (?, ?)", asignacionRosa, asignacionLuis);
        jdbc.update("DELETE FROM evaluacion.registro_logro WHERE asignacion_id IN (?, ?)", asignacionRosa, asignacionLuis);
        jdbc.update("DELETE FROM evaluacion.peso_bimestre WHERE asignacion_id IN (?, ?)", asignacionRosa, asignacionLuis);
        jdbc.update("UPDATE evaluacion.asignacion_docente SET regla_consolidacion = 'PROMEDIO_PONDERADO' WHERE id IN (?, ?)",
                asignacionRosa, asignacionLuis);
    }

    // ---- datos y ayudas ----

    private String competencia(String cursoId, String codigo, int orden) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.competencia (id, curso_id, codigo, nombre, nivel_esperado, orden) VALUES (?, ?, ?, ?, 'A', ?)",
                id, cursoId, codigo, "Competencia " + codigo, orden);
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

    /** Un logro del bimestre indicado, sin pasar por la API (UC-15 ya tiene sus pruebas). */
    private void logro(String alumno, String competencia, int bimestre, String nivel) {
        jdbc.update("INSERT INTO evaluacion.registro_logro (id, asignacion_id, alumno_id, competencia_id, bimestre_id, nivel_logro, "
                + "fecha_registro) VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), asignacionRosa, alumno, competencia,
                bimestres[bimestre], nivel, Timestamp.from(Instant.parse("2027-03-10T15:00:00Z")));
    }

    private void logros(String alumno, String competencia, String... niveles) {
        for (int i = 0; i < niveles.length; i++) {
            if (niveles[i] != null) {
                logro(alumno, competencia, i + 1, niveles[i]);
            }
        }
    }

    private void cambiarLogro(String alumno, String competencia, int bimestre, String nivel) {
        jdbc.update("UPDATE evaluacion.registro_logro SET nivel_logro = ? WHERE alumno_id = ? AND competencia_id = ? AND bimestre_id = ?",
                nivel, alumno, competencia, bimestres[bimestre]);
    }

    private String token(String docenteId) throws Exception {
        String cacheado = tokens.get(docenteId);
        if (cacheado != null) {
            return cacheado;
        }
        String usuario = jdbc.queryForObject("SELECT usuario_id FROM seguridad.docente WHERE id = ?", String.class, docenteId);
        String correo = "finales-" + usuario.substring(0, 8) + "@test.gesedu.pe";
        jdbc.update("UPDATE seguridad.usuario SET correo = ?, hash_contrasena = ?, activo = TRUE WHERE id = ?", correo,
                codificador.encode(CONTRASENA), usuario);
        invalidarCuentas();
        String token = tokenDe(correo);
        tokens.put(docenteId, token);
        return token;
    }

    private Respuesta consultar(String asignacion, String token) throws Exception {
        return get("/api/v1/asignaciones-docentes/" + asignacion + "/notas-finales", token);
    }

    private Respuesta calcular(String asignacion, String token) throws Exception {
        return post("/api/v1/asignaciones-docentes/" + asignacion + "/notas-finales/calculo", "", token);
    }

    private String anual(String alumno, String competencia) {
        List<String> niveles = jdbc.queryForList("SELECT nivel_logro FROM evaluacion.nota_consolidada WHERE alumno_id = ? AND "
                + "competencia_id = ? AND tipo = 'ANUAL'", String.class, alumno, competencia);
        assertThat(niveles.size()).as("a lo sumo una nota anual por alumno y competencia").isLessThanOrEqualTo(1);
        return niveles.isEmpty() ? null : niveles.get(0);
    }

    private int notasGuardadas() {
        return jdbc.queryForObject("SELECT count(*) FROM evaluacion.nota_consolidada WHERE asignacion_id = ?", Integer.class,
                asignacionRosa);
    }

    private int auditorias() {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'CALCULAR_NOTAS_FINALES' "
                + "AND id_entidad_afectada = ?", Integer.class, asignacionRosa);
    }

    private JsonNodeAyuda celda(Respuesta r, String alumno, String competencia) {
        for (var fila : r.cuerpo().get("alumnos")) {
            if (fila.get("alumnoId").asString().equals(alumno)) {
                return new JsonNodeAyuda(fila.get("competencias").get(competencia));
            }
        }
        throw new AssertionError("El alumno no está en la tabla");
    }

    private record JsonNodeAyuda(tools.jackson.databind.JsonNode nodo) {
        String anual() {
            return nodo.get("anual").isNull() ? null : nodo.get("anual").asString();
        }

        String bimestre(int numero) {
            var n = nodo.get("bimestres").get(String.valueOf(numero));
            return n == null ? null : n.asString();
        }
    }

    // ---- paso 2: la tabla ----

    @Test
    void sinLogrosLaTablaMuestraLasCeldasVacias() throws Exception {
        Respuesta r = consultar(asignacionRosa, token(rosa));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("curso").asString()).isEqualTo("Matemática");
        assertThat(r.cuerpo().get("seccion").asString()).isEqualTo("3° A");
        assertThat(r.cuerpo().get("regla").asString()).isEqualTo("PROMEDIO_PONDERADO");
        assertThat(r.cuerpo().get("bimestres").size()).isEqualTo(4);
        assertThat(r.cuerpo().get("competencias").size()).isEqualTo(2);
        assertThat(r.cuerpo().get("calculadas").asBoolean()).isFalse();
        assertThat(r.cuerpo().get("desactualizada").asBoolean()).isFalse();
        assertThat(r.cuerpo().get("editable").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("celdasSinNota").asInt()).isEqualTo(6);
        assertThat(r.cuerpo().get("calculo").isNull()).isTrue();
        List<String> apellidos = new ArrayList<>();
        r.cuerpo().get("alumnos").forEach(a -> apellidos.add(a.get("apellidos").asString()));
        assertThat(apellidos).containsExactly("Alva", "Bravo", "Castro");
    }

    @Test
    void laTablaMuestraElLogroDeCadaBimestreYLaNotaAnualCalculada() throws Exception {
        logros(alva, c1, "AD", "A", "A", "B");
        logros(bravo, c1, "C", "B");

        Respuesta antes = consultar(asignacionRosa, token(rosa));
        assertThat(celda(antes, alva, c1).bimestre(1)).isEqualTo("AD");
        assertThat(celda(antes, alva, c1).bimestre(4)).isEqualTo("B");
        assertThat(celda(antes, bravo, c1).bimestre(3)).isNull();
        assertThat(celda(antes, alva, c1).anual()).isNull();
        assertThat(antes.cuerpo().get("celdasSinNota").asInt()).isEqualTo(4);

        calcular(asignacionRosa, token(rosa));
        Respuesta despues = consultar(asignacionRosa, token(rosa));
        assertThat(celda(despues, alva, c1).anual()).isEqualTo("A");
        assertThat(celda(despues, alva, c1).bimestre(2)).isEqualTo("A");
        assertThat(despues.cuerpo().get("calculadas").asBoolean()).isTrue();
        assertThat(despues.cuerpo().get("desactualizada").asBoolean()).isFalse();
    }

    // ---- pasos 3 y 4: el cálculo ----

    @Test
    void elPromedioPonderadoCalculaLaNotaAnualDeCadaCompetencia() throws Exception {
        logros(alva, c1, "AD", "A", "A", "B");   // (4+3+3+2)/4 = 3 -> A
        logros(alva, c2, "C", "C", "B", "B");    // (1+1+2+2)/4 = 1,5 -> B
        logros(bravo, c1, "AD", "AD", "AD", "AD");
        logros(castro, c1, "B", "C");            // 1,5 -> B

        Respuesta r = calcular(asignacionRosa, token(rosa));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("calculo").get("creadas").asInt()).isEqualTo(4);
        assertThat(r.cuerpo().get("calculo").get("actualizadas").asInt()).isZero();
        assertThat(r.cuerpo().get("calculo").get("sinNota").asInt()).isEqualTo(2);
        assertThat(anual(alva, c1)).isEqualTo("A");
        assertThat(anual(alva, c2)).isEqualTo("B");
        assertThat(anual(bravo, c1)).isEqualTo("AD");
        assertThat(anual(castro, c1)).isEqualTo("B");
        assertThat(anual(bravo, c2)).as("sin logros no hay nota").isNull();
        assertThat(celda(r, alva, c1).anual()).isEqualTo("A");
        // La nota guarda el año, la asignación y no lleva bimestre ni libreta todavía
        var fila = jdbc.queryForMap("SELECT * FROM evaluacion.nota_consolidada WHERE alumno_id = ? AND competencia_id = ?", alva, c1);
        assertThat(fila.get("anio_escolar_id")).isEqualTo(e.anioId());
        assertThat(fila.get("asignacion_id")).isEqualTo(asignacionRosa);
        assertThat(fila.get("bimestre_id")).isNull();
        assertThat(fila.get("libreta_id")).isNull();
        assertThat(fila.get("tipo")).isEqualTo("ANUAL");
    }

    @Test
    void cadaReglaDaSuResultadoConLosMismosLogros() throws Exception {
        // AD, AD, A, B: promedio 3,25 -> A ; moda AD ; último B
        logros(alva, c1, "AD", "AD", "A", "B");
        String[][] casos = {{"PROMEDIO_PONDERADO", "A"}, {"MODA", "AD"}, {"ULTIMO_REGISTRO", "B"}};
        for (String[] caso : casos) {
            jdbc.update("UPDATE evaluacion.asignacion_docente SET regla_consolidacion = ? WHERE id = ?", caso[0], asignacionRosa);

            Respuesta r = calcular(asignacionRosa, token(rosa));

            assertThat(r.estado()).isEqualTo(200);
            assertThat(r.cuerpo().get("regla").asString()).isEqualTo(caso[0]);
            assertThat(anual(alva, c1)).as(caso[0]).isEqualTo(caso[1]);
            assertThat(notasGuardadas()).as("se reemplaza, no se duplica").isEqualTo(1);
        }
    }

    @Test
    void elEmpateDeLaModaLoGanaElBimestreMasReciente() throws Exception {
        jdbc.update("UPDATE evaluacion.asignacion_docente SET regla_consolidacion = 'MODA' WHERE id = ?", asignacionRosa);
        logros(alva, c1, "AD", "A", "AD", "A");

        calcular(asignacionRosa, token(rosa));

        assertThat(anual(alva, c1)).isEqualTo("A");
    }

    @Test
    void losPesosDeLosBimestresCambianElPromedio() throws Exception {
        // Pesos iguales: (4+1)/2 = 2,5 -> A. Con pesos 1 y 3: (4+3)/4 = 1,75 -> B
        logros(alva, c1, "AD", "C");
        calcular(asignacionRosa, token(rosa));
        assertThat(anual(alva, c1)).isEqualTo("A");

        jdbc.update("INSERT INTO evaluacion.peso_bimestre (id, asignacion_id, bimestre_id, peso) VALUES (?, ?, ?, 3)",
                UUID.randomUUID().toString(), asignacionRosa, bimestres[2]);
        Respuesta desactualizada = consultar(asignacionRosa, token(rosa));
        Respuesta recalculada = calcular(asignacionRosa, token(rosa));

        assertThat(desactualizada.cuerpo().get("desactualizada").asBoolean()).isTrue();
        assertThat(recalculada.cuerpo().get("calculo").get("actualizadas").asInt()).isEqualTo(1);
        assertThat(anual(alva, c1)).isEqualTo("B");
        assertThat(recalculada.cuerpo().get("desactualizada").asBoolean()).isFalse();
    }

    @Test
    void conLogrosParcialesSeCalculaConLoQueHayYSeCuentanLasCeldasSinNota() throws Exception {
        logros(alva, c1, "A", null, null, null);

        Respuesta r = calcular(asignacionRosa, token(rosa));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(anual(alva, c1)).isEqualTo("A");
        assertThat(r.cuerpo().get("calculo").get("creadas").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get("calculo").get("sinNota").asInt()).isEqualTo(5);
        assertThat(r.cuerpo().get("celdasSinNota").asInt()).isEqualTo(5);
    }

    @Test
    void soloLosAlumnosMatriculadosDeLaSeccionRecibenNota() throws Exception {
        logros(alva, c1, "A");
        logros(anulado, c1, "AD");

        Respuesta r = calcular(asignacionRosa, token(rosa));

        assertThat(anual(alva, c1)).isEqualTo("A");
        assertThat(anual(anulado, c1)).isNull();
        assertThat(r.cuerpo().get("calculo").get("creadas").asInt()).isEqualTo(1);
    }

    // ---- idempotencia ----

    @Test
    void calcularDeNuevoConLosMismosDatosNoCambiaNadaNiSeAudita() throws Exception {
        logros(alva, c1, "AD", "A");
        logros(bravo, c1, "B", "B");
        calcular(asignacionRosa, token(rosa));
        int filas = notasGuardadas();
        int auditorias = auditorias();
        Timestamp calculada = jdbc.queryForObject("SELECT calculada_en FROM evaluacion.nota_consolidada WHERE alumno_id = ?",
                Timestamp.class, alva);

        Respuesta r = calcular(asignacionRosa, token(rosa));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("calculo").get("creadas").asInt()).isZero();
        assertThat(r.cuerpo().get("calculo").get("actualizadas").asInt()).isZero();
        assertThat(r.cuerpo().get("calculo").get("sinCambios").asInt()).isEqualTo(2);
        assertThat(notasGuardadas()).isEqualTo(filas);
        assertThat(auditorias()).isEqualTo(auditorias);
        assertThat(jdbc.queryForObject("SELECT calculada_en FROM evaluacion.nota_consolidada WHERE alumno_id = ?", Timestamp.class, alva))
                .isEqualTo(calculada);
    }

    @Test
    void siCambiaUnLogroOLaReglaLaTablaSeMarcaDesactualizadaYRecalcularReemplazaLaNota() throws Exception {
        logros(alva, c1, "AD", "AD", "AD", "AD");
        calcular(asignacionRosa, token(rosa));
        assertThat(anual(alva, c1)).isEqualTo("AD");

        cambiarLogro(alva, c1, 4, "C");
        assertThat(consultar(asignacionRosa, token(rosa)).cuerpo().get("desactualizada").asBoolean()).isTrue();
        List<String> previos = jdbc.queryForList("SELECT id FROM auditoria.registro_auditoria WHERE accion = 'CALCULAR_NOTAS_FINALES' "
                + "AND id_entidad_afectada = ?", String.class, asignacionRosa);

        Respuesta r = calcular(asignacionRosa, token(rosa));

        assertThat(r.cuerpo().get("calculo").get("actualizadas").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get("desactualizada").asBoolean()).isFalse();
        assertThat(anual(alva, c1)).as("promedio 3,25 -> A").isEqualTo("A");
        assertThat(notasGuardadas()).isEqualTo(1);
        // Auditoría: una línea con el valor anterior y el nuevo
        var nuevos = jdbc.queryForList("SELECT id FROM auditoria.registro_auditoria WHERE accion = 'CALCULAR_NOTAS_FINALES' "
                + "AND id_entidad_afectada = ?", String.class, asignacionRosa).stream().filter(id -> !previos.contains(id)).toList();
        assertThat(nuevos).hasSize(1);
        var cambios = jdbc.queryForList("SELECT campo, valor_anterior, valor_nuevo FROM auditoria.cambio_auditoria WHERE "
                + "registro_auditoria_id = ?", nuevos.get(0));
        assertThat(cambios).singleElement().satisfies(c -> {
            assertThat((String) c.get("campo")).startsWith("anual ").endsWith(" C1");
            assertThat(c.get("valor_anterior")).isEqualTo("AD");
            assertThat(c.get("valor_nuevo")).isEqualTo("A");
        });

        jdbc.update("UPDATE evaluacion.asignacion_docente SET regla_consolidacion = 'ULTIMO_REGISTRO' WHERE id = ?", asignacionRosa);
        assertThat(consultar(asignacionRosa, token(rosa)).cuerpo().get("desactualizada").asBoolean()).isTrue();
        calcular(asignacionRosa, token(rosa));
        assertThat(anual(alva, c1)).isEqualTo("C");
    }

    @Test
    void elPrimerCalculoQuedaAuditadoConUnaLineaPorNota() throws Exception {
        logros(alva, c1, "A");
        logros(bravo, c2, "B");
        List<String> previos = jdbc.queryForList("SELECT id FROM auditoria.registro_auditoria WHERE accion = 'CALCULAR_NOTAS_FINALES' "
                + "AND id_entidad_afectada = ?", String.class, asignacionRosa);

        calcular(asignacionRosa, token(rosa));

        // La auditoría es de solo inserción: se mira el registro nuevo
        var nuevos = jdbc.queryForList("SELECT id FROM auditoria.registro_auditoria WHERE accion = 'CALCULAR_NOTAS_FINALES' "
                + "AND id_entidad_afectada = ?", String.class, asignacionRosa).stream().filter(id -> !previos.contains(id)).toList();
        assertThat(nuevos).hasSize(1);
        var cambios = jdbc.queryForList("SELECT campo, valor_anterior, valor_nuevo FROM auditoria.cambio_auditoria WHERE "
                + "registro_auditoria_id = ?", nuevos.get(0));
        assertThat(cambios).hasSize(2);
        assertThat(cambios).allSatisfy(c -> {
            assertThat((String) c.get("campo")).startsWith("anual ");
            assertThat(c.get("valor_anterior")).isNull();
        });
        assertThat(cambios).extracting(c -> c.get("valor_nuevo")).containsExactlyInAnyOrder("A", "B");
    }

    // ---- precondiciones ----

    @Test
    void sinNingunLogroNoSeCalcula() throws Exception {
        Respuesta r = calcular(asignacionRosa, token(rosa));

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("SIN_LOGROS_REGISTRADOS");
        assertThat(notasGuardadas()).isZero();
    }

    @Test
    void unAnioCerradoSeConsultaPeroNoSeCalcula() throws Exception {
        logros(alva, c1, "A");
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'CERRADO' WHERE id = ?", e.anioId());

        Respuesta ver = consultar(asignacionRosa, token(rosa));
        Respuesta calculo = calcular(asignacionRosa, token(rosa));

        assertThat(ver.estado()).isEqualTo(200);
        assertThat(ver.cuerpo().get("editable").asBoolean()).isFalse();
        assertThat(calculo.estado()).isEqualTo(422);
        assertThat(calculo.cuerpo().get("codigo").asString()).isEqualTo("ANIO_CERRADO");
        assertThat(notasGuardadas()).isZero();
    }

    // ---- alcance y permisos ----

    @Test
    void soloElDocenteDeLaAsignacionVeYCalcula() throws Exception {
        logros(alva, c1, "A");

        Respuesta ver = consultar(asignacionRosa, token(luis));
        Respuesta calculo = calcular(asignacionRosa, token(luis));
        Respuesta inexistente = consultar("no-existe", token(rosa));
        Respuesta calculoInexistente = calcular("no-existe", token(rosa));

        for (Respuesta r : List.of(ver, calculo, inexistente, calculoInexistente)) {
            assertThat(r.estado()).isEqualTo(403);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
        }
        assertThat(notasGuardadas()).isZero();
        assertThat(consultar(asignacionLuis, token(luis)).estado()).isEqualTo(200);
    }

    @Test
    void sinElPermisoNotaCalcularFinalNoSeAccede() throws Exception {
        logros(alva, c1, "A");
        for (String rol : List.of("ALUMNO", "APODERADO", "ADMINISTRADOR")) {
            String token = tokenDe(crearUsuario(rol, true));

            assertThat(consultar(asignacionRosa, token).cuerpo().get("codigo").asString()).isEqualTo("ACCESO_DENEGADO");
            assertThat(calcular(asignacionRosa, token).estado()).isEqualTo(403);
        }
        assertThat(get("/api/v1/asignaciones-docentes/" + asignacionRosa + "/notas-finales", null).estado()).isEqualTo(401);
        assertThat(notasGuardadas()).isZero();
    }

    // ---- concurrencia ----

    @Test
    void ochoCalculosSimultaneosDejanUnaNotaPorCeldaYUnaSolaAuditoria() throws Exception {
        logros(alva, c1, "AD", "A", "A", "B");
        logros(alva, c2, "B", "B");
        logros(bravo, c1, "C", "B", "A");
        logros(castro, c2, "AD");
        int antes = auditorias();
        String token = token(rosa);

        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Respuesta>> tareas = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tareas.add(() -> calcular(asignacionRosa, token));
            }
            for (Future<Respuesta> f : hilos.invokeAll(tareas)) {
                assertThat(f.get().estado()).isEqualTo(200);
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(notasGuardadas()).as("una nota por celda con logros").isEqualTo(4);
        assertThat(auditorias()).as("solo el cálculo que creó las notas se audita").isEqualTo(antes + 1);
        assertThat(anual(alva, c1)).isEqualTo("A");
    }
}
