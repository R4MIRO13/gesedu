package pe.edu.gesedu.academico.infraestructura;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.academico.dominio.AnioEscolar;

public interface AnioEscolarRepository extends JpaRepository<AnioEscolar, String> {

    Optional<AnioEscolar> findByAnio(int anio);

    List<AnioEscolar> findByAnioLessThan(int anio);
}
