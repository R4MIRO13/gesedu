package pe.edu.gesedu.evaluacion.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.evaluacion.dominio.Libreta;

public interface LibretaRepository extends JpaRepository<Libreta, String> {

    List<Libreta> findByAnioEscolarId(String anioEscolarId);
}
