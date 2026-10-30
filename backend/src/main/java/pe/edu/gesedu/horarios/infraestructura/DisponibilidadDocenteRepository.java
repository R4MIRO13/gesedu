package pe.edu.gesedu.horarios.infraestructura;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.horarios.dominio.DisponibilidadDocente;

public interface DisponibilidadDocenteRepository extends JpaRepository<DisponibilidadDocente, String> {

    Optional<DisponibilidadDocente> findByVinculacionId(String vinculacionId);

    List<DisponibilidadDocente> findByVinculacionIdIn(Collection<String> vinculacionIds);
}
