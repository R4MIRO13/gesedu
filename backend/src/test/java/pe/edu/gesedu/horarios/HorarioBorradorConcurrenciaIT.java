package pe.edu.gesedu.horarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.apoyo.DatosHorario.Escenario;

/**
 * Concurrencia del borrador (CLAUDE.md, RF-41): dos administradores que editan la misma versión nunca guardan los
 * dos; la versión (409 CONFLICTO_CONCURRENCIA) y los índices únicos (409 CONFLICTO_HORARIO) lo garantizan.
 */
class HorarioBorradorConcurrenciaIT extends BaseIT {

    private String cuerpo(String bloque, String seccion, String curso, String docente) throws Exception {
        return json.writeValueAsString(Map.of("bloqueId", bloque, "seccionId", seccion, "cursoId", curso,
                "docenteId", docente));
    }

    /** Lanza las tareas a la vez (todas esperan la señal de salida) y devuelve sus resultados. */
    private <T> List<T> enParalelo(List<Callable<T>> tareas) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(tareas.size());
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (Callable<T> tarea : tareas) {
                futuros.add(hilos.submit(() -> {
                    salida.await();
                    return tarea.call();
                }));
            }
            salida.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }

    private Respuesta post(String ruta, String cuerpo, String token, String ifMatch) throws Exception {
        return enviar("POST", ruta, cuerpo, token, ifMatch);
    }

    @RepeatedTest(5)
    void dosAltasConLaMismaVersionSoloGuardaUna() throws Exception {
        Escenario esc = new DatosHorario(jdbc).crearEscenario();
        String admin1 = tokenDe(crearUsuario("ADMINISTRADOR", true));
        String admin2 = tokenDe(crearUsuario("ADMINISTRADOR", true));
        String base = "/api/v1/horarios/" + esc.anio() + "/borrador";
        String version = etag(get(base, admin1));

        // Dos clases que no chocan entre sí, pero editan la misma versión del borrador
        List<Respuesta> respuestas = enParalelo(List.of(
                () -> post(base + "/asignaciones", cuerpo(esc.lunes745(), esc.seccion3A(), esc.matematica(), esc.rosa()),
                        admin1, version),
                () -> post(base + "/asignaciones", cuerpo(esc.lunes745(), esc.seccion4B(), esc.matematica(), esc.fernando()),
                        admin2, version)));

        assertThat(respuestas.stream().map(Respuesta::estado).sorted().toList()).containsExactly(201, 409);
        Respuesta perdedora = respuestas.stream().filter(r -> r.estado() == 409).findFirst().orElseThrow();
        assertThat(perdedora.cuerpo().get("codigo").asString()).isEqualTo("CONFLICTO_CONCURRENCIA");
        assertThat(contarClases(esc)).isEqualTo(1);
        assertThat(etag(get(base, admin1))).isEqualTo("\"1\"");
    }

    @RepeatedTest(5)
    void dosAltasQueChocanSoloGuardanUnaAunqueLleguenALaVez() throws Exception {
        Escenario esc = new DatosHorario(jdbc).crearEscenario();
        String admin1 = tokenDe(crearUsuario("ADMINISTRADOR", true));
        String admin2 = tokenDe(crearUsuario("ADMINISTRADOR", true));
        String base = "/api/v1/horarios/" + esc.anio() + "/borrador";
        String version = etag(get(base, admin1));

        // Rosa, en el mismo bloque y en dos secciones distintas
        List<Respuesta> respuestas = enParalelo(List.of(
                () -> post(base + "/asignaciones", cuerpo(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.rosa()),
                        admin1, version),
                () -> post(base + "/asignaciones", cuerpo(esc.martes915(), esc.seccion4B(), esc.matematica(), esc.rosa()),
                        admin2, version)));

        assertThat(respuestas.stream().map(Respuesta::estado).sorted().toList()).containsExactly(201, 409);
        assertThat(respuestas.stream().filter(r -> r.estado() == 409).findFirst().orElseThrow()
                .cuerpo().get("codigo").asString()).isIn("CONFLICTO_CONCURRENCIA", "CONFLICTO_HORARIO");
        assertThat(contarClases(esc)).isEqualTo(1);
        // Nunca queda el docente repetido en el bloque
        assertThat(jdbc.queryForObject("SELECT max(n) FROM (SELECT count(*) n FROM horarios.asignacion_horario a "
                + "JOIN horarios.horario_borrador b ON b.id = a.horario_borrador_id "
                + "WHERE b.anio_escolar_id = ? GROUP BY a.bloque_id, a.docente_id) t", Integer.class, esc.anioId()))
                .isEqualTo(1);
    }

    @Test
    void variosAdministradoresPidiendoElBorradorALaVezLoCreanUnaSolaVez() throws Exception {
        Escenario esc = new DatosHorario(jdbc).crearEscenario();
        String base = "/api/v1/horarios/" + esc.anio() + "/borrador";
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tokens.add(tokenDe(crearUsuario("ADMINISTRADOR", true)));
        }

        List<Callable<Respuesta>> tareas = new ArrayList<>();
        for (String token : tokens) {
            tareas.add(() -> get(base, token));
        }
        List<Respuesta> respuestas = enParalelo(tareas);

        assertThat(respuestas).allSatisfy(r -> assertThat(r.estado()).isEqualTo(200));
        assertThat(respuestas.stream().map(r -> r.cuerpo().get("id").asString()).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.horario_borrador WHERE anio_escolar_id = ?",
                Integer.class, esc.anioId())).isEqualTo(1);
    }

    @Test
    void laBaseDeDatosDetieneLosCrucesAunSinLaValidacionDeLaAplicacion() {
        Escenario esc = new DatosHorario(jdbc).crearEscenario();
        String borrador = java.util.UUID.randomUUID().toString();
        jdbc.update("INSERT INTO horarios.horario_borrador (id, anio_escolar_id, fecha_creacion, fecha_actualizacion) "
                + "VALUES (?, ?, now(), now())", borrador, esc.anioId());
        String asociacion = jdbc.queryForObject("SELECT a.id FROM horarios.asociacion_docente_curso a "
                + "JOIN horarios.vinculacion_docente v ON v.id = a.vinculacion_id WHERE v.docente_id = ? "
                + "AND a.curso_id = ?", String.class, esc.rosa(), esc.matematica());
        String insertar = "INSERT INTO horarios.asignacion_horario (id, horario_borrador_id, bloque_id, seccion_id, "
                + "curso_id, docente_id, asociacion_docente_curso_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbc.update(insertar, java.util.UUID.randomUUID().toString(), borrador, esc.martes915(), esc.seccion3A(),
                esc.matematica(), esc.rosa(), asociacion);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(insertar,
                java.util.UUID.randomUUID().toString(), borrador, esc.martes915(), esc.seccion4B(), esc.matematica(),
                esc.rosa(), asociacion)).hasMessageContaining("ux_asignacion_docente_bloque");
    }

    private int contarClases(Escenario esc) {
        return jdbc.queryForObject("SELECT count(*) FROM horarios.asignacion_horario a "
                + "JOIN horarios.horario_borrador b ON b.id = a.horario_borrador_id WHERE b.anio_escolar_id = ?",
                Integer.class, esc.anioId());
    }
}
