package pe.edu.gesedu.pagos.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.pagos.dominio.RegistroPago;

public interface RegistroPagoRepository extends JpaRepository<RegistroPago, String> {

    List<RegistroPago> findByObligacionIdIn(Collection<String> obligacionIds);
}
