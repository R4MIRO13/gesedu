package pe.edu.gesedu.asistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
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
import pe.edu.gesedu.apoyo.DatosHorario.Escenario;
import pe.edu.gesedu.apoyo.RelojPrueba;
import pe.edu.gesedu.apoyo.RelojPruebaConfig;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaProperties;

/**
 * UC-07, Registrar asistencia de docente (RF-13, RF-18 a RF-20): la hora sale del reloj del servidor (aquí, fijo y en la
 * zona de Lima), la puntualidad se mide contra la primera clase del docente ese día, y hay una sola asistencia por
 * docente y fecha, también ante solicitudes simultáneas.
 * <p>
 * Escenario: un año ACTIVO propio (solo puede haber uno: la prueba lo activa y lo devuelve a PLANIFICADO al terminar) con
 * horario publicado. Rosa dicta los lunes desde las 8:30 (el primer bloque del día es el de 7:45, que es de Carmen);
 * Carlos solo dicta los martes; Fernando no tiene clases. Cada prueba usa un lunes distinto para no cruzarse en la
 * unicidad docente-fecha.
 */
@Import(RelojPruebaConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MarcacionDocenteIT extends BaseIT {

    private static final String RUTA = "/api/v1/asistencia-docente";

    @Autowired
    private RelojPrueba reloj;

    @Autowired
    private AsistenciaProperties propiedades;

    private Escenario e;
    private String rosa;
    private String carmen;
    private String carlos;
    private String fernando;
    private String horarioId;
    private final Map<String, String> tokens = new HashMap<>();

    @BeforeAll
    void escenario() {
        // Solo puede haber un año ACTIVO: se aparta cualquiera que haya dejado otra prueba
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE estado = 'ACTIVO'");
        e = new DatosHorario(jdbc).crearEscenario();
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        String[][] bimestres = {{"1", "02-15", "04-30"}, {"2", "05-01", "07-15"}, {"3", "07-16", "09-30"}, {"4", "10-01", "12-20"}};
        for (String[] b : bimestres) {
            jdbc.update("INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin) "
                    + "VALUES (?, ?, ?, ?::date, ?::date)", UUID.randomUUID().toString(), e.anioId(), Integer.parseInt(b[0]),
                    e.anio() + "-" + b[1], e.anio() + "-" + b[2]);
        }
        String lunes830 = bloque("LUNES", "08:30", "09:15");
        String lunes915 = bloque("LUNES", "09:15", "10:00");

        DatosHorario datos = new DatosHorario(jdbc);
        rosa = e.rosa();
        carmen = e.carmen();
        carlos = e.carlos();
        fernando = datos.docente("Fernando", "Sin Clases");

        String admin = idDe(crearUsuario("ADMINISTRADOR", true));
        horarioId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO horarios.horario_publicado (id, anio_escolar_id, numero_version, fecha_hora_publicacion, "
                + "vigente, publicado_por) VALUES (?, ?, 1, now(), TRUE, ?)", horarioId, e.anioId(), admin);
        clase(lunes830, e.seccion3A(), rosa);
        clase(lunes915, e.seccion4B(), rosa);
        clase(e.lunes745(), e.seccion3A(), carmen);
        clase(e.martes915(), e.seccion3A(), carlos);
    }

    @AfterAll
    void devolverElAnio() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
    }

    // ---- datos ----

    private String bloque(String dia, String inicio, String fin) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO horarios.bloque_horario (id, anio_escolar_id, dia_semana, hora_inicio, hora_fin) "
                + "VALUES (?, ?, ?, ?::time, ?::time)", id, e.anioId(), dia, inicio, fin);
        return id;
    }

    private void clase(String bloqueId, String seccionId, String docenteId) {
        jdbc.update("INSERT INTO horarios.asignacion_publicada (id, horario_publicado_id, bloque_id, seccion_id, curso_id, "
                + "docente_id) VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), horarioId, bloqueId, seccionId,
                e.matematica(), docenteId);
    }

    /** El n-ésimo lunes de marzo del año de la prueba (n = 1…): cada prueba usa uno distinto. */
    private LocalDate lunes(int n) {
        return LocalDate.of(e.anio(), 3, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).plusWeeks(n - 1L);
    }

    private String token(String docenteId) throws Exception {
        String cacheado = tokens.get(docenteId);
        if (cacheado != null) {
            return cacheado;
        }
        String usuario = jdbc.queryForObject("SELECT usuario_id FROM seguridad.docente WHERE id = ?", String.class, docenteId);
        String correo = "marca-" + usuario.substring(0, 8) + "@test.gesedu.pe";
        jdbc.update("UPDATE seguridad.usuario SET correo = ?, hash_contrasena = ?, activo = TRUE WHERE id = ?", correo,
                codificador.encode(CONTRASENA), usuario);
        invalidarCuentas();
        String token = tokenDe(correo);
        tokens.put(docenteId, token);
        return token;
    }

    private Respuesta marcar(String token) throws Exception {
        return enviar("POST", RUTA + "/marcaciones", null, token, null);
    }

    private int registros(String docenteId, LocalDate fecha) {
        return jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_docente WHERE docente_id = ? AND fecha = ?",
                Integer.class, docenteId, java.sql.Date.valueOf(fecha));
    }

    private static Instant instante(Respuesta r, String campo) {
        return OffsetDateTime.parse(r.cuerpo().get(campo).asString()).toInstant();
    }

    private Instant local(LocalDate fecha, String hora) {
        return fecha.atTime(LocalTime.parse(hora)).atZone(reloj.getZone()).toInstant();
    }

    // ---- flujo básico: pasos 1 a 8 ----

    @Test
    void marcarConHoraDelServidorGuardaElRegistroYSuEstado() throws Exception {
        LocalDate dia = lunes(1);
        reloj.fijar(dia, "08:20:10");

        Respuesta r = marcar(token(rosa));

        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("PUNTUAL");
        assertThat(r.cuerpo().get("fecha").asString()).isEqualTo(dia.toString());
        assertThat(r.cuerpo().get("horaRegistro").asString()).endsWith("-05:00");
        assertThat(instante(r, "horaRegistro")).isEqualTo(local(dia, "08:20:10"));
        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM asistencia.asistencia_docente WHERE docente_id = ? AND fecha = ?",
                rosa, java.sql.Date.valueOf(dia));
        assertThat(fila.get("estado")).isEqualTo("PUNTUAL");
        assertThat(fila.get("hora_registro")).isNotNull();
    }

    @Test
    void laHoraDelDispositivoNoSeUsa() throws Exception {
        LocalDate dia = lunes(2);
        reloj.fijar(dia, "08:20");

        // Aunque la petición traiga una hora y una fecha propias, el servidor usa la suya
        Respuesta r = enviar("POST", RUTA + "/marcaciones", "{\"horaRegistro\":\"2000-01-01T00:00:00-05:00\",\"fecha\":\"2000-01-01\"}",
                token(rosa), null);

        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("fecha").asString()).isEqualTo(dia.toString());
        assertThat(instante(r, "horaRegistro")).isEqualTo(local(dia, "08:20"));
    }

    // ---- paso 6: límite inclusivo sobre el primer bloque DEL DOCENTE ----

    @Test
    void elLimiteEsLaPrimeraClaseDelDocenteMasLaToleranciaYEsInclusivo() throws Exception {
        int tolerancia = propiedades.teacherLateThresholdMinutes();
        LocalTime limite = LocalTime.of(8, 30).plusMinutes(tolerancia);

        reloj.fijar(lunes(3), limite.toString());
        assertThat(marcar(token(rosa)).cuerpo().get("estado").asString()).as("en el límite exacto").isEqualTo("PUNTUAL");

        reloj.fijar(lunes(4), limite.plusSeconds(1).toString());
        assertThat(marcar(token(rosa)).cuerpo().get("estado").asString()).as("un segundo después").isEqualTo("TARDANZA");
    }

    @Test
    void elLimiteSeCalculaConElPrimerBloqueDelDocenteNoConElPrimeroDelDia() throws Exception {
        LocalDate dia = lunes(5);
        reloj.fijar(dia, "08:00:00");

        // Carmen empieza a las 7:45 (límite 7:50): a las 8:00 llega tarde. Rosa empieza a las 8:30: a las 8:00 es puntual.
        assertThat(marcar(token(carmen)).cuerpo().get("estado").asString()).isEqualTo("TARDANZA");
        assertThat(marcar(token(rosa)).cuerpo().get("estado").asString()).isEqualTo("PUNTUAL");
    }

    @Test
    void laZonaHorariaEsLaInstitucionalYNoUtc() throws Exception {
        LocalDate dia = lunes(6);
        // 13:35 UTC = 08:35 en Lima = límite de Rosa (8:30 + 5): puntual. Con la hora UTC sería tardanza.
        reloj.fijar(local(dia, "08:35:00"));

        Respuesta r = marcar(token(rosa));

        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("PUNTUAL");
        assertThat(r.cuerpo().get("fecha").asString()).isEqualTo(dia.toString());
    }

    @Test
    void despuesDeLaMedianocheUtcSigueSiendoElMismoDiaEnLima() throws Exception {
        LocalDate dia = lunes(7);
        // El lunes a las 21:30 de Lima ya es martes en UTC: debe tratarse como lunes (y, a esa hora, jornada cerrada)
        reloj.fijar(local(dia, "21:30:00"));

        Respuesta r = marcar(token(rosa));

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("JORNADA_CERRADA");
    }

    // ---- FA-1: asistencia existente ----

    @Test
    void fa1UnSegundoMarcadoMuestraElRegistroExistenteYNoSobrescribe() throws Exception {
        LocalDate dia = lunes(8);
        reloj.fijar(dia, "08:20");
        Respuesta primera = marcar(token(rosa));
        reloj.fijar(dia, "10:00");

        Respuesta segunda = marcar(token(rosa));

        assertThat(segunda.estado()).isEqualTo(409);
        assertThat(segunda.cuerpo().get("codigo").asString()).isEqualTo("ASISTENCIA_YA_REGISTRADA");
        assertThat(segunda.cuerpo().get("registro").get("id").asString()).isEqualTo(primera.cuerpo().get("id").asString());
        assertThat(segunda.cuerpo().get("registro").get("estado").asString()).isEqualTo("PUNTUAL");
        assertThat(OffsetDateTime.parse(segunda.cuerpo().get("registro").get("horaRegistro").asString()).toInstant())
                .as("la hora del primer registro no cambia").isEqualTo(local(dia, "08:20"));
        assertThat(registros(rosa, dia)).isEqualTo(1);
    }

    @Test
    void fa1SolicitudesSimultaneasDejanUnSoloRegistro() throws Exception {
        LocalDate dia = lunes(9);
        reloj.fijar(dia, "08:20");
        String token = token(rosa);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Respuesta>> tareas = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tareas.add(() -> marcar(token));
            }
            List<Future<Respuesta>> resultados = pool.invokeAll(tareas);
            int creadas = 0;
            int conflictos = 0;
            for (Future<Respuesta> f : resultados) {
                Respuesta r = f.get();
                if (r.estado() == 201) {
                    creadas++;
                } else {
                    assertThat(r.estado()).isEqualTo(409);
                    assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("ASISTENCIA_YA_REGISTRADA");
                    assertThat(r.cuerpo().get("registro")).isNotNull();
                    conflictos++;
                }
            }
            assertThat(creadas).isEqualTo(1);
            assertThat(conflictos).isEqualTo(7);
        } finally {
            pool.shutdownNow();
        }
        assertThat(registros(rosa, dia)).isEqualTo(1);
    }

    // ---- FA-2: sin clases o sin horario publicado ----

    @Test
    void fa2UnDocenteSinClasesEseDiaNoRegistraAsistencia() throws Exception {
        LocalDate dia = lunes(10);
        reloj.fijar(dia, "08:00");

        for (String sinClases : new String[] {carlos, fernando}) {
            Respuesta r = marcar(token(sinClases));
            assertThat(r.estado()).as(sinClases).isEqualTo(422);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("SIN_CLASES_PROGRAMADAS");
            assertThat(registros(sinClases, dia)).isZero();
        }
    }

    @Test
    void fa2SinHorarioPublicadoNoSeRegistraAsistencia() throws Exception {
        LocalDate dia = lunes(11);
        reloj.fijar(dia, "08:00");
        String token = token(rosa);
        // Retirar la versión vigente es parte normal de publicar otra: aquí se deja el año sin versión vigente un momento
        jdbc.update("UPDATE horarios.horario_publicado SET vigente = FALSE WHERE id = ?", horarioId);
        try {
            Respuesta r = marcar(token);

            assertThat(r.estado()).isEqualTo(422);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("SIN_CLASES_PROGRAMADAS");
            assertThat(r.cuerpo().get("detail").asString()).contains("horario publicado");
            assertThat(registros(rosa, dia)).isZero();
        } finally {
            jdbc.update("UPDATE horarios.horario_publicado SET vigente = TRUE WHERE id = ?", horarioId);
        }
    }

    // ---- FA-3: día no lectivo o jornada cerrada ----

    @Test
    void fa3UnDiaSinBloquesNoEsLectivo() throws Exception {
        LocalDate sabado = lunes(12).with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        reloj.fijar(sabado, "08:00");

        Respuesta r = marcar(token(rosa));

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("DIA_NO_LECTIVO");
        assertThat(registros(rosa, sabado)).isZero();
    }

    @Test
    void fa3UnaFechaFueraDeLosBimestresNoEsLectiva() throws Exception {
        // Un lunes de enero: el año empieza el 15 de febrero
        LocalDate enero = LocalDate.of(e.anio(), 1, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        reloj.fijar(enero, "08:00");

        Respuesta r = marcar(token(rosa));

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("DIA_NO_LECTIVO");
        assertThat(registros(rosa, enero)).isZero();
    }

    @Test
    void fa3LaJornadaCierraEnElCierreExactoYAntesSiSePuedeMarcar() throws Exception {
        LocalTime cierre = propiedades.schoolDayClosingTime();

        reloj.fijar(lunes(13), cierre.minusSeconds(1).toString());
        Respuesta antes = marcar(token(rosa));
        assertThat(antes.estado()).isEqualTo(201);
        assertThat(antes.cuerpo().get("estado").asString()).isEqualTo("TARDANZA");

        LocalDate otro = lunes(14);
        reloj.fijar(otro, cierre.toString());
        Respuesta cerrada = marcar(token(rosa));
        assertThat(cerrada.estado()).isEqualTo(422);
        assertThat(cerrada.cuerpo().get("codigo").asString()).isEqualTo("JORNADA_CERRADA");
        assertThat(registros(rosa, otro)).isZero();
    }

    // ---- «Mi asistencia»: hoy y mi historial ----

    @Test
    void hoyMuestraLaHoraDelServidorLaPrimeraClaseYElLimite() throws Exception {
        LocalDate dia = lunes(15);
        reloj.fijar(dia, "08:12:30");

        Respuesta r = get(RUTA + "/hoy", token(rosa));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("fecha").asString()).isEqualTo(dia.toString());
        assertThat(instante(r, "horaServidor")).isEqualTo(local(dia, "08:12:30"));
        assertThat(LocalTime.parse(r.cuerpo().get("primerBloque").asString())).isEqualTo(LocalTime.of(8, 30));
        assertThat(LocalTime.parse(r.cuerpo().get("limitePuntual").asString()))
                .isEqualTo(LocalTime.of(8, 30).plusMinutes(propiedades.teacherLateThresholdMinutes()));
        assertThat(r.cuerpo().get("puedeMarcar").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("motivo").isNull()).isTrue();
        assertThat(r.cuerpo().get("registro").isNull()).isTrue();
    }

    @Test
    void hoyDespuesDeMarcarYEnDiaNoLectivoExplicaPorQueNoSePuedeMarcar() throws Exception {
        LocalDate dia = lunes(16);
        reloj.fijar(dia, "08:20");
        marcar(token(rosa));

        Respuesta ya = get(RUTA + "/hoy", token(rosa));
        assertThat(ya.cuerpo().get("puedeMarcar").asBoolean()).isFalse();
        assertThat(ya.cuerpo().get("motivo").asString()).isEqualTo("ASISTENCIA_YA_REGISTRADA");
        assertThat(ya.cuerpo().get("registro").get("estado").asString()).isEqualTo("PUNTUAL");

        reloj.fijar(lunes(16).with(TemporalAdjusters.next(DayOfWeek.SUNDAY)), "08:00");
        Respuesta domingo = get(RUTA + "/hoy", token(rosa));
        assertThat(domingo.cuerpo().get("puedeMarcar").asBoolean()).isFalse();
        assertThat(domingo.cuerpo().get("motivo").asString()).isEqualTo("DIA_NO_LECTIVO");
        assertThat(domingo.cuerpo().get("mensaje").asString()).isNotBlank();
    }

    @Test
    void miHistorialListaMisRegistrosDelMasRecienteAlMasAntiguoYValidaElRango() throws Exception {
        LocalDate a = lunes(17);
        LocalDate b = lunes(18);
        reloj.fijar(a, "08:20");
        marcar(token(carmen));
        reloj.fijar(b, "08:20");
        marcar(token(carmen));

        Respuesta r = get(RUTA + "/mias?desde=" + a + "&hasta=" + b, token(carmen));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().size()).isEqualTo(2);
        assertThat(r.cuerpo().get(0).get("fecha").asString()).isEqualTo(b.toString());
        assertThat(r.cuerpo().get(1).get("fecha").asString()).isEqualTo(a.toString());
        assertThat(get(RUTA + "/mias?desde=" + b + "&hasta=" + a, token(carmen)).estado()).isEqualTo(400);
        assertThat(get(RUTA + "/mias?desde=" + a.minusYears(3) + "&hasta=" + b, token(carmen)).estado()).isEqualTo(400);
    }

    // ---- permisos y alcance ----

    @Test
    void soloUnDocenteConElPermisoMarca() throws Exception {
        reloj.fijar(lunes(19), "08:20");
        String alumno = tokenDe(crearUsuario("ALUMNO", true));
        String administrador = tokenDe(crearUsuario("ADMINISTRADOR", true));

        assertThat(enviar("POST", RUTA + "/marcaciones", null, null, null).estado()).isEqualTo(401);
        assertThat(marcar(alumno).estado()).isEqualTo(403);
        assertThat(marcar(administrador).estado()).isEqualTo(403);
        assertThat(get(RUTA + "/hoy", alumno).estado()).isEqualTo(403);
        assertThat(get(RUTA + "/mias", administrador).estado()).isEqualTo(403);
    }

    @Test
    void unaCuentaDeDocenteSinPerfilNoPuedeMarcar() throws Exception {
        reloj.fijar(lunes(20), "08:20");
        String sinPerfil = tokenDe(crearUsuario("DOCENTE", true));

        Respuesta r = marcar(sinPerfil);

        assertThat(r.estado()).isEqualTo(403);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
    }
}
