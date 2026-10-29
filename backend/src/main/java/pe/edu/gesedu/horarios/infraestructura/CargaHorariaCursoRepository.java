package pe.edu.gesedu.horarios.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.horarios.dominio.CargaHorariaCurso;

public interface CargaHorariaCursoRepository extends JpaRepository<CargaHorariaCurso, String> {

    List<CargaHorariaCurso> findByGradoIdIn(Collection<String> gradoIds);
}
