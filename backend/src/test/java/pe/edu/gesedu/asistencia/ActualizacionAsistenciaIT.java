package pe.edu.gesedu.asistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
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
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaProperties;

/**
 * UC-09, Actualizar estado de asistencia (RF-15): el tutor cambia de FALTA a TARDANZA a un alumno de su sección que se
 * incorpora tarde, hasta StudentLateCutoffTime y con la jornada abierta; se modifica el mismo registro y el cambio queda
 * en la auditoría. Reloj fijo en la zona de Lima.
 * <p>
 * Escenario: año ACTIVO propio con jornada los lunes; 3° A (tutora Rosa) con dos alumnos; 3° B (tutor Luis) con uno.
 * Cada prueba usa un lunes distinto para tener su propia asistencia del alumno.
 */
@Import(RelojPruebaConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActualizacionAsistenciaIT extends BaseIT {

    private static final String RUTA = "/api/v1/asistencia-alumnos/";
    private static final String CAMBIO = "{\"estado\":\"TARDANZA\"}";

    @Autowired
    private RelojPrueba reloj;

    @Autowired
    private AsistenciaProperties propiedades;

    private Escenario e;
    private String rosa;
    private String luis;
    private String alumnoDeRosa;
    private String otroDeRosa;
    private String alumnoDeLuis;
    private final Map<String, String> tokens = new HashMap<>();

    @BeforeAll
    void escenario() {
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
        jdbc.update("INSERT INTO horarios.bloque_horario (id, anio_escolar_id, dia_semana, hora_inicio, hora_fin) "
                + "VALUES (?, ?, 'LUNES', '07:45', '08:30')", UUID.randomUUID().toString(), e.anioId());
        DatosHorario docentes = new DatosHorario(jdbc);
        rosa = docentes.docente("Rosa", "Quispe");
        luis = docentes.docente("Luis", "Castro");
        jdbc.update("UPDATE academico.seccion SET tutor_docente_id = ? WHERE id = ?", rosa, e.seccion3A());
        jdbc.update("UPDATE academico.seccion SET tutor_docente_id = ? WHERE id = ?", luis, e.seccion3B());
        alumnoDeRosa = alumno(e.seccion3A());
        otroDeRosa = alumno(e.seccion3A());
        alumnoDeLuis = alumno(e.seccion3B());
    }

    @AfterAll
    void devolverElAnio() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
    }

    // ---- ayudas ----

    private String alumno(String seccionId) {
        DatosMatricula datos = new DatosMatricula(jdbc);
        DatosMatricula.Solicitud s = datos.solicitud(e, false);
        datos.matricularYa(e, s, seccionId);
        return s.alumnoId();
    }

    private LocalDate lunes(int n) {
        return LocalDate.of(e.anio(), 3, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).plusWeeks(n - 1L);
    }

    private String token(String docenteId) throws Exception {
        String cacheado = tokens.get(docenteId);
        if (cacheado != null) {
            return cacheado;
        }
        String usuario = jdbc.queryForObject("SELECT usuario_id FROM seguridad.docente WHERE id = ?", String.class, docenteId);
        String correo = "cambio-" + usuario.substring(0, 8) + "@test.gesedu.pe";
        jdbc.update("UPDATE seguridad.usuario SET correo = ?, hash_contrasena = ?, activo = TRUE WHERE id = ?", correo,
                codificador.encode(CONTRASENA), usuario);
        invalidarCuentas();
        String token = tokenDe(correo);
        tokens.put(docenteId, token);
        return token;
    }

    /** Crea la asistencia del alumno en la fecha con el estado dado y devuelve el id del registro. */
    private String registro(String alumnoId, LocalDate fecha, String estado) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO asistencia.asistencia_alumno (id, alumno_id, fecha, estado) VALUES (?, ?, ?, ?)", id, alumnoId,
                Date.valueOf(fecha), estado);
        return id;
    }

    private Respuesta cambiar(String id, String token, String cuerpo) throws Exception {
        return enviar("PATCH", RUTA + id, cuerpo, token, null);
    }

    private String estadoDe(String id) {
        return jdbc.queryForObject("SELECT estado FROM asistencia.asistencia_alumno WHERE id = ?", String.class, id);
    }

    private int auditorias(String id) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = "
                + "'CAMBIAR_ASISTENCIA_A_TARDANZA' AND id_entidad_afectada = ?", Integer.class, id);
    }

    // ---- flujo básico: pasos 1 a 5 ----

    @Test
    void elTutorCambiaDeFaltaATardanzaElMismoRegistroYSuUpdatedAt() throws Exception {
        LocalDate dia = lunes(1);
        String id = registro(alumnoDeRosa, dia, "FALTA");
        Instant antes = jdbc.queryForObject("SELECT updated_at FROM asistencia.asistencia_alumno WHERE id = ?",
                java.sql.Timestamp.class, id).toInstant();
        reloj.fijar(dia, "08:10:00");

        Respuesta r = cambiar(id, token(rosa), CAMBIO);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("id").asString()).as("el mismo registro").isEqualTo(id);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("TARDANZA");
        assertThat(r.cuerpo().get("modificado").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("actualizadoEn").asString()).endsWith("-05:00");
        assertThat(estadoDe(id)).isEqualTo("TARDANZA");
        Instant despues = jdbc.queryForObject("SELECT updated_at FROM asistencia.asistencia_alumno WHERE id = ?",
                java.sql.Timestamp.class, id).toInstant();
        assertThat(despues).as("updated_at refleja el cambio con la hora del servidor")
                .isEqualTo(dia.atTime(LocalTime.of(8, 10)).atZone(reloj.getZone()).toInstant()).isNotEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_alumno WHERE alumno_id = ? AND fecha = ?",
                Integer.class, alumnoDeRosa, Date.valueOf(dia))).as("no se crea otro registro").isEqualTo(1);
    }

    @Test
    void elCambioQuedaEnLaAuditoriaConElEstadoAnteriorYElNuevo() throws Exception {
        LocalDate dia = lunes(2);
        String id = registro(alumnoDeRosa, dia, "FALTA");
        reloj.fijar(dia, "08:10");
        String usuarioTutor = jdbc.queryForObject("SELECT usuario_id FROM seguridad.docente WHERE id = ?", String.class, rosa);

        assertThat(cambiar(id, token(rosa), CAMBIO).estado()).isEqualTo(200);

        Map<String, Object> registroAuditoria = jdbc.queryForMap("SELECT * FROM auditoria.registro_auditoria WHERE accion = "
                + "'CAMBIAR_ASISTENCIA_A_TARDANZA' AND id_entidad_afectada = ?", id);
        assertThat(registroAuditoria.get("modulo")).isEqualTo("ASISTENCIA");
        assertThat(registroAuditoria.get("tipo_entidad")).isEqualTo("AsistenciaAlumno");
        assertThat(registroAuditoria.get("usuario_id")).isEqualTo(usuarioTutor);
        Map<String, Object> cambio = jdbc.queryForMap("SELECT * FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ?",
                registroAuditoria.get("id"));
        assertThat(cambio.get("campo")).isEqualTo("estado");
        assertThat(cambio.get("valor_anterior")).isEqualTo("FALTA");
        assertThat(cambio.get("valor_nuevo")).isEqualTo("TARDANZA");
    }

    // ---- FA-1: plazo excedido o jornada cerrada ----

    @Test
    void fa1ElLimiteEsInclusivoYUnSegundoDespuesElAlumnoSigueEnFalta() throws Exception {
        LocalTime limite = propiedades.studentLateCutoffTime();
        LocalDate enElLimite = lunes(3);
        String enTiempo = registro(alumnoDeRosa, enElLimite, "FALTA");
        reloj.fijar(enElLimite, limite.toString());
        assertThat(cambiar(enTiempo, token(rosa), CAMBIO).estado()).as("en el límite exacto").isEqualTo(200);

        LocalDate tarde = lunes(4);
        String fuera = registro(alumnoDeRosa, tarde, "FALTA");
        reloj.fijar(tarde, limite.plusSeconds(1).toString());
        Respuesta r = cambiar(fuera, token(rosa), CAMBIO);

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("PLAZO_TARDANZA_EXCEDIDO");
        assertThat(estadoDe(fuera)).isEqualTo("FALTA");
        assertThat(auditorias(fuera)).as("lo que no cambia no se audita").isZero();
    }

    @Test
    void fa1ConLaJornadaCerradaNoSeAdmiteElCambio() throws Exception {
        LocalDate dia = lunes(5);
        String id = registro(alumnoDeRosa, dia, "FALTA");
        reloj.fijar(dia, propiedades.schoolDayClosingTime().toString());

        Respuesta r = cambiar(id, token(rosa), CAMBIO);

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("PLAZO_TARDANZA_EXCEDIDO");
        assertThat(estadoDe(id)).isEqualTo("FALTA");
    }

    // ---- FA-2: registro no elegible ----

    @Test
    void fa2UnaAsistenciaPuntualNoSeModifica() throws Exception {
        LocalDate dia = lunes(6);
        String id = registro(alumnoDeRosa, dia, "PUNTUAL");
        reloj.fijar(dia, "08:10");

        Respuesta r = cambiar(id, token(rosa), CAMBIO);

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("SOLICITUD_INVALIDA");
        assertThat(estadoDe(id)).isEqualTo("PUNTUAL");
    }

    @Test
    void fa2UnRegistroQueYaEsTardanzaMuestraElResultadoExistenteSinCambiarNada() throws Exception {
        LocalDate dia = lunes(7);
        String id = registro(alumnoDeRosa, dia, "TARDANZA");
        reloj.fijar(dia, "08:10");

        Respuesta r = cambiar(id, token(rosa), CAMBIO);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("TARDANZA");
        assertThat(r.cuerpo().get("modificado").asBoolean()).isFalse();
        assertThat(auditorias(id)).isZero();
    }

    @Test
    void fa2UnRegistroDeOtraFechaNoSeModifica() throws Exception {
        LocalDate ayer = lunes(8);
        String id = registro(alumnoDeRosa, ayer, "FALTA");
        reloj.fijar(ayer.plusWeeks(1), "08:10");

        Respuesta r = cambiar(id, token(rosa), CAMBIO);

        assertThat(r.estado()).isEqualTo(400);
        assertThat(estadoDe(id)).isEqualTo("FALTA");
    }

    @Test
    void soloSeAdmiteElCambioATardanza() throws Exception {
        LocalDate dia = lunes(9);
        String id = registro(alumnoDeRosa, dia, "FALTA");
        reloj.fijar(dia, "08:10");

        for (String cuerpo : new String[] {"{\"estado\":\"PUNTUAL\"}", "{\"estado\":\"FALTA\"}", "{\"estado\":\"AUSENTE\"}", "{}"}) {
            assertThat(cambiar(id, token(rosa), cuerpo).estado()).as(cuerpo).isEqualTo(400);
        }
        assertThat(estadoDe(id)).isEqualTo("FALTA");
    }

    // ---- FA-3: alumno fuera de la sección del tutor ----

    @Test
    void fa3ElTutorDeOtraSeccionNoPuedeCambiarlo() throws Exception {
        LocalDate dia = lunes(10);
        String id = registro(alumnoDeRosa, dia, "FALTA");
        String deLuis = registro(alumnoDeLuis, dia, "FALTA");
        reloj.fijar(dia, "08:10");

        Respuesta intruso = cambiar(id, token(luis), CAMBIO);
        Respuesta aRevesRosa = cambiar(deLuis, token(rosa), CAMBIO);

        for (Respuesta r : new Respuesta[] {intruso, aRevesRosa}) {
            assertThat(r.estado()).isEqualTo(403);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
        }
        assertThat(estadoDe(id)).isEqualTo("FALTA");
        assertThat(estadoDe(deLuis)).isEqualTo("FALTA");
    }

    @Test
    void unRegistroQueNoExisteSeTrataComoFueraDeAlcance() throws Exception {
        reloj.fijar(lunes(11), "08:10");

        assertThat(cambiar(UUID.randomUUID().toString(), token(rosa), CAMBIO).estado()).isEqualTo(403);
    }

    @Test
    void unDiaNoLectivoNoPermiteElCambio() throws Exception {
        LocalDate dia = lunes(12);
        String id = registro(alumnoDeRosa, dia, "FALTA");
        reloj.fijar(dia.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)), "08:10");

        Respuesta r = cambiar(id, token(rosa), CAMBIO);

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("DIA_NO_LECTIVO");
        assertThat(estadoDe(id)).isEqualTo("FALTA");
    }

    // ---- solicitudes simultáneas ----

    @Test
    void variosCambiosSimultaneosDejanUnSoloCambioYUnaSolaAuditoria() throws Exception {
        LocalDate dia = lunes(13);
        String id = registro(alumnoDeRosa, dia, "FALTA");
        reloj.fijar(dia, "08:10");
        String token = token(rosa);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        int modificados = 0;
        try {
            List<Callable<Respuesta>> tareas = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                tareas.add(() -> cambiar(id, token, CAMBIO));
            }
            for (Future<Respuesta> f : pool.invokeAll(tareas)) {
                Respuesta r = f.get();
                assertThat(r.estado()).isEqualTo(200);
                assertThat(r.cuerpo().get("estado").asString()).isEqualTo("TARDANZA");
                if (r.cuerpo().get("modificado").asBoolean()) {
                    modificados++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(modificados).isEqualTo(1);
        assertThat(estadoDe(id)).isEqualTo("TARDANZA");
        assertThat(auditorias(id)).isEqualTo(1);
    }

    // ---- la vista del día informa si todavía se puede cambiar ----

    @Test
    void laConsultaDelDiaDiceSiAunSePuedeActualizarATardanza() throws Exception {
        LocalDate dia = lunes(14);
        LocalTime limite = propiedades.studentLateCutoffTime();

        reloj.fijar(dia, limite.toString());
        assertThat(get("/api/v1/secciones/3A/asistencia", token(rosa)).cuerpo().get("actualizacionTardanzaHabilitada").asBoolean()).isTrue();
        reloj.fijar(dia, limite.plusSeconds(1).toString());
        assertThat(get("/api/v1/secciones/3A/asistencia", token(rosa)).cuerpo().get("actualizacionTardanzaHabilitada").asBoolean()).isFalse();
        reloj.fijar(dia, propiedades.schoolDayClosingTime().toString());
        assertThat(get("/api/v1/secciones/3A/asistencia", token(rosa)).cuerpo().get("actualizacionTardanzaHabilitada").asBoolean()).isFalse();
    }

    // ---- permisos ----

    @Test
    void soloUnTutorConElPermisoPuedeCambiar() throws Exception {
        LocalDate dia = lunes(15);
        String id = registro(otroDeRosa, dia, "FALTA");
        reloj.fijar(dia, "08:10");
        String alumnoToken = tokenDe(crearUsuario("ALUMNO", true));
        String administrador = tokenDe(crearUsuario("ADMINISTRADOR", true));

        assertThat(cambiar(id, null, CAMBIO).estado()).isEqualTo(401);
        assertThat(cambiar(id, alumnoToken, CAMBIO).estado()).isEqualTo(403);
        assertThat(cambiar(id, administrador, CAMBIO).estado()).isEqualTo(403);
        assertThat(estadoDe(id)).isEqualTo("FALTA");
    }
}
