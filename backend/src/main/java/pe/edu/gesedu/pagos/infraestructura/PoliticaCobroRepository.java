package pe.edu.gesedu.pagos.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.pagos.dominio.PoliticaCobro;

public interface PoliticaCobroRepository extends JpaRepository<PoliticaCobro, String> {

    Optional<PoliticaCobro> findByAnioEscolarId(String anioEscolarId);
}
