package pe.edu.gesedu.transversal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Fija el orden del asesor transaccional. El aspecto {@code @Auditable} (orden {@code LOWEST_PRECEDENCE}) debe
 * ejecutarse dentro de la transacción de la operación auditada; por eso la transacción se abre antes (DA-06).
 * Esta declaración reemplaza a la de Spring Boot, que usa el orden por defecto (empate con el aspecto).
 */
@Configuration
@EnableTransactionManagement(proxyTargetClass = true, order = TransaccionesConfig.ORDEN_TRANSACCION)
public class TransaccionesConfig {

    public static final int ORDEN_TRANSACCION = Ordered.LOWEST_PRECEDENCE - 100;
}
