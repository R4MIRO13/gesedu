package pe.edu.gesedu.horarios.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.horarios.dominio.VinculacionDocente;

public interface VinculacionDocenteRepository extends JpaRepository<VinculacionDocente, String> {

    List<VinculacionDocente> findByAnioEscolarIdAndHabilitadoTrue(String anioEscolarId);
}
