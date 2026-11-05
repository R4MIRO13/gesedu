package pe.edu.gesedu.evaluacion.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.evaluacion.dominio.PesoBimestre;

public interface PesoBimestreRepository extends JpaRepository<PesoBimestre, String> {

    List<PesoBimestre> findByAsignacionId(String asignacionId);
}
