package pe.edu.gesedu.transversal.planificador;

import javax.sql.DataSource;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tareas programadas (DA-08). Cada job lleva {@code @Scheduled} más {@code @SchedulerLock(name = "...")}: con
 * varias instancias detrás del balanceador, ShedLock hace que solo una ejecute cada disparo. El bloqueo vive en
 * {@code transversal.shedlock} y usa la hora de la base de datos, para que los relojes de las instancias no
 * importen. Los jobs deben ser idempotentes y actúan como el usuario «Sistema» (sin autenticación, la auditoría
 * registra {@code usuario_id} nulo).
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class ConfiguracionPlanificador {

    @Bean
    LockProvider proveedorDeBloqueos(DataSource datos) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(datos))
                .withTableName("transversal.shedlock")
                .usingDbTime()
                .build());
    }

    /** Para ejecutar un trabajo bajo el mismo bloqueo desde código (por ejemplo, en pruebas de concurrencia). */
    @Bean
    LockingTaskExecutor ejecutorConBloqueo(LockProvider proveedor) {
        return new DefaultLockingTaskExecutor(proveedor);
    }
}
