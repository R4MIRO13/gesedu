package pe.edu.gesedu.pagos.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.pagos.dominio.EstadoObligacion;
import pe.edu.gesedu.pagos.dominio.ObligacionPago;

public interface ObligacionPagoRepository extends JpaRepository<ObligacionPago, String> {

    /** Saldo de los compromisos de los años indicados que ya vencieron y no se pagaron (monto más penalidad). */
    @Query("SELECT COALESCE(SUM(o.montoBase + o.montoPenalidad), 0) FROM ObligacionPago o "
            + "WHERE o.alumnoId = :alumnoId AND o.anioEscolarId IN :anios AND o.estado <> :pagada "
            + "AND o.fechaVencimiento < :hoy")
    BigDecimal deudaVencida(@Param("alumnoId") String alumnoId, @Param("anios") Collection<String> anios,
                            @Param("pagada") EstadoObligacion pagada, @Param("hoy") LocalDate hoy);

    long countByMatriculaId(String matriculaId);

    List<ObligacionPago> findByAlumnoIdAndAnioEscolarIdIn(String alumnoId, Collection<String> anios);

    List<ObligacionPago> findByAlumnoId(String alumnoId);

    /**
     * La obligación con un bloqueo de escritura ({@code SELECT … FOR UPDATE}): quien valida o revisa un comprobante
     * espera a que termine la otra operación sobre la misma obligación (RNF-10).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM ObligacionPago o WHERE o.id = :id")
    Optional<ObligacionPago> bloquear(@Param("id") String id);

    List<ObligacionPago> findByIdIn(Collection<String> ids);

    /**
     * Ids de las obligaciones por procesar: ya pasó su vencimiento, no están pagadas y todavía son PENDIENTES o no
     * recibieron su penalidad (RF-09, RF-10).
     */
    @Query("SELECT o.id FROM ObligacionPago o WHERE o.estado <> :pagada AND o.fechaVencimiento < :hoy "
            + "AND (o.estado = :pendiente OR o.penalidadAplicadaEn IS NULL) ORDER BY o.fechaVencimiento, o.id")
    List<String> porVencer(@Param("hoy") LocalDate hoy, @Param("pagada") EstadoObligacion pagada,
                           @Param("pendiente") EstadoObligacion pendiente);
}
