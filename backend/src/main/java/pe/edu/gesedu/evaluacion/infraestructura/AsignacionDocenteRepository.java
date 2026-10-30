package pe.edu.gesedu.evaluacion.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.evaluacion.dominio.AsignacionDocente;

public interface AsignacionDocenteRepository extends JpaRepository<AsignacionDocente, String> {

    List<AsignacionDocente> findBySeccionIdIn(Collection<String> seccionIds);
}
