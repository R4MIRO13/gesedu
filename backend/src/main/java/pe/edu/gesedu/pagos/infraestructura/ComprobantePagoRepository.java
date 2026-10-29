package pe.edu.gesedu.pagos.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.pagos.dominio.ComprobantePago;

public interface ComprobantePagoRepository extends JpaRepository<ComprobantePago, String> {

    List<ComprobantePago> findByObligacionIdIn(Collection<String> obligacionIds);
}
