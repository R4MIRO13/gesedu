package pe.edu.gesedu.pagos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import pe.edu.gesedu.matricula.BaseMatriculaIT;
import pe.edu.gesedu.pagos.aplicacion.JobVencimientos;
import pe.edu.gesedu.pagos.aplicacion.VencimientosService;
import pe.edu.gesedu.pagos.aplicacion.VencimientosService.ResultadoVencimientos;

/** Job «Vencimientos y penalidades» (RF-09, RF-10, S-10): vencimiento, penalidad única, idempotencia y ShedLock. */
class JobVencimientosIT extends BaseMatriculaIT {

    @Autowired
    private VencimientosService vencimientos;

    @Autowired
    private JobVencimientos job;

    @Autowired
    private LockingTaskExecutor ejecutor;

    /** Una fecha anterior a cualquier vencimiento: el proceso no encuentra nada que cambiar. */
    private static final LocalDate ANTES_DE_TODO = LocalDate.of(1999, 1, 1);

    private String estado(String id) {
        return jdbc.queryForObject("SELECT estado FROM pagos.obligacion_pago WHERE id = ?", String.class, id);
    }

    private String penalidad(String id) {
        return jdbc.queryForObject("SELECT monto_penalidad::text FROM pagos.obligacion_pago WHERE id = ?", String.class, id);
    }

    /** Solo las obligaciones de la familia de la prueba: otras pruebas dejan obligaciones propias en la misma base. */
    private long vencidasDe(Familia f) {
        return contar("SELECT count(*) FROM pagos.obligacion_pago WHERE matricula_id = ? AND estado = 'VENCIDA'", f.matriculaId());
    }

    @Test
    void lasObligacionesVencidasPasanAVencidaConSuPenalidadYLasDemasNoCambian() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        String abril = f.pension(datos, 4);
        String mayo = f.pension(datos, 5);
        // Pensiones: vencen el 5 de cada mes del año del escenario. «Hoy» = 20 de abril: marzo y abril ya vencieron
        LocalDate hoy = LocalDate.of(f.escenario().anio(), 4, 20);
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'PAGADA' WHERE id = ?", abril);

        ResultadoVencimientos r = vencimientos.procesar(hoy);

        assertThat(r.obligacionesVencidas()).isGreaterThanOrEqualTo(1);
        assertThat(estado(marzo)).isEqualTo("VENCIDA");
        assertThat(penalidad(marzo)).isEqualTo("17.50");
        assertThat(jdbc.queryForObject("SELECT penalidad_aplicada_en IS NOT NULL FROM pagos.obligacion_pago WHERE id = ?",
                Boolean.class, marzo)).isTrue();
        assertThat(estado(abril)).as("una pagada no se toca").isEqualTo("PAGADA");
        assertThat(penalidad(abril)).isEqualTo("0.00");
        assertThat(estado(mayo)).as("aún no vence").isEqualTo("PENDIENTE");
        assertThat(penalidad(mayo)).isEqualTo("0.00");
        // La matrícula (vence el 28/02) también venció
        assertThat(estado(datos.obligacion(f.matriculaId(), String.valueOf(f.escenario().anio())))).isEqualTo("VENCIDA");
    }

    @Test
    void ejecutarDosVecesDaElMismoResultadoYNoDuplicaLaPenalidad() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        LocalDate hoy = LocalDate.of(f.escenario().anio(), 4, 20);

        vencimientos.procesar(hoy);
        String aplicadaEn = jdbc.queryForObject("SELECT penalidad_aplicada_en::text FROM pagos.obligacion_pago WHERE id = ?",
                String.class, marzo);
        long versionAntes = jdbc.queryForObject("SELECT version FROM pagos.obligacion_pago WHERE id = ?", Long.class, marzo);
        long vencidasAntes = vencidasDe(f);

        ResultadoVencimientos segunda = vencimientos.procesar(hoy);

        assertThat(segunda.obligacionesVencidas()).as("nada pendiente").isZero();
        assertThat(segunda.penalidadesAplicadas()).isZero();
        assertThat(penalidad(marzo)).isEqualTo("17.50");
        assertThat(jdbc.queryForObject("SELECT penalidad_aplicada_en::text FROM pagos.obligacion_pago WHERE id = ?", String.class,
                marzo)).isEqualTo(aplicadaEn);
        assertThat(jdbc.queryForObject("SELECT version FROM pagos.obligacion_pago WHERE id = ?", Long.class, marzo)).isEqualTo(versionAntes);
        assertThat(vencidasDe(f)).isEqualTo(vencidasAntes);

        // Un mes después vence mayo, pero la penalidad de marzo no se recalcula ni cambia su marca
        String mayo = f.pension(datos, 5);
        vencimientos.procesar(hoy.plusMonths(1));
        assertThat(estado(mayo)).isEqualTo("VENCIDA");
        assertThat(penalidad(mayo)).isEqualTo("17.50");
        assertThat(penalidad(marzo)).isEqualTo("17.50");
        assertThat(jdbc.queryForObject("SELECT penalidad_aplicada_en::text FROM pagos.obligacion_pago WHERE id = ?", String.class,
                marzo)).isEqualTo(aplicadaEn);
    }

    @Test
    void unaObligacionVencidaSinPenalidadLaRecibeUnaSolaVez() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        // Quedó VENCIDA por otro camino (por ejemplo, sembrada) sin penalidad aplicada
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'VENCIDA' WHERE id = ?", marzo);

        ResultadoVencimientos r = vencimientos.procesar(LocalDate.of(f.escenario().anio(), 4, 20));

        assertThat(r.penalidadesAplicadas()).isGreaterThanOrEqualTo(1);
        assertThat(penalidad(marzo)).isEqualTo("17.50");
        assertThat(vencimientos.procesar(LocalDate.of(f.escenario().anio(), 4, 20)).penalidadesAplicadas()).isZero();
        assertThat(penalidad(marzo)).isEqualTo("17.50");
    }

    @Test
    void sinPoliticaDeCobroVenceSinPenalizarYLaPenalidadLlegaCuandoSeConfigura() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        jdbc.update("DELETE FROM pagos.politica_cobro WHERE anio_escolar_id = ?", f.escenario().anioId());
        LocalDate hoy = LocalDate.of(f.escenario().anio(), 4, 20);

        vencimientos.procesar(hoy);

        assertThat(estado(marzo)).isEqualTo("VENCIDA");
        assertThat(penalidad(marzo)).isEqualTo("0.00");
        assertThat(jdbc.queryForObject("SELECT penalidad_aplicada_en IS NULL FROM pagos.obligacion_pago WHERE id = ?",
                Boolean.class, marzo)).isTrue();

        jdbc.update("INSERT INTO pagos.politica_cobro (id, anio_escolar_id, porcentaje_penalidad) VALUES (gen_random_uuid()::text, ?, 10.00)",
                f.escenario().anioId());
        vencimientos.procesar(hoy);

        assertThat(penalidad(marzo)).isEqualTo("35.00");
    }

    @Test
    void elPorcentajeDeCadaAnioSeUsaConSuPropioMonto() throws Exception {
        Familia f = crearFamilia();
        jdbc.update("UPDATE pagos.politica_cobro SET porcentaje_penalidad = 7.50 WHERE anio_escolar_id = ?", f.escenario().anioId());
        jdbc.update("UPDATE pagos.obligacion_pago SET monto_base = 333.33 WHERE id = ?", f.pension(datos, 3));

        vencimientos.procesar(LocalDate.of(f.escenario().anio(), 4, 20));

        // 333.33 × 7.5 % = 24.99975 → 25.00
        assertThat(penalidad(f.pension(datos, 3))).isEqualTo("25.00");
    }

    @Test
    void elProcesoQuedaAuditadoComoSistemaSoloSiCambioAlgo() throws Exception {
        Familia f = crearFamilia();
        long antes = contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'APLICAR_VENCIMIENTOS'");

        vencimientos.procesar(LocalDate.of(f.escenario().anio(), 4, 20));
        long despues = contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'APLICAR_VENCIMIENTOS'");
        // Una ejecución sin nada que cambiar no deja registro
        vencimientos.procesar(ANTES_DE_TODO);
        long alFinal = contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'APLICAR_VENCIMIENTOS'");

        assertThat(despues).isEqualTo(antes + 1);
        assertThat(alFinal).isEqualTo(despues);
        assertThat(contar("SELECT count(*) FROM auditoria.cambio_auditoria c JOIN auditoria.registro_auditoria r "
                + "ON r.id = c.registro_auditoria_id WHERE r.accion = 'APLICAR_VENCIMIENTOS' AND c.campo = 'penalidadesAplicadas' "
                + "AND c.valor_nuevo::int > 0")).isGreaterThan(0);
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'APLICAR_VENCIMIENTOS' "
                + "AND usuario_id IS NOT NULL")).as("el actor es «Sistema»").isZero();
    }

    @Test
    void conDosInstanciasSimuladasSoloUnaEjecutaElJob() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        jdbc.update("UPDATE pagos.obligacion_pago SET fecha_vencimiento = DATE '2000-03-05' WHERE id = ?", marzo);
        CountDownLatch primeraAdentro = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(1);
        try {
            // «Instancia 1» está ejecutando el job: tiene el bloqueo del mismo nombre
            Future<?> primera = hilos.submit(() -> ejecutor.executeWithLock((Runnable) () -> {
                primeraAdentro.countDown();
                try {
                    liberar.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, new LockConfiguration(Instant.now(), JobVencimientos.BLOQUEO, Duration.ofMinutes(1), Duration.ZERO)));
            assertThat(primeraAdentro.await(10, TimeUnit.SECONDS)).isTrue();

            // «Instancia 2» dispara el job real mientras la otra lo ejecuta: se omite y no toca nada
            job.ejecutar();
            assertThat(estado(marzo)).as("el disparo duplicado no ejecutó").isEqualTo("PENDIENTE");

            liberar.countDown();
            primera.get(10, TimeUnit.SECONDS);
        } finally {
            liberar.countDown();
            hilos.shutdownNow();
        }

        // Liberado el bloqueo, el siguiente disparo sí procesa
        job.ejecutar();
        assertThat(estado(marzo)).isEqualTo("VENCIDA");
        assertThat(penalidad(marzo)).isEqualTo("17.50");
    }

    @Test
    void unaValidacionDePagoEnCursoNoLaPisaElJob() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        // El pago de marzo se validó antes de que corriera el job: la obligación está PAGADA aunque venció
        String id = jdbc.queryForObject("SELECT gen_random_uuid()::text", String.class);
        jdbc.update("INSERT INTO pagos.comprobante_pago (id, obligacion_id, apoderado_id, numero_operacion, fecha_pago, "
                + "monto_presentado, medio_pago, referencia_comprobante, estado_revision) "
                + "VALUES (?, ?, ?, 'JV-0001', CURRENT_DATE, 350.00, 'BCP', 'comprobantes/prueba.pdf', 'APROBADO')",
                id, marzo, f.apoderado().apoderadoId());
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'PAGADA' WHERE id = ?", marzo);

        vencimientos.procesar(LocalDate.of(f.escenario().anio(), 4, 20));

        assertThat(estado(marzo)).isEqualTo("PAGADA");
        assertThat(penalidad(marzo)).isEqualTo("0.00");
        assertThat(jdbc.queryForObject("SELECT penalidad_aplicada_en IS NULL FROM pagos.obligacion_pago WHERE id = ?",
                Boolean.class, marzo)).isTrue();
    }
}
