package pe.edu.gesedu.academico.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.academico.dominio.Curso;

public interface CursoRepository extends JpaRepository<Curso, String> {

    List<Curso> findByAnioEscolarIdOrderByNombre(String anioEscolarId);
}
