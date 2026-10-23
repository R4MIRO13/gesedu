package pe.edu.gesedu.compartido.auditoria;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca una operación crítica (pagos, matrícula, notas, horarios, cuentas, roles, bloqueos) para que el aspecto
 * del módulo {@code auditoria} escriba un registro en la misma transacción (RF-49, DA-06).
 * <p>
 * El método debe ser público, estar en un bean de Spring y ejecutarse dentro de una transacción
 * ({@code @Transactional} en la capa {@code aplicacion}). Si el método lanza una excepción no se audita
 * (la transacción se revierte con él). Las expresiones SpEL ven los parámetros por nombre y {@code #result}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {

    /** Acción realizada, por ejemplo {@code CREAR_BLOQUE_HORARIO}. */
    String accion();

    /** Módulo funcional, por ejemplo {@code HORARIOS}. */
    String modulo();

    /** Tipo de la entidad afectada, por ejemplo {@code BloqueHorario}. */
    String tipoEntidad();

    /** SpEL con el id de la entidad afectada, por ejemplo {@code "#result.id"}. Vacío si no aplica. */
    String idEntidad() default "";

    /** Texto del registro. Si queda vacío se genera a partir de la acción y la entidad. */
    String descripcion() default "";

    /** SpEL booleano: solo se audita si da {@code true} (por ejemplo {@code "#result"}). Vacío = siempre. */
    String condicion() default "";
}
