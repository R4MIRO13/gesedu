package pe.edu.gesedu.academico.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.academico.dominio.Competencia;

public interface CompetenciaRepository extends JpaRepository<Competencia, String> {

    List<Competencia> findByCursoIdOrderByOrden(String cursoId);

    List<Competencia> findByCursoIdInOrderByOrden(Collection<String> cursoIds);
}
