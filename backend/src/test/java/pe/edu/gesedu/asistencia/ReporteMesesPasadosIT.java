package pe.edu.gesedu.asistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.apoyo.DatosMatricula;
import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;
import pe.edu.gesedu.apoyo.RelojPrueba;
import pe.edu.gesedu.apoyo.RelojPruebaConfig;

/**
 * Reportes de meses pasados (UC-10, UC-11; fase 6, tarea 6.4): cada día se evalúa con el universo que había entonces.
 * Los docentes esperan clase según la versión del horario vigente en cada fecha, y cada alumno cuenta en la sección que
 * tenía ese día (historial de la matrícula, RF-05).
 * <p>
 * Escenario: año ACTIVO con jornada los lunes. La versión 1 del horario (Rosa dicta) se publicó en febrero; la versión 2
 * (la dicta Carmen) se publicó la víspera del tercer lunes de marzo. Pedro, de 3° A, se traslada a 3° B el tercer lunes.
 */
@Import(RelojPruebaConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReporteMesesPasadosIT extends BaseIT {

    private static final String RUTA = "/api/v1/reportes/asistencia";

    @Autowired
    private RelojPrueba reloj;

    private Escenario e;
    private String rosa;
    private String carmen;
    private String pedro;
    private String matriculaPedro;
    private List<LocalDate> lunes;

    @BeforeAll
    void escenario() throws Exception {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE estado = 'ACTIVO'");
        e = new DatosMatricula(jdbc).crearEscenario(30);
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        String[][] bimestres = {{"1", "02-15", "04-30"}, {"2", "05-01", "07-15"}, {"3", "07-16", "09-30"}, {"4", "10-01", "12-20"}};
        for (String[] b : bimestres) {
            jdbc.update("INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin) "
                    + "VALUES (?, ?, ?, ?::date, ?::date)", UUID.randomUUID().toString(), e.anioId(), Integer.parseInt(b[0]),
                    e.anio() + "-" + b[1], e.anio() + "-" + b[2]);
        }
        String bloque = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO horarios.bloque_horario (id, anio_escolar_id, dia_semana, hora_inicio, hora_fin) "
                + "VALUES (?, ?, 'LUNES', '07:45', '08:30')", bloque, e.anioId());
        String curso = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) VALUES (?, ?, 'Matemática', 'matematica', 5)",
                curso, e.anioId());
        DatosHorario docentes = new DatosHorario(jdbc);
        rosa = docentes.docente("Rosa", "Quispe");
        carmen = docentes.docente("Carmen", "Villanueva");

        lunes = new ArrayList<>();
        for (LocalDate d = LocalDate.of(e.anio(), 3, 1); d.getMonthValue() == 3; d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.MONDAY) {
                lunes.add(d);
            }
        }
        String admin = idDe(crearUsuario("ADMINISTRADOR", true));
        // v1 (febrero, ya no vigente): la dicta Rosa. v2 (víspera del 3.er lunes de marzo, vigente): la dicta Carmen
        String v1 = version(1, LocalDate.of(e.anio(), 2, 20).atTime(12, 0).atZone(reloj.getZone()).toInstant(), false, admin);
        String v2 = version(2, lunes.get(2).minusDays(1).atTime(12, 0).atZone(reloj.getZone()).toInstant(), true, admin);
        clase(v1, bloque, rosa, curso);
        clase(v2, bloque, carmen, curso);

        // Una marcación de cada docente (sin registros el reporte no se genera, FA-2)
        for (String[] m : new String[][] {{rosa, lunes.get(0).toString()}, {carmen, lunes.get(3).toString()}}) {
            jdbc.update("INSERT INTO asistencia.asistencia_docente (id, docente_id, fecha, hora_registro, estado) VALUES (?, ?, ?::date, ?, 'PUNTUAL')",
                    UUID.randomUUID().toString(), m[0], m[1], java.sql.Timestamp.from(LocalDate.parse(m[1]).atTime(7, 40).atZone(reloj.getZone()).toInstant()));
        }
        // Tres alumnos en 3° A; Pedro es uno de ellos y se traslada a 3° B el tercer lunes (por la API, con historial)
        pedro = alumno(e.seccion3A(), "Paz", "Pedro");
        alumno(e.seccion3A(), "Alva", "Lucía");
        alumno(e.seccion3B(), "Ramos", "Rita");
        matriculaPedro = jdbc.queryForObject("SELECT id FROM matricula.matricula WHERE alumno_id = ?", String.class, pedro);
        for (LocalDate d : lunes) {
            jdbc.update("INSERT INTO asistencia.asistencia_alumno (id, alumno_id, fecha, estado) VALUES (?, ?, ?, 'PUNTUAL')",
                    UUID.randomUUID().toString(), pedro, Date.valueOf(d));
        }
        // Las matrículas se insertaron sin pasar por la reserva de vacantes: el origen debe tener cupos ocupados para trasladar
        jdbc.update("UPDATE academico.seccion SET vacantes_disponibles = vacantes_disponibles - 2 WHERE id = ?", e.seccion3A());
        reloj.fijar(lunes.get(2), "09:00");
        Respuesta traslado = enviar("PATCH", "/api/v1/matriculas/" + matriculaPedro + "/seccion",
                json.writeValueAsString(Map.of("seccionId", e.seccion3B())), tokenDe(crearUsuario("ADMINISTRADOR", true)), null);
        assertThat(traslado.estado()).as(String.valueOf(traslado.cuerpo())).isEqualTo(200);
        reloj.fijar(LocalDate.of(e.anio(), 4, 10), "10:00");
    }

    @AfterAll
    void devolverElAnio() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
    }

    private String version(int numero, java.time.Instant publicada, boolean vigente, String admin) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO horarios.horario_publicado (id, anio_escolar_id, numero_version, fecha_hora_publicacion, vigente, "
                + "publicado_por) VALUES (?, ?, ?, ?, ?, ?)", id, e.anioId(), numero, java.sql.Timestamp.from(publicada), vigente, admin);
        return id;
    }

    private void clase(String horario, String bloque, String docente, String curso) {
        jdbc.update("INSERT INTO horarios.asignacion_publicada (id, horario_publicado_id, bloque_id, seccion_id, curso_id, docente_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), horario, bloque, e.seccion3A(), curso, docente);
    }

    private String alumno(String seccionId, String apellidos, String nombres) {
        DatosMatricula datos = new DatosMatricula(jdbc);
        DatosMatricula.Solicitud s = datos.solicitud(e, false);
        datos.matricularYa(e, s, seccionId);
        jdbc.update("UPDATE seguridad.usuario SET apellidos = ?, nombres = ? WHERE id = (SELECT usuario_id FROM seguridad.alumno WHERE id = ?)",
                apellidos, nombres, s.alumnoId());
        return s.alumnoId();
    }

    private JsonNode reporte(String ambito, String seccion) throws Exception {
        Respuesta r = get(RUTA + "?ambito=" + ambito + "&mes=3&anio=" + e.anio() + (seccion == null ? "" : "&seccion=" + seccion),
                tokenDe(crearUsuario("ADMINISTRADOR", true)));
        assertThat(r.estado()).isEqualTo(200);
        return r.cuerpo();
    }

    private static JsonNode fila(JsonNode reporte, String parteDelNombre) {
        for (JsonNode f : reporte.get("filas")) {
            if (f.get("nombre").asString().contains(parteDelNombre)) {
                return f;
            }
        }
        return null;
    }

    @Test
    void cadaDocenteEsperaClaseSegunLaVersionDelHorarioVigenteEnCadaFecha() throws Exception {
        JsonNode r = reporte("DOCENTES", null);

        assertThat(lunes.size()).isGreaterThanOrEqualTo(4);
        assertThat(fila(r, "Quispe").get("diasEsperados").asInt()).as("Rosa dictó con la v1: los dos primeros lunes").isEqualTo(2);
        assertThat(fila(r, "Villanueva").get("diasEsperados").asInt()).as("Carmen dicta desde la v2: el resto de lunes")
                .isEqualTo(lunes.size() - 2);
        // Cada uno marcó un solo lunes: el resto de sus días esperados queda sin registro
        assertThat(fila(r, "Quispe").get("noRegistrados").asInt()).isEqualTo(1);
        assertThat(fila(r, "Villanueva").get("noRegistrados").asInt()).isEqualTo(lunes.size() - 3);
    }

    @Test
    void elAlumnoTrasladadoCuentaEnCadaSeccionSoloLosDiasQueEstuvoAllí() throws Exception {
        JsonNode origen = reporte("ALUMNOS", "3A");
        JsonNode destino = reporte("ALUMNOS", "3B");

        JsonNode enOrigen = fila(origen, "Paz");
        JsonNode enDestino = fila(destino, "Paz");
        assertThat(enOrigen.get("diasEsperados").asInt()).as("lunes antes del traslado").isEqualTo(2);
        assertThat(enOrigen.get("puntual").asInt()).isEqualTo(2);
        assertThat(enDestino.get("diasEsperados").asInt()).as("desde el traslado").isEqualTo(lunes.size() - 2);
        assertThat(enDestino.get("puntual").asInt()).isEqualTo(lunes.size() - 2);
        assertThat(enOrigen.get("porcentaje").asString()).isEqualTo("100.00");
        assertThat(enDestino.get("porcentaje").asString()).isEqualTo("100.00");
        // Los alumnos que no se movieron siguen contando todos los lunes
        assertThat(fila(origen, "Alva").get("diasEsperados").asInt()).isEqualTo(lunes.size());
        assertThat(fila(destino, "Ramos").get("diasEsperados").asInt()).isEqualTo(lunes.size());
    }

    @Test
    void elTrasladoQuedaEnElHistorialDeLaMatriculaConSuFechaYLaSeccionAnteriorDesdeLaMatricula() {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT seccion_id, desde FROM matricula.historial_seccion "
                + "WHERE matricula_id = ? ORDER BY desde, id", matriculaPedro);

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0).get("seccion_id")).isEqualTo(e.seccion3A());
        assertThat(filas.get(1).get("seccion_id")).isEqualTo(e.seccion3B());
        assertThat(filas.get(1).get("desde").toString()).isEqualTo(lunes.get(2).toString());
    }

    @Test
    void elReporteGeneralNoCuentaDosVecesUnMismoDia() throws Exception {
        JsonNode todas = reporte("ALUMNOS", null);

        int dias = 0;
        for (JsonNode f : todas.get("filas")) {
            if (f.get("nombre").asString().contains("Paz")) {
                dias += f.get("diasEsperados").asInt();
            }
        }
        assertThat(dias).as("2 días en 3° A + el resto en 3° B = todos los lunes").isEqualTo(lunes.size());
    }
}
