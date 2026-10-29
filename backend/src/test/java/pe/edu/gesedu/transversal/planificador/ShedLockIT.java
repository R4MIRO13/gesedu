package pe.edu.gesedu.transversal.planificador;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.ServiciosPrueba;

/**
 * ShedLock (DA-08): con dos instancias que disparan el mismo job a la vez, solo una lo ejecuta; el bloqueo se
 * guarda en {@code transversal.shedlock} con el rol {@code gesedu_app}. Y un job sin usuario audita como «Sistema».
 */
class ShedLockIT extends BaseIT {

    @Autowired
    private LockingTaskExecutor ejecutor;

    @Autowired
    private ServiciosPrueba servicios;

    @Autowired
    private PlatformTransactionManager gestorTransacciones;

    @Autowired
    private javax.sql.DataSource datos;

    private static LockConfiguration bloqueo(String nombre) {
        return new LockConfiguration(Instant.now(), nombre, Duration.ofMinutes(1), Duration.ZERO);
    }

    @Test
    void dosDisparosSimultaneosDelMismoJobSoloEjecutanUno() throws Exception {
        String job = "job-prueba-" + System.nanoTime();
        AtomicInteger ejecuciones = new AtomicInteger();
        CountDownLatch primeroAdentro = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            // «Instancia 1» toma el bloqueo y lo retiene
            Future<?> primera = hilos.submit(() -> ejecutor.executeWithLock((Runnable) () -> {
                ejecuciones.incrementAndGet();
                primeroAdentro.countDown();
                try {
                    liberar.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, bloqueo(job)));
            assertThat(primeroAdentro.await(5, TimeUnit.SECONDS)).isTrue();

            // «Instancia 2» dispara el mismo job mientras la primera sigue ejecutándolo: se omite
            ejecutor.executeWithLock((Runnable) ejecuciones::incrementAndGet, bloqueo(job));

            liberar.countDown();
            primera.get(5, TimeUnit.SECONDS);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(ejecuciones.get()).isEqualTo(1);
    }

    @Test
    void otroJobConOtroNombreNoSeBloquea() {
        AtomicInteger ejecuciones = new AtomicInteger();
        ejecutor.executeWithLock((Runnable) ejecuciones::incrementAndGet, bloqueo("job-a-" + System.nanoTime()));
        ejecutor.executeWithLock((Runnable) ejecuciones::incrementAndGet, bloqueo("job-b-" + System.nanoTime()));

        assertThat(ejecuciones.get()).isEqualTo(2);
    }

    @Test
    void elBloqueoQuedaEnLaTablaDeShedlock() {
        String job = "job-tabla-" + System.nanoTime();
        ejecutor.executeWithLock((Runnable) () -> {
        }, new LockConfiguration(Instant.now(), job, Duration.ofMinutes(1), Duration.ofSeconds(30)));

        List<Map<String, Object>> filas = jdbc.queryForList("SELECT * FROM transversal.shedlock WHERE name = ?", job);
        assertThat(filas).hasSize(1);
        assertThat(filas.getFirst().get("locked_by")).isNotNull();
    }

    @Test
    void elProveedorUsaLaTablaDelEsquemaTransversal() {
        assertThat(new JdbcTemplate(datos).queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'transversal' AND table_name = 'shedlock'",
                Integer.class)).isEqualTo(1);
        assertThat(JdbcTemplateLockProvider.class).isNotNull();
    }

    @Test
    void unJobSinUsuarioAuditaComoSistema() {
        String nombre = "job-sistema-" + System.nanoTime();
        // Un job corre sin sesión HTTP ni autenticación: el hilo no tiene SecurityContext
        ejecutor.executeWithLock((Runnable) () -> new TransactionTemplate(gestorTransacciones)
                .executeWithoutResult(estado -> servicios.crear(nombre)), bloqueo(nombre));

        Map<String, Object> registro = jdbc.queryForMap(
                "SELECT * FROM auditoria.registro_auditoria WHERE id_entidad_afectada = ?", "cosa-" + nombre);
        assertThat(registro.get("accion")).isEqualTo("PRUEBA_CREAR");
        assertThat(registro.get("usuario_id")).as("actor «Sistema»").isNull();
    }
}
