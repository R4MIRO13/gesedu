package pe.edu.gesedu.horarios.infraestructura;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.horarios.dominio.VinculacionDocente;

public interface VinculacionDocenteRepository extends JpaRepository<VinculacionDocente, String> {

    List<VinculacionDocente> findByAnioEscolarIdAndHabilitadoTrue(String anioEscolarId);

    List<VinculacionDocente> findByAnioEscolarId(String anioEscolarId);

    Optional<VinculacionDocente> findByDocenteIdAndAnioEscolarId(String docenteId, String anioEscolarId);
}
