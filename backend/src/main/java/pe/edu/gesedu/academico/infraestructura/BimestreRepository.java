package pe.edu.gesedu.academico.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.academico.dominio.Bimestre;

public interface BimestreRepository extends JpaRepository<Bimestre, String> {

    List<Bimestre> findByAnioEscolarIdOrderByNumero(String anioEscolarId);

    List<Bimestre> findByAnioEscolarIdInOrderByNumero(Collection<String> anioEscolarIds);
}
