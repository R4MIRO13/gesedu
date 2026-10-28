package pe.edu.gesedu.academico.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.academico.dominio.Grado;

public interface GradoRepository extends JpaRepository<Grado, String> {

    List<Grado> findByAnioEscolarId(String anioEscolarId);
}
