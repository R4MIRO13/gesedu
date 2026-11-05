package pe.edu.gesedu.evaluacion.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.evaluacion.dominio.ConsolidacionBimestral;

public interface ConsolidacionBimestralRepository extends JpaRepository<ConsolidacionBimestral, String> {

    Optional<ConsolidacionBimestral> findByBimestreId(String bimestreId);
}
