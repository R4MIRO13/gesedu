package pe.edu.gesedu.academico.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.academico.dominio.Seccion;

public interface SeccionRepository extends JpaRepository<Seccion, String> {

    List<Seccion> findByAnioEscolarId(String anioEscolarId);
}
