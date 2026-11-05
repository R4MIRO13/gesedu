package pe.edu.gesedu.matricula.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.matricula.dominio.EstadoMatricula;
import pe.edu.gesedu.matricula.dominio.Matricula;

public interface MatriculaRepository extends JpaRepository<Matricula, String> {

    boolean existsByAlumnoIdAndAnioEscolarIdAndEstado(String alumnoId, String anioEscolarId, EstadoMatricula estado);

    List<Matricula> findByAlumnoIdInAndEstado(Collection<String> alumnoIds, EstadoMatricula estado);

    List<Matricula> findBySeccionIdAndEstado(String seccionId, EstadoMatricula estado);

    List<Matricula> findByAlumnoIdAndAnioEscolarIdAndEstado(String alumnoId, String anioEscolarId, EstadoMatricula estado);
}
