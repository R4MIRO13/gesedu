package pe.edu.gesedu.evaluacion.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.evaluacion.dominio.RegistroLogro;

public interface RegistroLogroRepository extends JpaRepository<RegistroLogro, String> {

    List<RegistroLogro> findByAsignacionIdAndBimestreId(String asignacionId, String bimestreId);
}
