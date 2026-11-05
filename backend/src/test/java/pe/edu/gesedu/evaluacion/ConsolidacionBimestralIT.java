package pe.edu.gesedu.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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
 * UC-18, Consolidar notas bimestrales del alumno (RF-27, RF-28, RF-35, RF-37): el administrador ve el avance de cada curso en
 * cada sección y consolida el bimestre cuando todo está completo; las notas pasan a la libreta de cada alumno. En el último
 * bimestre calcula las notas anuales y deja la libreta FINAL, si los anteriores están consolidados y al día.
 * <p>
 * Escenario: año ACTIVO propio con cuatro bimestres; Matemática (C1, C2) que Rosa dicta en 3° A (Alva, Bravo y un matriculado
 * anulado) y Luis en 3° B (Ramos); Comunicación (C1) que Marta dicta en 3° A. Son 8 celdas por bimestre.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsolidacionBimestralIT extends BaseIT {

    private Escenario e;
    private String rosa;
    private String luis;
    private String marta;
    private String matematica3A;
    private String matematica3B;
    private String comunicacion3A;
    private final String[] bimestres = new String[5];
    private String c1;
    private String c2;
    private String k1;
    private String alva;
    private String bravo;
    private String ramos;
    private String anulado;
    private String admin;
    private String adminId;

    @BeforeAll
    void escenario() throws Exception {
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
        String matematica = curso("Matemática", "matematica");
        c1 = competencia(matematica, "C1", 1);
        c2 = competencia(matematica, "C2", 2);
        String comunicacion = curso("Comunicación", "comunicacion");
        k1 = competencia(comunicacion, "C1", 1);
        DatosHorario docentes = new DatosHorario(jdbc);
        rosa = docentes.docente("Rosa", "Quispe");
        luis = docentes.docente("Luis", "Castro");
        marta = docentes.docente("Marta", "Ibáñez");
        matematica3A = asignacion(rosa, matematica, e.seccion3A());
        matematica3B = asignacion(luis, matematica, e.seccion3B());
        comunicacion3A = asignacion(marta, comunicacion, e.seccion3A());
        alva = alumno(e.seccion3A(), "Alva", "Lucía");
        bravo = alumno(e.seccion3A(), "Bravo", "Tomás");
        anulado = alumno(e.seccion3A(), "Zeta", "Anulado");
        jdbc.update("UPDATE matricula.matricula SET estado = 'ANULADA' WHERE alumno_id = ?", anulado);
        ramos = alumno(e.seccion3B(), "Ramos", "Pedro");
        String correo = crearUsuario("ADMINISTRADOR", true);
        adminId = idDe(correo);
        admin = tokenDe(correo);
    }

    @AfterAll
    void devolverElAnio() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
    }

    @BeforeEach
    void restablecer() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        jdbc.update("DELETE FROM evaluacion.nota_consolidada WHERE anio_escolar_id = ?", e.anioId());
        jdbc.update("DELETE FROM evaluacion.libreta WHERE anio_escolar_id = ?", e.anioId());
        jdbc.update("DELETE FROM evaluacion.registro_logro WHERE asignacion_id IN (?, ?, ?)", matematica3A, matematica3B, comunicacion3A);
        jdbc.update("DELETE FROM evaluacion.consolidacion_bimestral WHERE bimestre_id IN (?, ?, ?, ?)", bimestres[1], bimestres[2],
                bimestres[3], bimestres[4]);
        jdbc.update("UPDATE evaluacion.asignacion_docente SET regla_consolidacion = 'PROMEDIO_PONDERADO' WHERE id IN (?, ?, ?)",
                matematica3A, matematica3B, comunicacion3A);
        jdbc.update("DELETE FROM evaluacion.peso_bimestre WHERE asignacion_id IN (?, ?, ?)", matematica3A, matematica3B, comunicacion3A);
    }

    // ---- datos y ayudas ----

    private String curso(String nombre, String slug) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) VALUES (?, ?, ?, ?, 4)",
                id, e.anioId(), nombre, slug);
        return id;
    }

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

    private void logro(String asignacion, String alumno, String competencia, int bimestre, String nivel) {
        jdbc.update("INSERT INTO evaluacion.registro_logro (id, asignacion_id, alumno_id, competencia_id, bimestre_id, nivel_logro, "
                + "fecha_registro) VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), asignacion, alumno, competencia,
                bimestres[bimestre], nivel, Timestamp.from(Instant.parse("2027-03-10T15:00:00Z")));
    }

    /** Todas las celdas de Matemática 3° A del bimestre con el mismo nivel. */
    private void completarMatematica3A(int bimestre, String nivel) {
        for (String alumno : List.of(alva, bravo)) {
            for (String competencia : List.of(c1, c2)) {
                logro(matematica3A, alumno, competencia, bimestre, nivel);
            }
        }
    }

    private void completarMatematica3B(int bimestre, String nivel) {
        for (String competencia : List.of(c1, c2)) {
            logro(matematica3B, ramos, competencia, bimestre, nivel);
        }
    }

    private void completarComunicacion3A(int bimestre, String nivel) {
        for (String alumno : List.of(alva, bravo)) {
            logro(comunicacion3A, alumno, k1, bimestre, nivel);
        }
    }

    /** Los tres cursos completos en el bimestre (8 celdas). */
    private void completarTodo(int bimestre, String nivel) {
        completarMatematica3A(bimestre, nivel);
        completarMatematica3B(bimestre, nivel);
        completarComunicacion3A(bimestre, nivel);
    }

    private void marcar(int bimestre, String estado) {
        jdbc.update("INSERT INTO evaluacion.consolidacion_bimestral (id, bimestre_id, fecha_consolidacion, estado) VALUES (?, ?, now(), ?)",
                UUID.randomUUID().toString(), bimestres[bimestre], estado);
    }

    private String ruta(int bimestre, String parte) {
        return "/api/v1/anios-escolares/" + e.anio() + "/bimestres/" + bimestre + "/" + parte;
    }

    private Respuesta avance(int bimestre, String token) throws Exception {
        return get(ruta(bimestre, "avance"), token);
    }

    private Respuesta consolidar(int bimestre, String token) throws Exception {
        return post(ruta(bimestre, "consolidacion"), "", token);
    }

    private int contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int notasBimestrales(int bimestre) {
        return contar("SELECT count(*) FROM evaluacion.nota_consolidada WHERE anio_escolar_id = ? AND bimestre_id = ? AND tipo = 'BIMESTRAL'",
                e.anioId(), bimestres[bimestre]);
    }

    private int notasAnuales() {
        return contar("SELECT count(*) FROM evaluacion.nota_consolidada WHERE anio_escolar_id = ? AND tipo = 'ANUAL'", e.anioId());
    }

    private int libretas() {
        return contar("SELECT count(*) FROM evaluacion.libreta WHERE anio_escolar_id = ?", e.anioId());
    }

    private String estadoDe(int bimestre) {
        List<String> estados = jdbc.queryForList("SELECT estado FROM evaluacion.consolidacion_bimestral WHERE bimestre_id = ?",
                String.class, bimestres[bimestre]);
        return estados.isEmpty() ? null : estados.get(0);
    }

    private int auditorias() {
        return contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'CONSOLIDAR_BIMESTRE' AND id_entidad_afectada = ?",
                e.anio() + "/" + 1) + contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'CONSOLIDAR_BIMESTRE' "
                + "AND id_entidad_afectada = ?", e.anio() + "/" + 4);
    }

    private List<String> nombres(tools.jackson.databind.JsonNode lista, String campo) {
        List<String> resultado = new ArrayList<>();
        lista.forEach(x -> resultado.add(x.get(campo).asString()));
        return resultado;
    }

    // ---- pasos 1 a 3: el avance ----

    @Test
    void sinNotasElAvanceMuestraTodosLosCursosIncompletos() throws Exception {
        Respuesta r = avance(1, admin);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("anio").asInt()).isEqualTo(e.anio());
        assertThat(r.cuerpo().get("bimestre").get("numero").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get("bimestre").get("ultimo").asBoolean()).isFalse();
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("SIN_CONSOLIDAR");
        assertThat(r.cuerpo().get("fechaConsolidacion").isNull()).isTrue();
        assertThat(r.cuerpo().get("totales").get("cursos").asInt()).isEqualTo(3);
        assertThat(r.cuerpo().get("totales").get("completos").asInt()).isZero();
        assertThat(r.cuerpo().get("totales").get("incompletos").asInt()).isEqualTo(3);
        assertThat(r.cuerpo().get("puedeConsolidar").asBoolean()).isFalse();
        assertThat(r.cuerpo().get("motivo").asString()).isEqualTo("No se puede consolidar el Bimestre I: 3 secciones incompletas");
        List<String> docentes = new ArrayList<>();
        r.cuerpo().get("docentesPendientes").forEach(x -> docentes.add(x.asString()));
        assertThat(docentes).containsExactly("Luis Castro", "Marta Ibáñez", "Rosa Quispe");
        assertThat(r.cuerpo().get("bimestres").size()).isEqualTo(4);
        assertThat(r.cuerpo().get("cursos").get(0).get("porcentaje").asInt()).isZero();
        assertThat(r.cuerpo().get("editable").asBoolean()).isTrue();
    }

    @Test
    void elAvanceCuentaLasCeldasConLogroYOrdenaLosIncompletosPrimero() throws Exception {
        completarMatematica3B(1, "A");                 // 2/2 = completo
        logro(matematica3A, alva, c1, 1, "B");         // 1/4 = 25 %
        logro(matematica3A, alva, c2, 1, "B");
        logro(matematica3A, bravo, c1, 1, "B");        // 3/4 = 75 %
        logro(comunicacion3A, alva, k1, 1, "A");       // 1/2 = 50 %
        logro(matematica3A, anulado, c1, 1, "AD");     // un alumno anulado no cuenta

        Respuesta r = avance(1, admin);

        var cursos = r.cuerpo().get("cursos");
        assertThat(cursos.size()).isEqualTo(3);
        assertThat(nombres(cursos, "curso")).containsExactly("Comunicación", "Matemática", "Matemática");
        assertThat(cursos.get(0).get("porcentaje").asInt()).isEqualTo(50);
        assertThat(cursos.get(0).get("celdasCompletas").asInt()).isEqualTo(1);
        assertThat(cursos.get(0).get("celdasTotales").asInt()).isEqualTo(2);
        assertThat(cursos.get(0).get("docente").asString()).isEqualTo("Marta Ibáñez");
        assertThat(cursos.get(1).get("seccion").asString()).isEqualTo("3° A");
        assertThat(cursos.get(1).get("porcentaje").asInt()).isEqualTo(75);
        assertThat(cursos.get(2).get("seccion").asString()).isEqualTo("3° B");
        assertThat(cursos.get(2).get("completo").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("totales").get("completos").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get("totales").get("incompletos").asInt()).isEqualTo(2);
    }

    @Test
    void conTodoCompletoSePuedeConsolidar() throws Exception {
        completarTodo(1, "A");

        Respuesta r = avance(1, admin);

        assertThat(r.cuerpo().get("puedeConsolidar").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("motivo").isNull()).isTrue();
        assertThat(r.cuerpo().get("totales").get("incompletos").asInt()).isZero();
        assertThat(r.cuerpo().get("docentesPendientes").size()).isZero();
        assertThat(r.cuerpo().get("resultado").isNull()).isTrue();
    }

    // ---- FA-2: registro incompleto ----

    @Test
    void fa2ConNotasPendientesNoSeConsolidaYSeListanLosDocentes() throws Exception {
        completarComunicacion3A(1, "A");
        completarMatematica3B(1, "A");
        logro(matematica3A, alva, c1, 1, "A");

        Respuesta r = consolidar(1, admin);

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("BIMESTRE_INCOMPLETO");
        assertThat(r.cuerpo().get("detail").asString()).isEqualTo("No se puede consolidar el Bimestre I: 1 sección incompleta");
        assertThat(r.cuerpo().get("pendientes").size()).isEqualTo(1);
        assertThat(r.cuerpo().get("pendientes").get(0).get("docente").asString()).isEqualTo("Rosa Quispe");
        assertThat(r.cuerpo().get("pendientes").get(0).get("curso").asString()).isEqualTo("Matemática");
        assertThat(r.cuerpo().get("pendientes").get(0).get("porcentaje").asInt()).isEqualTo(25);
        assertThat(r.cuerpo().get("docentes").get(0).asString()).isEqualTo("Rosa Quispe");
        assertThat(notasBimestrales(1)).isZero();
        assertThat(libretas()).isZero();
        assertThat(estadoDe(1)).isNull();
    }

    // ---- paso 4: consolidar ----

    @Test
    void consolidarPasaLasNotasBimestralesALaLibretaDeCadaAlumno() throws Exception {
        completarTodo(1, "A");
        List<String> previos = jdbc.queryForList("SELECT id FROM auditoria.registro_auditoria WHERE accion = 'CONSOLIDAR_BIMESTRE' "
                + "AND id_entidad_afectada = ?", String.class, e.anio() + "/1");

        Respuesta r = consolidar(1, admin);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("COMPLETADA");
        assertThat(r.cuerpo().get("fechaConsolidacion").isNull()).isFalse();
        assertThat(r.cuerpo().get("resultado").get("creadas").asInt()).isEqualTo(8);
        assertThat(r.cuerpo().get("resultado").get("actualizadas").asInt()).isZero();
        assertThat(r.cuerpo().get("resultado").get("libretas").asInt()).isEqualTo(3);
        assertThat(r.cuerpo().get("resultado").get("finalizada").asBoolean()).isFalse();
        assertThat(r.cuerpo().get("resultado").get("notasAnuales").isNull()).isTrue();
        assertThat(notasBimestrales(1)).isEqualTo(8);
        assertThat(notasAnuales()).as("el bimestre I no es el último: no hay notas anuales").isZero();
        assertThat(estadoDe(1)).isEqualTo("COMPLETADA");
        assertThat(jdbc.queryForObject("SELECT ejecutada_por FROM evaluacion.consolidacion_bimestral WHERE bimestre_id = ?", String.class,
                bimestres[1])).isEqualTo(adminId);
        // Una libreta PARCIAL por alumno matriculado (no por el anulado) y cada nota enlazada a la de su alumno
        assertThat(libretas()).isEqualTo(3);
        assertThat(contar("SELECT count(*) FROM evaluacion.libreta WHERE anio_escolar_id = ? AND estado = 'PARCIAL'", e.anioId())).isEqualTo(3);
        assertThat(contar("SELECT count(*) FROM evaluacion.libreta WHERE alumno_id = ?", anulado)).isZero();
        assertThat(contar("SELECT count(*) FROM evaluacion.nota_consolidada n JOIN evaluacion.libreta l ON l.id = n.libreta_id "
                + "AND l.alumno_id = n.alumno_id WHERE n.anio_escolar_id = ?", e.anioId())).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT nivel_logro FROM evaluacion.nota_consolidada WHERE alumno_id = ? AND competencia_id = ? "
                + "AND bimestre_id = ?", String.class, alva, c1, bimestres[1])).isEqualTo("A");
        // Auditoría: un registro nuevo con el estado del bimestre y las cuentas
        var nuevos = jdbc.queryForList("SELECT id FROM auditoria.registro_auditoria WHERE accion = 'CONSOLIDAR_BIMESTRE' "
                + "AND id_entidad_afectada = ?", String.class, e.anio() + "/1").stream().filter(id -> !previos.contains(id)).toList();
        assertThat(nuevos).hasSize(1);
        var cambios = jdbc.queryForList("SELECT campo, valor_anterior, valor_nuevo FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ?",
                nuevos.get(0));
        assertThat(cambios).anySatisfy(c -> {
            assertThat(c.get("campo")).isEqualTo("bimestre 1");
            assertThat(c.get("valor_anterior")).isEqualTo("SIN_CONSOLIDAR");
            assertThat(c.get("valor_nuevo")).isEqualTo("COMPLETADA");
        });
        assertThat(cambios).anySatisfy(c -> assertThat(c.get("valor_nuevo")).isEqualTo("8 nuevas, 0 actualizadas, 0 sin cambios"));
    }

    @Test
    void aplicaLaReglaDeCadaCurso() throws Exception {
        // Con un logro por bimestre, la nota bimestral es ese logro con cualquier regla
        jdbc.update("UPDATE evaluacion.asignacion_docente SET regla_consolidacion = 'MODA' WHERE id = ?", matematica3A);
        jdbc.update("UPDATE evaluacion.asignacion_docente SET regla_consolidacion = 'ULTIMO_REGISTRO' WHERE id = ?", comunicacion3A);
        completarMatematica3A(1, "AD");
        completarMatematica3B(1, "B");
        completarComunicacion3A(1, "C");

        assertThat(consolidar(1, admin).estado()).isEqualTo(200);

        assertThat(jdbc.queryForObject("SELECT nivel_logro FROM evaluacion.nota_consolidada WHERE alumno_id = ? AND competencia_id = ? "
                + "AND bimestre_id = ?", String.class, alva, c1, bimestres[1])).isEqualTo("AD");
        assertThat(jdbc.queryForObject("SELECT nivel_logro FROM evaluacion.nota_consolidada WHERE alumno_id = ? AND competencia_id = ? "
                + "AND bimestre_id = ?", String.class, ramos, c2, bimestres[1])).isEqualTo("B");
        assertThat(jdbc.queryForObject("SELECT nivel_logro FROM evaluacion.nota_consolidada WHERE alumno_id = ? AND competencia_id = ? "
                + "AND bimestre_id = ?", String.class, bravo, k1, bimestres[1])).isEqualTo("C");
    }

    @Test
    void sePuedeConsolidarDespuesDeLaFechaDeFinDelBimestre() throws Exception {
        // El año de la prueba es el 24xx: el bimestre ya "terminó" o aún no empieza respecto del reloj real, y da igual (S-69)
        completarTodo(2, "B");

        Respuesta r = consolidar(2, admin);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(notasBimestrales(2)).isEqualTo(8);
    }

    // ---- idempotencia ----

    @Test
    void reconsolidarConLosMismosDatosNoDuplicaNiCambiaLasNotas() throws Exception {
        completarTodo(1, "A");
        consolidar(1, admin);
        Timestamp calculada = jdbc.queryForObject("SELECT calculada_en FROM evaluacion.nota_consolidada WHERE alumno_id = ? AND "
                + "competencia_id = ? AND bimestre_id = ?", Timestamp.class, alva, c1, bimestres[1]);

        Respuesta r = consolidar(1, admin);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("resultado").get("creadas").asInt()).isZero();
        assertThat(r.cuerpo().get("resultado").get("actualizadas").asInt()).isZero();
        assertThat(r.cuerpo().get("resultado").get("sinCambios").asInt()).isEqualTo(8);
        assertThat(notasBimestrales(1)).isEqualTo(8);
        assertThat(libretas()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluacion.consolidacion_bimestral WHERE bimestre_id = ?", Integer.class,
                bimestres[1])).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT calculada_en FROM evaluacion.nota_consolidada WHERE alumno_id = ? AND competencia_id = ? "
                + "AND bimestre_id = ?", Timestamp.class, alva, c1, bimestres[1])).isEqualTo(calculada);
    }

    @Test
    void reconsolidarTrasCorregirUnLogroReemplazaLaNotaYDejaElBimestreAlDia() throws Exception {
        completarTodo(1, "A");
        consolidar(1, admin);
        jdbc.update("UPDATE evaluacion.registro_logro SET nivel_logro = 'AD' WHERE alumno_id = ? AND competencia_id = ? AND bimestre_id = ?",
                alva, c1, bimestres[1]);
        jdbc.update("UPDATE evaluacion.consolidacion_bimestral SET estado = 'DESACTUALIZADA' WHERE bimestre_id = ?", bimestres[1]);
        assertThat(avance(1, admin).cuerpo().get("estado").asString()).isEqualTo("DESACTUALIZADA");

        Respuesta r = consolidar(1, admin);

        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("COMPLETADA");
        assertThat(r.cuerpo().get("resultado").get("actualizadas").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get("resultado").get("sinCambios").asInt()).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT nivel_logro FROM evaluacion.nota_consolidada WHERE alumno_id = ? AND competencia_id = ? "
                + "AND bimestre_id = ?", String.class, alva, c1, bimestres[1])).isEqualTo("AD");
        assertThat(notasBimestrales(1)).isEqualTo(8);
        assertThat(estadoDe(1)).isEqualTo("COMPLETADA");
    }

    // ---- FA-1: el último bimestre ----

    @Test
    void fa1EnElUltimoBimestreCalculaLasNotasAnualesYDejaLaLibretaFinal() throws Exception {
        // Alva en C1 de Matemática: AD, A, A, B -> 3,0 = A ; todo lo demás A en los cuatro bimestres
        for (int b = 1; b <= 4; b++) {
            completarMatematica3B(b, "A");
            completarComunicacion3A(b, "A");
            logro(matematica3A, alva, c2, b, "A");
            logro(matematica3A, bravo, c1, b, "A");
            logro(matematica3A, bravo, c2, b, "A");
        }
        String[] alvaC1 = {"AD", "A", "A", "B"};
        for (int b = 1; b <= 4; b++) {
            logro(matematica3A, alva, c1, b, alvaC1[b - 1]);
        }
        for (int b = 1; b <= 3; b++) {
            assertThat(consolidar(b, admin).estado()).isEqualTo(200);
        }
        assertThat(notasAnuales()).isZero();

        Respuesta r = consolidar(4, admin);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("bimestre").get("ultimo").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("resultado").get("finalizada").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("resultado").get("notasAnuales").get("creadas").asInt()).isEqualTo(8);
        assertThat(notasAnuales()).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT nivel_logro FROM evaluacion.nota_consolidada WHERE alumno_id = ? AND competencia_id = ? "
                + "AND tipo = 'ANUAL'", String.class, alva, c1)).isEqualTo("A");
        assertThat(contar("SELECT count(*) FROM evaluacion.libreta WHERE anio_escolar_id = ? AND estado = 'FINAL'", e.anioId())).isEqualTo(3);
        assertThat(contar("SELECT count(*) FROM evaluacion.nota_consolidada n JOIN evaluacion.libreta l ON l.id = n.libreta_id "
                + "AND l.alumno_id = n.alumno_id WHERE n.anio_escolar_id = ? AND n.tipo = 'ANUAL'", e.anioId())).isEqualTo(8);
        // Reconsolidar un bimestre anterior no devuelve la libreta a parcial
        assertThat(consolidar(2, admin).estado()).isEqualTo(200);
        assertThat(contar("SELECT count(*) FROM evaluacion.libreta WHERE anio_escolar_id = ? AND estado = 'FINAL'", e.anioId())).isEqualTo(3);
        // Y volver a consolidar el último no duplica las notas anuales
        Respuesta otra = consolidar(4, admin);
        assertThat(otra.cuerpo().get("resultado").get("notasAnuales").get("creadas").asInt()).isZero();
        assertThat(otra.cuerpo().get("resultado").get("notasAnuales").get("sinCambios").asInt()).isEqualTo(8);
        assertThat(notasAnuales()).isEqualTo(8);
    }

    @Test
    void elUltimoBimestreExigeLosAnterioresConsolidadosYAlDia() throws Exception {
        completarTodo(4, "A");
        consolidar(1, admin);
        completarTodo(1, "A");
        completarTodo(2, "A");
        completarTodo(3, "A");
        marcar(1, "COMPLETADA");
        marcar(3, "DESACTUALIZADA");   // el II no está consolidado y el III está desactualizado

        Respuesta r = consolidar(4, admin);

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("BIMESTRES_ANTERIORES_PENDIENTES");
        assertThat(r.cuerpo().get("bimestres").size()).isEqualTo(2);
        assertThat(r.cuerpo().get("bimestres").get(0).get("numero").asInt()).isEqualTo(2);
        assertThat(r.cuerpo().get("bimestres").get(0).get("estado").asString()).isEqualTo("SIN_CONSOLIDAR");
        assertThat(r.cuerpo().get("bimestres").get(1).get("estado").asString()).isEqualTo("DESACTUALIZADA");
        assertThat(notasBimestrales(4)).isZero();
        assertThat(notasAnuales()).isZero();
        assertThat(estadoDe(4)).isNull();
        // El avance del último bimestre lo explica
        Respuesta avance = avance(4, admin);
        assertThat(avance.cuerpo().get("puedeConsolidar").asBoolean()).isFalse();
        assertThat(avance.cuerpo().get("motivo").asString()).contains("bimestres anteriores");
    }

    @Test
    void unBimestreAnteriorDesactualizadoImpideElUltimoPeroNoUnoIntermedio() throws Exception {
        for (int b = 1; b <= 4; b++) {
            completarTodo(b, "A");
        }
        marcar(1, "COMPLETADA");
        marcar(2, "DESACTUALIZADA");
        marcar(3, "COMPLETADA");

        assertThat(consolidar(4, admin).cuerpo().get("codigo").asString()).isEqualTo("BIMESTRES_ANTERIORES_PENDIENTES");
        // Un bimestre intermedio no depende de los demás
        assertThat(consolidar(3, admin).estado()).isEqualTo(200);
        // Al reconsolidar el II queda al día y entonces el último se puede
        assertThat(consolidar(2, admin).estado()).isEqualTo(200);
        assertThat(consolidar(4, admin).estado()).isEqualTo(200);
    }

    @Test
    void elUltimoBimestreIncompletoSeBloqueaPorLasNotasPendientes() throws Exception {
        completarMatematica3B(4, "A");

        Respuesta r = consolidar(4, admin);

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("BIMESTRE_INCOMPLETO");
    }

    // ---- precondiciones ----

    @Test
    void unAnioCerradoSeConsultaPeroNoSeConsolida() throws Exception {
        completarTodo(1, "A");
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'CERRADO' WHERE id = ?", e.anioId());

        Respuesta ver = avance(1, admin);
        Respuesta r = consolidar(1, admin);

        assertThat(ver.estado()).isEqualTo(200);
        assertThat(ver.cuerpo().get("editable").asBoolean()).isFalse();
        assertThat(ver.cuerpo().get("puedeConsolidar").asBoolean()).isFalse();
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("ANIO_CERRADO");
        assertThat(notasBimestrales(1)).isZero();
    }

    @Test
    void unBimestreOUnAnioInexistenteEs404() throws Exception {
        assertThat(avance(9, admin).estado()).isEqualTo(404);
        assertThat(consolidar(9, admin).estado()).isEqualTo(404);
        assertThat(get("/api/v1/anios-escolares/1900/bimestres/1/avance", admin).estado()).isEqualTo(404);
    }

    // ---- permisos ----

    @Test
    void soloQuienTieneNotaConsolidarVeYConsolida() throws Exception {
        completarTodo(1, "A");
        DatosHorario datos = new DatosHorario(jdbc);
        String usuarioRosa = jdbc.queryForObject("SELECT usuario_id FROM seguridad.docente WHERE id = ?", String.class, rosa);
        String correoRosa = "consolida-" + usuarioRosa.substring(0, 8) + "@test.gesedu.pe";
        jdbc.update("UPDATE seguridad.usuario SET correo = ?, hash_contrasena = ?, activo = TRUE WHERE id = ?", correoRosa,
                codificador.encode(CONTRASENA), usuarioRosa);
        invalidarCuentas();
        List<String> tokens = List.of(tokenDe(correoRosa), tokenDe(crearUsuario("ALUMNO", true)), tokenDe(crearUsuario("APODERADO", true)));

        for (String token : tokens) {
            assertThat(avance(1, token).cuerpo().get("codigo").asString()).isEqualTo("ACCESO_DENEGADO");
            assertThat(consolidar(1, token).estado()).isEqualTo(403);
        }
        assertThat(get(ruta(1, "avance"), null).estado()).isEqualTo(401);
        assertThat(notasBimestrales(1)).isZero();
        assertThat(datos).isNotNull();
    }

    // ---- concurrencia ----

    @Test
    void cuatroConsolidacionesSimultaneasDejanUnaSolaConstanciaYUnaNotaPorCelda() throws Exception {
        completarTodo(1, "A");

        ExecutorService hilos = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Respuesta>> tareas = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                tareas.add(() -> consolidar(1, admin));
            }
            for (Future<Respuesta> f : hilos.invokeAll(tareas)) {
                assertThat(f.get().estado()).isEqualTo(200);
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(contar("SELECT count(*) FROM evaluacion.consolidacion_bimestral WHERE bimestre_id = ?", bimestres[1])).isEqualTo(1);
        assertThat(notasBimestrales(1)).isEqualTo(8);
        assertThat(libretas()).isEqualTo(3);
        assertThat(Map.of("estado", estadoDe(1))).containsEntry("estado", "COMPLETADA");
    }

    @Test
    void unaEdicionDeLogrosDuranteLaConsolidacionQuedaMarcadaComoDesactualizada() throws Exception {
        // La edición de UC-15 bloquea la asignación y mira la constancia: si llega después de consolidar, la marca
        completarTodo(1, "A");
        assertThat(consolidar(1, admin).estado()).isEqualTo(200);
        jdbc.update("UPDATE evaluacion.consolidacion_bimestral SET estado = 'DESACTUALIZADA' WHERE bimestre_id = ?", bimestres[1]);

        Respuesta r = avance(1, admin);

        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("DESACTUALIZADA");
        assertThat(r.cuerpo().get("bimestres").get(0).get("estado").asString()).isEqualTo("DESACTUALIZADA");
    }
}
