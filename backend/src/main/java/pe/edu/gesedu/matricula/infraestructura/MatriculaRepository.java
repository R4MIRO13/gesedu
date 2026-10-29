package pe.edu.gesedu.matricula.infraestructura;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.matricula.dominio.EstadoMatricula;
import pe.edu.gesedu.matricula.dominio.Matricula;

public interface MatriculaRepository extends JpaRepository<Matricula, String> {

    boolean existsByAlumnoIdAndAnioEscolarIdAndEstado(String alumnoId, String anioEscolarId, EstadoMatricula estado);
}
