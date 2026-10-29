package pe.edu.gesedu.pagos.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pe.edu.gesedu.pagos.dominio.RegistroPago;

public interface RegistroPagoRepository extends JpaRepository<RegistroPago, String> {

    List<RegistroPago> findByObligacionIdIn(Collection<String> obligacionIds);

    /** Siguiente número de la serie de boletas (la secuencia puede dejar huecos si una validación se revierte). */
    @Query(value = "SELECT nextval('pagos.seq_boleta')", nativeQuery = true)
    long siguienteNumeroDeBoleta();
}
