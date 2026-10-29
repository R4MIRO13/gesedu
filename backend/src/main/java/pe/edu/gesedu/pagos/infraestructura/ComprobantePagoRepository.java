package pe.edu.gesedu.pagos.infraestructura;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.pagos.dominio.ComprobantePago;
import pe.edu.gesedu.pagos.dominio.EstadoRevision;

public interface ComprobantePagoRepository
        extends JpaRepository<ComprobantePago, String>, JpaSpecificationExecutor<ComprobantePago> {

    List<ComprobantePago> findByObligacionIdIn(Collection<String> obligacionIds);

    boolean existsByObligacionIdAndEstadoRevision(String obligacionId, EstadoRevision estado);

    boolean existsByNumeroOperacionAndEstadoRevisionIn(String numeroOperacion, Collection<EstadoRevision> estados);

    /** Solo el id de la obligación: se necesita para bloquearla antes de leer el estado del comprobante. */
    @Query("SELECT c.obligacionId FROM ComprobantePago c WHERE c.id = :id")
    Optional<String> obligacionIdDe(@Param("id") String id);
}
