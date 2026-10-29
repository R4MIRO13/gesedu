package pe.edu.gesedu.horarios.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.horarios.dominio.AsociacionDocenteCurso;

public interface AsociacionDocenteCursoRepository extends JpaRepository<AsociacionDocenteCurso, String> {

    List<AsociacionDocenteCurso> findByVinculacionIdIn(Collection<String> vinculacionIds);
}
