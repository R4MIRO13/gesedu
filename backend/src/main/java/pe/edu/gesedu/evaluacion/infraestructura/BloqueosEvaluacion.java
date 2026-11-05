package pe.edu.gesedu.evaluacion.infraestructura;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;

/** Candados de PostgreSQL que duran lo que la transacción en curso (se liberan solos al confirmar o revertir). */
@Component
public class BloqueosEvaluacion {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Toma un candado consultivo por clave: dos transacciones con la misma clave se ejecutan una tras otra. La consolidación
     * de un bimestre lo usa para que dos administradores no consoliden a la vez el mismo bimestre.
     */
    public void bloquearPorClave(String clave) {
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(hashtext(?1)) as text)")
                .setParameter(1, clave).getSingleResult();
    }
}
