package pe.edu.gesedu.pagos.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.pagos.dominio.ComprobantePago;
import pe.edu.gesedu.pagos.dominio.EstadoRevision;

public interface ComprobantePagoRepository extends JpaRepository<ComprobantePago, String> {

    List<ComprobantePago> findByObligacionIdIn(Collection<String> obligacionIds);

    boolean existsByObligacionIdAndEstadoRevision(String obligacionId, EstadoRevision estado);

    boolean existsByNumeroOperacionAndEstadoRevisionIn(String numeroOperacion, Collection<EstadoRevision> estados);
}
