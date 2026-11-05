package pe.edu.gesedu.asistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
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
import pe.edu.gesedu.asistencia.aplicacion.CierreDiarioService;
import pe.edu.gesedu.asistencia.aplicacion.CierreDiarioService.Resultado;
import pe.edu.gesedu.asistencia.aplicacion.CierreDiarioService.ResultadoCierre;
import pe.edu.gesedu.asistencia.aplicacion.JobCierreDiario;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaProperties;

/**
 * UC-12, Registrar faltas de docentes (RF-13, RF-19, RF-20; R-09): al cierre de la jornada, FALTA para los docentes con
 * clases que no marcaron; idempotente, con reintentos y ejecuciones paralelas, y sin inventar faltas si falta el horario
 * o el calendario. Reloj fijo en la zona de Lima y el cron del job apagado: cada prueba lo invoca a mano.
 * <p>
 * Escenario: año ACTIVO propio con horario publicado. Rosa y Carmen dictan los lunes; Carlos solo los martes; Fernando
 * no dicta. Cada prueba usa un lunes distinto para no cruzarse en la unicidad docente-fecha ni en {@code cierre_diario}.
 */
@Import(RelojPruebaConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CierreDiarioIT extends BaseIT {

    @Autowired
    private RelojPrueba reloj;

    @Autowired
    private AsistenciaProperties propiedades;

    @Autowired
    private CierreDiarioService cierre;

    @Autowired
    private JobCierreDiario job;

    @Autowired
    private LockingTaskExecutor ejecutor;

    private Escenario e;
    private String rosa;
    private String carmen;
    private String carlos;
    private String fernando;
    private String horarioId;

    @BeforeAll
    void escenario() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE estado = 'ACTIVO'");
        DatosHorario datos = new DatosHorario(jdbc);
        e = datos.crearEscenario();
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        String[][] bimestres = {{"1", "02-15", "04-30"}, {"2", "05-01", "07-15"}, {"3", "07-16", "09-30"}, {"4", "10-01", "12-20"}};
        for (String[] b : bimestres) {
            jdbc.update("INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin) "
                    + "VALUES (?, ?, ?, ?::date, ?::date)", UUID.randomUUID().toString(), e.anioId(), Integer.parseInt(b[0]),
                    e.anio() + "-" + b[1], e.anio() + "-" + b[2]);
        }
        rosa = e.rosa();
        carmen = e.carmen();
        carlos = e.carlos();
        fernando = datos.docente("Fernando", "Sin Clases");
        String admin = idDe(crearUsuario("ADMINISTRADOR", true));
        horarioId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO horarios.horario_publicado (id, anio_escolar_id, numero_version, fecha_hora_publicacion, "
                + "vigente, publicado_por) VALUES (?, ?, 1, now(), TRUE, ?)", horarioId, e.anioId(), admin);
        String lunes830 = bloque("LUNES", "08:30", "09:15");
        clase(e.lunes745(), e.seccion3A(), carmen);
        clase(lunes830, e.seccion3A(), rosa);
        clase(e.martes915(), e.seccion3A(), carlos);
    }

    @AfterAll
    void devolverElAnio() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
    }

    // ---- ayudas ----

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

    private LocalDate lunes(int n) {
        return LocalDate.of(e.anio(), 3, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).plusWeeks(n - 1L);
    }

    private void alCierre(LocalDate dia) {
        reloj.fijar(dia, propiedades.schoolDayClosingTime().toString());
    }

    private Map<String, Object> asistencia(String docenteId, LocalDate fecha) {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT * FROM asistencia.asistencia_docente WHERE docente_id = ? AND fecha = ?",
                docenteId, Date.valueOf(fecha));
        return filas.isEmpty() ? null : filas.getFirst();
    }

    private int registrosDelDia(LocalDate fecha) {
        return jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_docente WHERE fecha = ? AND docente_id IN (?, ?, ?, ?)",
                Integer.class, Date.valueOf(fecha), rosa, carmen, carlos, fernando);
    }

    private List<Map<String, Object>> cierres(LocalDate fecha) {
        return jdbc.queryForList("SELECT * FROM asistencia.cierre_diario WHERE fecha = ?", Date.valueOf(fecha));
    }

    private void marcarPuntual(String docenteId, LocalDate fecha, String hora) {
        jdbc.update("INSERT INTO asistencia.asistencia_docente (id, docente_id, fecha, hora_registro, estado) VALUES (?, ?, ?, ?, 'PUNTUAL')",
                UUID.randomUUID().toString(), docenteId, Date.valueOf(fecha),
                java.sql.Timestamp.from(fecha.atTime(java.time.LocalTime.parse(hora)).atZone(reloj.getZone()).toInstant()));
    }

    private int auditorias(String descripcionFecha) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'CERRAR_ASISTENCIA_DOCENTE' "
                + "AND fecha_hora > ?", Integer.class, java.sql.Timestamp.from(Instant.now().minus(Duration.ofHours(1))));
    }

    // ---- flujo básico: pasos 1 a 6 ----

    @Test
    void creaFaltaSoloParaLosDocentesConClasesQueNoMarcaron() throws Exception {
        LocalDate dia = lunes(1);
        marcarPuntual(carmen, dia, "07:44:00");
        alCierre(dia);

        ResultadoCierre r = cierre.procesar(dia);

        assertThat(r.resultado()).isEqualTo(Resultado.COMPLETADO);
        assertThat(r.faltasCreadas()).as("solo Rosa: Carmen marcó").isEqualTo(1);
        assertThat(r.docentesConClases()).isGreaterThanOrEqualTo(2);
        Map<String, Object> falta = asistencia(rosa, dia);
        assertThat(falta).isNotNull();
        assertThat(falta.get("estado")).isEqualTo("FALTA");
        assertThat(falta.get("hora_registro")).as("la falta del cierre no lleva hora de marcación").isNull();
        assertThat(asistencia(carmen, dia).get("estado")).as("la marcación existente no se toca").isEqualTo("PUNTUAL");
        assertThat(asistencia(carmen, dia).get("hora_registro")).isNotNull();
        assertThat(asistencia(carlos, dia)).as("Carlos no dicta los lunes").isNull();
        assertThat(asistencia(fernando, dia)).as("Fernando no tiene clases").isNull();
        assertThat(cierres(dia)).hasSize(1);
        assertThat(cierres(dia).getFirst().get("estado")).isEqualTo("EXITOSO");
        assertThat(cierres(dia).getFirst().get("detalle").toString()).contains("1 falta creada");
    }

    @Test
    void nuncaCreaRegistrosDeAlumnos() throws Exception {
        LocalDate dia = lunes(2);
        int antes = jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_alumno", Integer.class);
        alCierre(dia);

        cierre.procesar(dia);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_alumno", Integer.class)).isEqualTo(antes);
    }

    // ---- FA-2 y FA-3: registros existentes, reejecución y concurrencia ----

    @Test
    void fa2SiTodosYaTienenAsistenciaNoCreaNingunRegistro() throws Exception {
        LocalDate dia = lunes(3);
        marcarPuntual(carmen, dia, "07:44:00");
        marcarPuntual(rosa, dia, "08:31:00");
        alCierre(dia);

        ResultadoCierre r = cierre.procesar(dia);

        assertThat(r.resultado()).isEqualTo(Resultado.COMPLETADO);
        assertThat(r.faltasCreadas()).isZero();
        assertThat(registrosDelDia(dia)).isEqualTo(2);
        assertThat(asistencia(rosa, dia).get("estado")).isEqualTo("PUNTUAL");
    }

    @Test
    void fa3EjecutarloDosVecesElMismoDiaNoDuplicaFaltasNiTrabajo() throws Exception {
        LocalDate dia = lunes(4);
        alCierre(dia);

        ResultadoCierre primera = cierre.procesar(dia);
        ResultadoCierre segunda = cierre.procesar(dia);

        assertThat(primera.resultado()).isEqualTo(Resultado.COMPLETADO);
        assertThat(primera.faltasCreadas()).isEqualTo(2);
        assertThat(segunda.resultado()).isEqualTo(Resultado.YA_PROCESADO);
        assertThat(segunda.faltasCreadas()).isZero();
        assertThat(registrosDelDia(dia)).isEqualTo(2);
        assertThat(cierres(dia)).hasSize(1);
    }

    @Test
    void fa3UnaMarcacionTardiaDespuesDelCierreNoSeSobrescribeAlReintentar() throws Exception {
        LocalDate dia = lunes(5);
        alCierre(dia);
        cierre.procesar(dia);
        // Una corrección posterior a TARDANZA... el reintento (otro job, otra instancia) no debe pisarla
        jdbc.update("DELETE FROM asistencia.cierre_diario WHERE fecha = ?", Date.valueOf(dia));
        jdbc.update("UPDATE asistencia.asistencia_docente SET estado = 'PUNTUAL', hora_registro = ? WHERE docente_id = ? AND fecha = ?",
                java.sql.Timestamp.from(dia.atTime(8, 40).atZone(reloj.getZone()).toInstant()), rosa, Date.valueOf(dia));

        ResultadoCierre r = cierre.procesar(dia);

        assertThat(r.faltasCreadas()).isZero();
        assertThat(asistencia(rosa, dia).get("estado")).isEqualTo("PUNTUAL");
        assertThat(asistencia(rosa, dia).get("hora_registro")).isNotNull();
    }

    @Test
    void fa3EjecucionesParalelasDejanUnSoloRegistroPorDocenteYUnaSolaConstancia() throws Exception {
        LocalDate dia = lunes(6);
        alCierre(dia);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        int creadas = 0;
        try {
            List<Callable<ResultadoCierre>> tareas = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tareas.add(() -> cierre.procesar(dia));
            }
            for (Future<ResultadoCierre> f : pool.invokeAll(tareas)) {
                creadas += f.get().faltasCreadas();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(creadas).as("entre todas las ejecuciones se crearon exactamente 2 faltas").isEqualTo(2);
        assertThat(registrosDelDia(dia)).isEqualTo(2);
        assertThat(cierres(dia)).hasSize(1);
    }

    // ---- la jornada debe haber cerrado ----

    @Test
    void antesDelCierreNoHaceNadaNiAnotaNada() throws Exception {
        LocalDate dia = lunes(7);
        reloj.fijar(dia, propiedades.schoolDayClosingTime().minusSeconds(1).toString());

        ResultadoCierre r = cierre.procesar(dia);

        assertThat(r.resultado()).isEqualTo(Resultado.JORNADA_ABIERTA);
        assertThat(registrosDelDia(dia)).isZero();
        assertThat(cierres(dia)).isEmpty();
    }

    @Test
    void enElCierreExactoYaActuaYUnaFechaFuturaNo() throws Exception {
        LocalDate dia = lunes(8);
        alCierre(dia);

        LocalDate futura = lunes(30);
        assertThat(cierre.procesar(dia).resultado()).isEqualTo(Resultado.COMPLETADO);
        assertThat(cierre.procesar(futura).resultado()).isEqualTo(Resultado.JORNADA_ABIERTA);
        assertThat(cierres(futura)).isEmpty();
    }

    @Test
    void elJobActuaConLaHoraDelServidorYLaZonaDeLima() throws Exception {
        LocalDate dia = lunes(9);
        // 20:30 UTC = 15:30 en Lima: ya es el cierre. Con la hora UTC sería de noche, pero igual de lunes.
        reloj.fijar(dia.atTime(15, 30).atZone(reloj.getZone()).toInstant());

        job.ejecutar();

        assertThat(registrosDelDia(dia)).isEqualTo(2);
        assertThat(cierres(dia)).hasSize(1);
        // Dos minutos después, otro disparo del job no repite nada
        job.ejecutar();
        assertThat(registrosDelDia(dia)).isEqualTo(2);
        assertThat(cierres(dia)).hasSize(1);
    }

    @Test
    void elJobAntesDelCierreNoHaceNada() throws Exception {
        LocalDate dia = lunes(10);
        reloj.fijar(dia, "15:29:00");

        job.ejecutar();

        assertThat(registrosDelDia(dia)).isZero();
        assertThat(cierres(dia)).isEmpty();
    }

    // ---- FA-1: día no lectivo ----

    @Test
    void fa1UnDiaSinJornadaTerminaSinCrearRegistros() throws Exception {
        LocalDate sabado = lunes(11).with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        alCierre(sabado);

        ResultadoCierre r = cierre.procesar(sabado);

        assertThat(r.resultado()).isEqualTo(Resultado.DIA_NO_LECTIVO);
        assertThat(registrosDelDia(sabado)).isZero();
        assertThat(cierres(sabado).getFirst().get("estado")).isEqualTo("EXITOSO");
    }

    @Test
    void fa1UnaFechaFueraDeLosBimestresNoEsLectiva() throws Exception {
        LocalDate enero = LocalDate.of(e.anio(), 1, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        alCierre(enero);

        ResultadoCierre r = cierre.procesar(enero);

        assertThat(r.resultado()).isEqualTo(Resultado.DIA_NO_LECTIVO);
        assertThat(registrosDelDia(enero)).isZero();
    }

    // ---- FA-4: horario o calendario no disponible ----

    @Test
    void fa4SinHorarioPublicadoRegistraElFalloYNoInventaFaltasYLuegoSePuedeReintentar() throws Exception {
        LocalDate dia = lunes(12);
        alCierre(dia);
        jdbc.update("UPDATE horarios.horario_publicado SET vigente = FALSE WHERE id = ?", horarioId);
        try {
            ResultadoCierre fallido = cierre.procesar(dia);

            assertThat(fallido.resultado()).isEqualTo(Resultado.FALLIDO);
            assertThat(fallido.detalle()).contains("No hay un horario publicado");
            assertThat(registrosDelDia(dia)).isZero();
            assertThat(cierres(dia).getFirst().get("estado")).isEqualTo("FALLIDO");
            // El siguiente disparo vuelve a intentar y vuelve a anotar el fallo, sin duplicar la constancia
            assertThat(cierre.procesar(dia).resultado()).isEqualTo(Resultado.FALLIDO);
            assertThat(cierres(dia)).hasSize(1);
        } finally {
            jdbc.update("UPDATE horarios.horario_publicado SET vigente = TRUE WHERE id = ?", horarioId);
        }

        // Atendido el problema (horario publicado), el reintento completa el cierre y reemplaza el fallo
        ResultadoCierre reintento = cierre.procesar(dia);

        assertThat(reintento.resultado()).isEqualTo(Resultado.COMPLETADO);
        assertThat(reintento.faltasCreadas()).isEqualTo(2);
        assertThat(cierres(dia)).hasSize(1);
        assertThat(cierres(dia).getFirst().get("estado")).isEqualTo("EXITOSO");
    }

    @Test
    void fa4SinAnioActivoNoHayCalendarioYSeRegistraElFallo() throws Exception {
        LocalDate dia = lunes(13);
        alCierre(dia);
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
        try {
            ResultadoCierre r = cierre.procesar(dia);

            assertThat(r.resultado()).isEqualTo(Resultado.FALLIDO);
            assertThat(r.detalle()).contains("No hay un año escolar activo");
            assertThat(registrosDelDia(dia)).isZero();
        } finally {
            jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        }
    }

    // ---- auditoría como «Sistema» ----

    @Test
    void soloSeAuditaComoSistemaCuandoSeCreanFaltas() throws Exception {
        LocalDate conFaltas = lunes(14);
        alCierre(conFaltas);
        int antes = auditorias("");

        cierre.procesar(conFaltas);
        int trasCrear = auditorias("");
        cierre.procesar(conFaltas);
        LocalDate sinFaltas = lunes(15);
        marcarPuntual(rosa, sinFaltas, "08:30:00");
        marcarPuntual(carmen, sinFaltas, "07:45:00");
        alCierre(sinFaltas);
        cierre.procesar(sinFaltas);

        assertThat(trasCrear - antes).isEqualTo(1);
        assertThat(auditorias("")).as("ni la repetición ni el cierre sin faltas dejan rastro").isEqualTo(trasCrear);
        Map<String, Object> registro = jdbc.queryForMap("SELECT * FROM auditoria.registro_auditoria WHERE accion = "
                + "'CERRAR_ASISTENCIA_DOCENTE' ORDER BY fecha_hora DESC LIMIT 1");
        assertThat(registro.get("usuario_id")).as("actor «Sistema»").isNull();
        assertThat(registro.get("modulo")).isEqualTo("ASISTENCIA");
    }

    // ---- ShedLock: un disparo duplicado no ejecuta ----

    @Test
    void dosInstanciasSimuladasElDisparoDuplicadoNoEjecuta() throws Exception {
        LocalDate dia = lunes(16);
        alCierre(dia);
        CountDownLatch primeraAdentro = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(1);
        try {
            // «Instancia 1» está ejecutando el cierre: tiene el bloqueo del mismo nombre
            Future<?> primera = hilos.submit(() -> ejecutor.executeWithLock((Runnable) () -> {
                primeraAdentro.countDown();
                try {
                    liberar.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }, new LockConfiguration(Instant.now(), JobCierreDiario.BLOQUEO, Duration.ofMinutes(1), Duration.ZERO)));
            assertThat(primeraAdentro.await(10, TimeUnit.SECONDS)).isTrue();

            // «Instancia 2» dispara el job real mientras la otra lo ejecuta: se omite y no crea nada
            job.ejecutar();
            assertThat(registrosDelDia(dia)).as("el disparo duplicado no ejecutó").isZero();

            liberar.countDown();
            primera.get(10, TimeUnit.SECONDS);
        } finally {
            liberar.countDown();
            hilos.shutdownNow();
        }

        // Liberado el bloqueo, el siguiente disparo sí procesa
        job.ejecutar();
        assertThat(registrosDelDia(dia)).isEqualTo(2);
    }

    // ---- consulta del administrador: asistencia docente por día ----

    private static final String RUTA_DIA = "/api/v1/asistencia-docente";

    private tools.jackson.databind.JsonNode fila(Respuesta r, String docenteId) {
        for (tools.jackson.databind.JsonNode f : r.cuerpo().get("docentes")) {
            if (docenteId.equals(f.get("docenteId").asString())) {
                return f;
            }
        }
        return null;
    }

    @Test
    void laConsultaListaALosProgramadosConSuEstadoYLasFaltasGeneradasAlCierre() throws Exception {
        LocalDate dia = lunes(31);
        marcarPuntual(carmen, dia, "07:44:00");
        String admin = tokenDe(crearUsuario("ADMINISTRADOR", true));
        reloj.fijar(dia, "10:00");

        Respuesta antes = get(RUTA_DIA + "?fecha=" + dia, admin);

        assertThat(antes.estado()).isEqualTo(200);
        assertThat(antes.cuerpo().get("diaLectivo").asBoolean()).isTrue();
        assertThat(antes.cuerpo().get("cierre").asString()).isEqualTo("PENDIENTE");
        assertThat(fila(antes, carmen).get("estado").asString()).isEqualTo("PUNTUAL");
        assertThat(fila(antes, carmen).get("horaRegistro").asString()).endsWith("-05:00");
        assertThat(LocalTimeDe(fila(antes, carmen).get("primerBloque").asString())).isEqualTo(java.time.LocalTime.of(7, 45));
        assertThat(fila(antes, rosa).get("estado").isNull()).as("programada sin registro: no es falta").isTrue();
        assertThat(fila(antes, carlos)).as("Carlos no dicta los lunes").isNull();
        assertThat(antes.cuerpo().get("totales").get("sinRegistrar").asInt()).isGreaterThanOrEqualTo(1);

        alCierre(dia);
        cierre.procesar(dia);
        Respuesta despues = get(RUTA_DIA + "?fecha=" + dia, admin);

        assertThat(despues.cuerpo().get("cierre").asString()).isEqualTo("PROCESADO");
        assertThat(despues.cuerpo().get("detalleCierre").asString()).contains("1 falta creada");
        assertThat(fila(despues, rosa).get("estado").asString()).isEqualTo("FALTA");
        assertThat(fila(despues, rosa).get("generadaAlCierre").asBoolean()).isTrue();
        assertThat(fila(despues, rosa).get("horaRegistro").isNull()).isTrue();
        assertThat(fila(despues, carmen).get("generadaAlCierre").asBoolean()).isFalse();
        assertThat(despues.cuerpo().get("totales").get("puntual").asInt()).isEqualTo(1);
        assertThat(despues.cuerpo().get("totales").get("falta").asInt()).isEqualTo(1);
        assertThat(despues.cuerpo().get("totales").get("sinRegistrar").asInt()).isZero();
    }

    @Test
    void laConsultaSinFechaEsLaDeHoyYSaleEnOrdenAlfabetico() throws Exception {
        LocalDate dia = lunes(32);
        reloj.fijar(dia, "10:00");
        String admin = tokenDe(crearUsuario("ADMINISTRADOR", true));

        Respuesta r = get(RUTA_DIA, admin);

        assertThat(r.cuerpo().get("fecha").asString()).isEqualTo(dia.toString());
        List<String> nombres = new ArrayList<>();
        r.cuerpo().get("docentes").forEach(f -> nombres.add(f.get("nombre").asString()));
        assertThat(nombres).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    void unDiaNoLectivoNoTieneDocentesProgramadosYUnaFechaFuturaSeRechaza() throws Exception {
        LocalDate dia = lunes(33);
        reloj.fijar(dia, "10:00");
        String admin = tokenDe(crearUsuario("ADMINISTRADOR", true));

        Respuesta sabado = get(RUTA_DIA + "?fecha=" + dia.with(TemporalAdjusters.previous(DayOfWeek.SATURDAY)), admin);
        Respuesta futura = get(RUTA_DIA + "?fecha=" + dia.plusDays(1), admin);

        assertThat(sabado.estado()).isEqualTo(200);
        assertThat(sabado.cuerpo().get("diaLectivo").asBoolean()).isFalse();
        assertThat(sabado.cuerpo().get("docentes").size()).isZero();
        assertThat(futura.estado()).isEqualTo(400);
    }

    @Test
    void laConsultaMuestraUnCierreFallidoParaAtenderlo() throws Exception {
        LocalDate dia = lunes(34);
        alCierre(dia);
        String admin = tokenDe(crearUsuario("ADMINISTRADOR", true));
        jdbc.update("UPDATE horarios.horario_publicado SET vigente = FALSE WHERE id = ?", horarioId);
        try {
            cierre.procesar(dia);
            Respuesta r = get(RUTA_DIA + "?fecha=" + dia, admin);

            assertThat(r.cuerpo().get("cierre").asString()).isEqualTo("FALLIDO");
            assertThat(r.cuerpo().get("detalleCierre").asString()).contains("No hay un horario publicado");
            assertThat(r.cuerpo().get("totales").get("falta").asInt()).isZero();
        } finally {
            jdbc.update("UPDATE horarios.horario_publicado SET vigente = TRUE WHERE id = ?", horarioId);
        }
    }

    @Test
    void soloElAdministradorConsultaLaAsistenciaDeLosDocentes() throws Exception {
        reloj.fijar(lunes(35), "10:00");

        assertThat(get(RUTA_DIA, null).estado()).isEqualTo(401);
        assertThat(get(RUTA_DIA, tokenDe(crearUsuario("DOCENTE", true))).estado()).isEqualTo(403);
        assertThat(get(RUTA_DIA, tokenDe(crearUsuario("ALUMNO", true))).estado()).isEqualTo(403);
        assertThat(get(RUTA_DIA, tokenDe(crearUsuario("APODERADO", true))).estado()).isEqualTo(403);
        assertThat(get(RUTA_DIA, tokenDe(crearUsuario("ADMINISTRADOR", true))).estado()).isEqualTo(200);
    }

    private static java.time.LocalTime LocalTimeDe(String texto) {
        return java.time.LocalTime.parse(texto);
    }
}
