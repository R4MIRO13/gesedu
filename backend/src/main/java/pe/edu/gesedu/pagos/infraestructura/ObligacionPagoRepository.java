package pe.edu.gesedu.pagos.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
