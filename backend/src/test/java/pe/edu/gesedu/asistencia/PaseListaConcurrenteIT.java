package pe.edu.gesedu.asistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * UC-08, Registrar asistencia de alumnos (RF-14): el tutor pasa lista de su sección con PUNTUAL, TARDANZA o FALTA, en un
 * lote atómico, con un registro por alumno y fecha aun con solicitudes simultáneas. Reloj fijo en la zona de Lima.
 * <p>
 * Escenario: año ACTIVO propio con jornada los lunes; 3° A (tutora Rosa) con cuatro alumnos matriculados (más una
 * matrícula anulada que no cuenta) y 3° B (tutor Luis) con un alumno; 3° C (tutora Marta) sin alumnos; Carmen no es tutora.
 */
@Import(RelojPruebaConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaseListaConcurrenteIT extends BaseIT {

    @Autowired
    private RelojPrueba reloj;

    @Autowired
    private AsistenciaProperties propiedades;

    private Escenario e;
    private String rosa;
    private String luis;
    private String marta;
    private String carmen;
    private String alva;
    private String bravo;
    private String castro;
    private String diaz;
    private String ajeno;
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
        marta = docentes.docente("Marta", "Ibáñez");
        carmen = docentes.docente("Carmen", "Villanueva");
        jdbc.update("UPDATE academico.seccion SET tutor_docente_id = ? WHERE id = ?", rosa, e.seccion3A());
        jdbc.update("UPDATE academico.seccion SET tutor_docente_id = ? WHERE id = ?", luis, e.seccion3B());
        String seccion3C = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.seccion (id, grado_id, anio_escolar_id, letra, capacidad, vacantes_disponibles, "
                + "tutor_docente_id) VALUES (?, ?, ?, 'C', 30, 30, ?)", seccion3C, e.grado3(), e.anioId(), marta);

        // Se insertan desordenados a propósito: la lista sale en orden alfabético de apellidos
        castro = alumno(e.seccion3A(), "Castro", "Mateo");
        alva = alumno(e.seccion3A(), "Alva", "Lucía");
        diaz = alumno(e.seccion3A(), "Diaz", "Valeria");
        bravo = alumno(e.seccion3A(), "Bravo", "Tomás");
        String anulado = alumno(e.seccion3A(), "Zeta", "Anulado");
        jdbc.update("UPDATE matricula.matricula SET estado = 'ANULADA' WHERE alumno_id = ?", anulado);
        ajeno = alumno(e.seccion3B(), "Ramos", "Pedro");
    }

    @AfterAll
    void devolverElAnio() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
    }

    // ---- datos y ayudas ----

    private String alumno(String seccionId, String apellidos, String nombres) {
        DatosMatricula datos = new DatosMatricula(jdbc);
        DatosMatricula.Solicitud s = datos.solicitud(e, false);
        datos.matricularYa(e, s, seccionId);
        jdbc.update("UPDATE seguridad.usuario SET apellidos = ?, nombres = ? WHERE id = "
                + "(SELECT usuario_id FROM seguridad.alumno WHERE id = ?)", apellidos, nombres, s.alumnoId());
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
        String correo = "lista-" + usuario.substring(0, 8) + "@test.gesedu.pe";
        jdbc.update("UPDATE seguridad.usuario SET correo = ?, hash_contrasena = ?, activo = TRUE WHERE id = ?", correo,
                codificador.encode(CONTRASENA), usuario);
        invalidarCuentas();
        String token = tokenDe(correo);
        tokens.put(docenteId, token);
        return token;
    }

    private static final String RUTA_3A = "/api/v1/secciones/3A/asistencia";

    private String lote(String... alumnoYEstado) throws Exception {
        List<Map<String, String>> items = new ArrayList<>();
        for (int i = 0; i < alumnoYEstado.length; i += 2) {
            items.add(Map.of("alumnoId", alumnoYEstado[i], "estado", alumnoYEstado[i + 1]));
        }
        return json.writeValueAsString(Map.of("asistencias", items));
    }

    private Respuesta registrar(String ruta, String token, String cuerpo) throws Exception {
        return enviar("POST", ruta, cuerpo, token, null);
    }

    private int registros(LocalDate fecha) {
        return jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_alumno WHERE fecha = ? AND alumno_id IN (?, ?, ?, ?, ?)",
                Integer.class, Date.valueOf(fecha), alva, bravo, castro, diaz, ajeno);
    }

    private String estadoDe(String alumnoId, LocalDate fecha) {
        return jdbc.queryForObject("SELECT estado FROM asistencia.asistencia_alumno WHERE alumno_id = ? AND fecha = ?",
                String.class, alumnoId, Date.valueOf(fecha));
    }

    private void yaRegistrado(String alumnoId, LocalDate fecha, String estado) {
        jdbc.update("INSERT INTO asistencia.asistencia_alumno (id, alumno_id, fecha, estado) VALUES (?, ?, ?, ?)",
                UUID.randomUUID().toString(), alumnoId, Date.valueOf(fecha), estado);
    }

    // ---- pasos 1 a 4: la sección y sus alumnos ----

    @Test
    void laSeccionSeMuestraEnOrdenAlfabeticoSinLosMatriculadosAnulados() throws Exception {
        reloj.fijar(lunes(1), "07:50");

        Respuesta r = get(RUTA_3A, token(rosa));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("3A");
        assertThat(r.cuerpo().get("nombre").asString()).isEqualTo("3° A");
        assertThat(r.cuerpo().get("fecha").asString()).isEqualTo(lunes(1).toString());
        assertThat(r.cuerpo().get("registroHabilitado").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("mensaje").isNull()).isTrue();
        assertThat(LocalTime.parse(r.cuerpo().get("limiteTardanza").asString())).isEqualTo(propiedades.studentLateCutoffTime());
        List<String> apellidos = new ArrayList<>();
        r.cuerpo().get("alumnos").forEach(a -> apellidos.add(a.get("apellidos").asString()));
        assertThat(apellidos).containsExactly("Alva", "Bravo", "Castro", "Diaz");
        assertThat(r.cuerpo().get("alumnos").get(0).get("nombres").asString()).isEqualTo("Lucía");
        assertThat(r.cuerpo().get("alumnos").get(0).get("codigo").asString()).isNotBlank();
        assertThat(r.cuerpo().get("alumnos").get(0).get("estado").isNull()).as("sin registro no es falta").isTrue();
        assertThat(r.cuerpo().get("totales").get("pendientes").asInt()).isEqualTo(4);
    }

    @Test
    void miSeccionDevuelveLaSeccionDelTutorYElCodigoSeAceptaEnMinusculas() throws Exception {
        reloj.fijar(lunes(2), "07:50");

        Respuesta mia = get("/api/v1/asistencia-alumnos/mi-seccion", token(rosa));

        assertThat(mia.estado()).isEqualTo(200);
        assertThat(mia.cuerpo().get("codigo").asString()).isEqualTo("3A");
        assertThat(mia.cuerpo().get("alumnos").asInt()).isEqualTo(4);
        assertThat(get("/api/v1/secciones/3a/asistencia", token(rosa)).estado()).isEqualTo(200);
    }

    // ---- pasos 5 a 8: lote atómico con los tres estados ----

    @Test
    void elLoteConLosTresEstadosSeGuardaCompletoYMuestraLosTotales() throws Exception {
        LocalDate dia = lunes(3);
        reloj.fijar(dia, "07:55");

        Respuesta r = registrar(RUTA_3A, token(rosa), lote(alva, "PUNTUAL", bravo, "TARDANZA", castro, "FALTA", diaz, "PUNTUAL"));

        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("totales").get("puntual").asInt()).isEqualTo(2);
        assertThat(r.cuerpo().get("totales").get("tardanza").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get("totales").get("falta").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get("totales").get("pendientes").asInt()).isZero();
        assertThat(estadoDe(alva, dia)).isEqualTo("PUNTUAL");
        assertThat(estadoDe(bravo, dia)).isEqualTo("TARDANZA");
        assertThat(estadoDe(castro, dia)).isEqualTo("FALTA");
        assertThat(estadoDe(diaz, dia)).isEqualTo("PUNTUAL");
        assertThat(registros(dia)).isEqualTo(4);
        // La consulta posterior muestra lo guardado
        Respuesta consulta = get(RUTA_3A, token(rosa));
        assertThat(consulta.cuerpo().get("alumnos").get(1).get("estado").asString()).isEqualTo("TARDANZA");
        assertThat(consulta.cuerpo().get("alumnos").get(1).get("registroId").asString()).isNotBlank();
    }

    @Test
    void unEstadoInvalidoSeRechazaYNoSeGuardaNada() throws Exception {
        LocalDate dia = lunes(4);
        reloj.fijar(dia, "07:55");

        Respuesta r = registrar(RUTA_3A, token(rosa), lote(alva, "PUNTUAL", bravo, "AUSENTE", castro, "FALTA", diaz, "PUNTUAL"));

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
        assertThat(registros(dia)).isZero();
    }

    @Test
    void fa1UnLoteIncompletoSenalaLosPendientesYNoGuardaNada() throws Exception {
        LocalDate dia = lunes(5);
        reloj.fijar(dia, "07:55");

        Respuesta r = registrar(RUTA_3A, token(rosa), lote(alva, "PUNTUAL", bravo, "PUNTUAL"));

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
        assertThat(r.cuerpo().get("detail").asString()).contains("Faltan 2 alumnos");
        Set<String> campos = new HashSet<>();
        r.cuerpo().get("errores").forEach(x -> campos.add(x.get("campo").asString()));
        assertThat(campos).containsExactlyInAnyOrder("asistencias." + castro, "asistencias." + diaz);
        assertThat(registros(dia)).isZero();
    }

    @Test
    void unAlumnoDeOtraSeccionORepetidoSeRechazaYNoSeGuardaNada() throws Exception {
        LocalDate dia = lunes(6);
        reloj.fijar(dia, "07:55");

        Respuesta ajenoEnElLote = registrar(RUTA_3A, token(rosa),
                lote(alva, "PUNTUAL", bravo, "PUNTUAL", castro, "PUNTUAL", diaz, "PUNTUAL", ajeno, "PUNTUAL"));
        Respuesta repetido = registrar(RUTA_3A, token(rosa),
                lote(alva, "PUNTUAL", alva, "FALTA", bravo, "PUNTUAL", castro, "PUNTUAL", diaz, "PUNTUAL"));

        assertThat(ajenoEnElLote.estado()).isEqualTo(400);
        assertThat(ajenoEnElLote.cuerpo().get("errores").get(0).get("campo").asString()).isEqualTo("asistencias." + ajeno);
        assertThat(repetido.estado()).isEqualTo(400);
        assertThat(registros(dia)).isZero();
    }

    @Test
    void unLoteVacioOSinCuerpoValidoSeRechaza() throws Exception {
        reloj.fijar(lunes(7), "07:55");

        assertThat(registrar(RUTA_3A, token(rosa), "{\"asistencias\":[]}").estado()).isEqualTo(400);
        assertThat(registrar(RUTA_3A, token(rosa), "{}").estado()).isEqualTo(400);
    }

    // ---- FA-2: registros existentes ----

    @Test
    void fa2LosYaGuardadosNoSeSobrescribenYSoloSeRegistranLosPendientes() throws Exception {
        LocalDate dia = lunes(8);
        reloj.fijar(dia, "07:55");
        yaRegistrado(alva, dia, "FALTA");

        Respuesta r = registrar(RUTA_3A, token(rosa), lote(bravo, "PUNTUAL", castro, "TARDANZA", diaz, "PUNTUAL"));

        assertThat(r.estado()).isEqualTo(201);
        assertThat(estadoDe(alva, dia)).as("el existente no cambia").isEqualTo("FALTA");
        assertThat(registros(dia)).isEqualTo(4);
        assertThat(r.cuerpo().get("totales").get("falta").asInt()).isEqualTo(1);
    }

    @Test
    void fa2UnLoteQueIncluyeAUnoYaRegistradoNoSobrescribeNiGuardaNadaDelLote() throws Exception {
        LocalDate dia = lunes(9);
        reloj.fijar(dia, "07:55");
        yaRegistrado(alva, dia, "FALTA");

        Respuesta r = registrar(RUTA_3A, token(rosa),
                lote(alva, "PUNTUAL", bravo, "PUNTUAL", castro, "PUNTUAL", diaz, "PUNTUAL"));

        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("ASISTENCIA_YA_REGISTRADA");
        assertThat(r.cuerpo().get("registro").get("alumnos").get(0).get("estado").asString()).isEqualTo("FALTA");
        assertThat(estadoDe(alva, dia)).isEqualTo("FALTA");
        assertThat(registros(dia)).as("nada del lote se guardó").isEqualTo(1);
    }

    @Test
    void conTodosYaRegistradosLaPantallaQuedaEnConsultaSinPendientes() throws Exception {
        LocalDate dia = lunes(10);
        reloj.fijar(dia, "07:55");
        for (String a : new String[] {alva, bravo, castro, diaz}) {
            yaRegistrado(a, dia, "PUNTUAL");
        }

        Respuesta r = get(RUTA_3A, token(rosa));

        assertThat(r.cuerpo().get("totales").get("pendientes").asInt()).isZero();
        assertThat(r.cuerpo().get("totales").get("puntual").asInt()).isEqualTo(4);
    }

    // ---- FA-3: sin alumnos o sin tutoría ----

    @Test
    void fa3UnaSeccionSinAlumnosNoCreaRegistros() throws Exception {
        reloj.fijar(lunes(11), "07:55");

        Respuesta consulta = get("/api/v1/secciones/3C/asistencia", token(marta));
        Respuesta registro = registrar("/api/v1/secciones/3C/asistencia", token(marta), lote(alva, "PUNTUAL"));

        assertThat(consulta.estado()).isEqualTo(200);
        assertThat(consulta.cuerpo().get("alumnos").size()).isZero();
        assertThat(consulta.cuerpo().get("mensaje").asString()).contains("no tiene alumnos");
        assertThat(registro.estado()).isEqualTo(422);
        assertThat(registro.cuerpo().get("codigo").asString()).isEqualTo("SECCION_SIN_ALUMNOS");
        assertThat(registros(lunes(11))).isZero();
    }

    @Test
    void fa3UnDocenteSinTutoriaNoVeNiRegistra() throws Exception {
        reloj.fijar(lunes(12), "07:55");

        Respuesta mia = get("/api/v1/asistencia-alumnos/mi-seccion", token(carmen));
        Respuesta ajena = get(RUTA_3A, token(carmen));
        Respuesta otroTutor = get(RUTA_3A, token(luis));
        Respuesta registro = registrar(RUTA_3A, token(luis), lote(alva, "PUNTUAL", bravo, "PUNTUAL", castro, "PUNTUAL", diaz, "PUNTUAL"));

        for (Respuesta r : new Respuesta[] {mia, ajena, otroTutor, registro}) {
            assertThat(r.estado()).isEqualTo(403);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
        }
        assertThat(registros(lunes(12))).isZero();
    }

    @Test
    void unaSeccionQueNoExisteResponde404() throws Exception {
        reloj.fijar(lunes(13), "07:55");

        assertThat(get("/api/v1/secciones/9Z/asistencia", token(rosa)).estado()).isEqualTo(404);
    }

    // ---- FA-4: día no lectivo o jornada cerrada ----

    @Test
    void fa4DespuesDelCierreElRegistroNoEstaHabilitadoPeroLaConsultaSigue() throws Exception {
        LocalDate dia = lunes(14);
        reloj.fijar(dia, propiedades.schoolDayClosingTime().toString());

        Respuesta registro = registrar(RUTA_3A, token(rosa), lote(alva, "PUNTUAL", bravo, "PUNTUAL", castro, "PUNTUAL", diaz, "PUNTUAL"));
        Respuesta consulta = get(RUTA_3A, token(rosa));

        assertThat(registro.estado()).isEqualTo(422);
        assertThat(registro.cuerpo().get("codigo").asString()).isEqualTo("JORNADA_CERRADA");
        assertThat(registros(dia)).isZero();
        assertThat(consulta.estado()).isEqualTo(200);
        assertThat(consulta.cuerpo().get("registroHabilitado").asBoolean()).isFalse();
        assertThat(consulta.cuerpo().get("mensaje").asString()).contains("ya no está habilitado");
    }

    @Test
    void fa4UnMinutoAntesDelCierreSiSePuedeRegistrar() throws Exception {
        LocalDate dia = lunes(15);
        reloj.fijar(dia, propiedades.schoolDayClosingTime().minusSeconds(1).toString());

        Respuesta r = registrar(RUTA_3A, token(rosa), lote(alva, "PUNTUAL", bravo, "PUNTUAL", castro, "PUNTUAL", diaz, "PUNTUAL"));

        assertThat(r.estado()).isEqualTo(201);
    }

    @Test
    void fa4UnDiaNoLectivoNoHabilitaElRegistro() throws Exception {
        LocalDate sabado = lunes(16).with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        reloj.fijar(sabado, "07:55");

        for (Respuesta r : new Respuesta[] {get(RUTA_3A, token(rosa)), get("/api/v1/asistencia-alumnos/mi-seccion", token(rosa)),
                registrar(RUTA_3A, token(rosa), lote(alva, "PUNTUAL"))}) {
            assertThat(r.estado()).isEqualTo(422);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("DIA_NO_LECTIVO");
        }
        assertThat(registros(sabado)).isZero();
    }

    // ---- FA-5: solicitudes simultáneas ----

    @Test
    void fa5SolicitudesSimultaneasDelMismoLoteDejanUnSoloRegistroPorAlumno() throws Exception {
        LocalDate dia = lunes(17);
        reloj.fijar(dia, "07:55");
        String token = token(rosa);
        String cuerpo = lote(alva, "PUNTUAL", bravo, "TARDANZA", castro, "FALTA", diaz, "PUNTUAL");
        int creadas = 0;
        int conflictos = 0;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Respuesta>> tareas = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tareas.add(() -> registrar(RUTA_3A, token, cuerpo));
            }
            for (Future<Respuesta> f : pool.invokeAll(tareas)) {
                Respuesta r = f.get();
                if (r.estado() == 201) {
                    creadas++;
                } else {
                    assertThat(r.estado()).isEqualTo(409);
                    assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("ASISTENCIA_YA_REGISTRADA");
                    assertThat(r.cuerpo().get("registro").get("alumnos")).isNotNull();
                    conflictos++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(creadas).isEqualTo(1);
        assertThat(conflictos).isEqualTo(7);
        assertThat(registros(dia)).isEqualTo(4);
    }

    @Test
    void fa5DosLotesDistintosSimultaneosNoSeMezclan() throws Exception {
        LocalDate dia = lunes(18);
        reloj.fijar(dia, "07:55");
        String token = token(rosa);
        String todosPuntual = lote(alva, "PUNTUAL", bravo, "PUNTUAL", castro, "PUNTUAL", diaz, "PUNTUAL");
        String todosFalta = lote(alva, "FALTA", bravo, "FALTA", castro, "FALTA", diaz, "FALTA");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Respuesta>> tareas = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String cuerpo = i % 2 == 0 ? todosPuntual : todosFalta;
                tareas.add(() -> registrar(RUTA_3A, token, cuerpo));
            }
            long exitos = pool.invokeAll(tareas).stream().filter(f -> {
                try {
                    return f.get().estado() == 201;
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }).count();
            assertThat(exitos).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        // Atomicidad: ganó un lote completo; nunca una mezcla de los dos
        Set<String> estados = new HashSet<>();
        for (String a : new String[] {alva, bravo, castro, diaz}) {
            estados.add(estadoDe(a, dia));
        }
        assertThat(estados).hasSize(1);
        assertThat(registros(dia)).isEqualTo(4);
    }

    // ---- permisos ----

    @Test
    void soloUnDocenteConElPermisoYLaTutoriaAccede() throws Exception {
        reloj.fijar(lunes(19), "07:55");
        String alumnoToken = tokenDe(crearUsuario("ALUMNO", true));
        String administrador = tokenDe(crearUsuario("ADMINISTRADOR", true));
        String sinPerfil = tokenDe(crearUsuario("DOCENTE", true));

        assertThat(get(RUTA_3A, null).estado()).isEqualTo(401);
        assertThat(registrar(RUTA_3A, null, lote(alva, "PUNTUAL")).estado()).isEqualTo(401);
        for (String t : new String[] {alumnoToken, administrador}) {
            assertThat(get(RUTA_3A, t).estado()).isEqualTo(403);
            assertThat(registrar(RUTA_3A, t, lote(alva, "PUNTUAL")).estado()).isEqualTo(403);
            assertThat(get("/api/v1/asistencia-alumnos/mi-seccion", t).estado()).isEqualTo(403);
        }
        assertThat(get(RUTA_3A, sinPerfil).estado()).isEqualTo(403);
        assertThat(registros(lunes(19))).isZero();
    }
}
