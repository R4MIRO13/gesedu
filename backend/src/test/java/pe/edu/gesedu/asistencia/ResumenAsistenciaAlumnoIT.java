package pe.edu.gesedu.asistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosMatricula;
import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;
import pe.edu.gesedu.apoyo.RelojPrueba;
import pe.edu.gesedu.apoyo.RelojPruebaConfig;

/**
 * Resumen de asistencia del propio alumno para su inicio (RF-16 bajo demanda): mismos conteos, días no registrados y
 * porcentaje que el reporte (UC-11), solo para el alumno que lo pide. Reloj fijo en la zona de Lima.
 * <p>
 * Escenario: año ACTIVO con jornada los lunes y miércoles. Lucía (con cuenta) tiene en marzo 3 puntuales, 1 tardanza y
 * 1 falta; Mateo (con cuenta) no tiene registros.
 */
@Import(RelojPruebaConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResumenAsistenciaAlumnoIT extends BaseIT {

    @Autowired
    private RelojPrueba reloj;

    private Escenario e;
    private String lucia;
    private String mateo;
    private String tokenLucia;
    private String tokenMateo;
    private List<LocalDate> lectivos;

    @BeforeAll
    void escenario() throws Exception {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE estado = 'ACTIVO'");
        DatosMatricula datos = new DatosMatricula(jdbc);
        e = datos.crearEscenario(30);
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        String[][] bimestres = {{"1", "02-15", "04-30"}, {"2", "05-01", "07-15"}, {"3", "07-16", "09-30"}, {"4", "10-01", "12-20"}};
        for (String[] b : bimestres) {
            jdbc.update("INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin) "
                    + "VALUES (?, ?, ?, ?::date, ?::date)", UUID.randomUUID().toString(), e.anioId(), Integer.parseInt(b[0]),
                    e.anio() + "-" + b[1], e.anio() + "-" + b[2]);
        }
        for (String dia : new String[] {"LUNES", "MIERCOLES"}) {
            jdbc.update("INSERT INTO horarios.bloque_horario (id, anio_escolar_id, dia_semana, hora_inicio, hora_fin) "
                    + "VALUES (?, ?, ?, '07:45', '08:30')", UUID.randomUUID().toString(), e.anioId(), dia);
        }
        lucia = conCuenta(datos.solicitud(e, false).alumnoId());
        mateo = conCuenta(datos.solicitud(e, false).alumnoId());
        tokenLucia = tokenDeAlumno(lucia);
        tokenMateo = tokenDeAlumno(mateo);

        lectivos = new ArrayList<>();
        for (LocalDate d = LocalDate.of(e.anio(), 3, 1); d.getMonthValue() == 3; d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.MONDAY || d.getDayOfWeek() == DayOfWeek.WEDNESDAY) {
                lectivos.add(d);
            }
        }
        for (int i = 0; i < 5; i++) {
            registro(lucia, lectivos.get(i), i < 3 ? "PUNTUAL" : i == 3 ? "TARDANZA" : "FALTA");
        }
    }

    @AfterAll
    void devolverElAnio() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
    }

    private String conCuenta(String alumnoId) {
        String usuario = jdbc.queryForObject("SELECT usuario_id FROM seguridad.alumno WHERE id = ?", String.class, alumnoId);
        jdbc.update("UPDATE seguridad.usuario SET correo = ?, hash_contrasena = ?, activo = TRUE WHERE id = ?",
                "alumno-" + usuario.substring(0, 8) + "@test.gesedu.pe", codificador.encode(CONTRASENA), usuario);
        invalidarCuentas();
        return alumnoId;
    }

    private String tokenDeAlumno(String alumnoId) throws Exception {
        String correo = jdbc.queryForObject("SELECT u.correo::text FROM seguridad.usuario u JOIN seguridad.alumno a ON a.usuario_id = u.id "
                + "WHERE a.id = ?", String.class, alumnoId);
        return tokenDe(correo);
    }

    private void registro(String alumnoId, LocalDate fecha, String estado) {
        jdbc.update("INSERT INTO asistencia.asistencia_alumno (id, alumno_id, fecha, estado) VALUES (?, ?, ?, ?)",
                UUID.randomUUID().toString(), alumnoId, Date.valueOf(fecha), estado);
    }

    private String ruta(String alumnoId, String mes) {
        return "/api/v1/alumnos/" + alumnoId + "/asistencia/resumen" + (mes == null ? "" : "?mes=" + mes);
    }

    private String marzo() {
        return "%d-03".formatted(e.anio());
    }

    @Test
    void elAlumnoVeSusConteosSusDiasNoRegistradosYSuPorcentaje() throws Exception {
        reloj.fijar(LocalDate.of(e.anio(), 4, 10), "10:00");

        Respuesta r = get(ruta(lucia, marzo()), tokenLucia);

        assertThat(r.estado()).isEqualTo(200);
        JsonNode c = r.cuerpo();
        assertThat(c.get("alumnoId").asString()).isEqualTo(lucia);
        assertThat(c.get("anio").asInt()).isEqualTo(e.anio());
        assertThat(c.get("mes").asInt()).isEqualTo(3);
        assertThat(c.get("puntual").asInt()).isEqualTo(3);
        assertThat(c.get("tardanza").asInt()).isEqualTo(1);
        assertThat(c.get("falta").asInt()).isEqualTo(1);
        assertThat(c.get("registrados").asInt()).isEqualTo(5);
        assertThat(c.get("diasEsperados").asInt()).isEqualTo(lectivos.size());
        assertThat(c.get("noRegistrados").asInt()).isEqualTo(lectivos.size() - 5);
        assertThat(c.get("porcentaje").asString()).as("100 × (3 + 1) / 5").isEqualTo("80.00");
        assertThat(c.get("parcial").asBoolean()).isFalse();
    }

    @Test
    void sinRegistrosNoEsUnErrorYElPorcentajeNoEstaDisponible() throws Exception {
        reloj.fijar(LocalDate.of(e.anio(), 4, 10), "10:00");

        JsonNode c = get(ruta(mateo, marzo()), tokenMateo).cuerpo();

        assertThat(c.get("registrados").asInt()).isZero();
        assertThat(c.get("falta").asInt()).as("sin registro no es falta").isZero();
        assertThat(c.get("porcentaje").isNull()).isTrue();
        assertThat(c.get("noRegistrados").asInt()).isEqualTo(lectivos.size());
    }

    @Test
    void sinMesUsaElMesActualDelServidorYElMesEnCursoEsParcial() throws Exception {
        LocalDate hoy = lectivos.get(3);
        reloj.fijar(hoy, "10:00");

        JsonNode c = get(ruta(lucia, null), tokenLucia).cuerpo();

        assertThat(c.get("mes").asInt()).isEqualTo(3);
        assertThat(c.get("parcial").asBoolean()).isTrue();
        assertThat(c.get("fechaCorte").asString()).isEqualTo(hoy.minusDays(1).toString());
        assertThat(c.get("registrados").asInt()).as("la tardanza de hoy aún no cuenta (jornada abierta)").isEqualTo(3);
    }

    @Test
    void unAlumnoNoVeLaAsistenciaDeOtroNiUnApoderadoLaDeSusHijos() throws Exception {
        reloj.fijar(LocalDate.of(e.anio(), 4, 10), "10:00");

        Respuesta ajeno = get(ruta(lucia, marzo()), tokenMateo);

        assertThat(ajeno.estado()).isEqualTo(403);
        assertThat(ajeno.cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
        assertThat(get(ruta(lucia, marzo()), tokenDe(crearUsuario("APODERADO", true))).estado()).isEqualTo(403);
        assertThat(get(ruta(lucia, marzo()), tokenDe(crearUsuario("ADMINISTRADOR", true))).estado()).isEqualTo(403);
        assertThat(get(ruta(lucia, marzo()), null).estado()).isEqualTo(401);
        assertThat(get(ruta(UUID.randomUUID().toString(), marzo()), tokenLucia).estado()).isEqualTo(403);
    }

    @Test
    void unMesFuturoUnFormatoInvalidoYLaFaltaDeAnioActivoSeInforman() throws Exception {
        reloj.fijar(LocalDate.of(e.anio(), 4, 10), "10:00");

        Respuesta futuro = get(ruta(lucia, "%d-05".formatted(e.anio())), tokenLucia);
        assertThat(futuro.estado()).isEqualTo(422);
        assertThat(futuro.cuerpo().get("codigo").asString()).isEqualTo("PERIODO_INVALIDO");
        assertThat(get(ruta(lucia, "marzo"), tokenLucia).estado()).isEqualTo(400);

        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
        try {
            Respuesta sinAnio = get(ruta(lucia, marzo()), tokenLucia);
            assertThat(sinAnio.estado()).isEqualTo(422);
            assertThat(sinAnio.cuerpo().get("codigo").asString()).isEqualTo("CALENDARIO_NO_DISPONIBLE");
        } finally {
            jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        }
    }

    @Test
    void elResumenCoincideConLaFilaDelReporteDelAdministrador() throws Exception {
        reloj.fijar(LocalDate.of(e.anio(), 4, 10), "10:00");
        // El reporte de alumnos del administrador usa la matrícula de la sección: se matricula a Lucía en 3° A para compararla
        DatosMatricula datos = new DatosMatricula(jdbc);
        DatosMatricula.Solicitud s = datos.solicitud(e, false);
        datos.matricularYa(e, s, e.seccion3A());
        jdbc.update("UPDATE seguridad.usuario SET apellidos = 'Comparada' WHERE id = (SELECT usuario_id FROM seguridad.alumno WHERE id = ?)", s.alumnoId());
        for (int i = 0; i < 5; i++) {
            registro(s.alumnoId(), lectivos.get(i), i < 3 ? "PUNTUAL" : i == 3 ? "TARDANZA" : "FALTA");
        }
        String admin = tokenDe(crearUsuario("ADMINISTRADOR", true));

        JsonNode reporte = get("/api/v1/reportes/asistencia?ambito=ALUMNOS&mes=3&anio=" + e.anio() + "&seccion=3A", admin).cuerpo();
        JsonNode fila = null;
        for (JsonNode f : reporte.get("filas")) {
            if (f.get("nombre").asString().startsWith("Comparada")) {
                fila = f;
            }
        }

        assertThat(fila).isNotNull();
        JsonNode resumen = get(ruta(lucia, marzo()), tokenLucia).cuerpo();
        assertThat(fila.get("porcentaje").asString()).isEqualTo(resumen.get("porcentaje").asString());
        assertThat(fila.get("noRegistrados").asInt()).isEqualTo(resumen.get("noRegistrados").asInt());
    }
}
