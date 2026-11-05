package pe.edu.gesedu.evaluacion.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.evaluacion.dominio.RegistroLogro;

public interface RegistroLogroRepository extends JpaRepository<RegistroLogro, String> {

    List<RegistroLogro> findByAsignacionIdAndBimestreId(String asignacionId, String bimestreId);

    /** Todos los logros de un bimestre, de todas las asignaciones (para el avance y la consolidación). */
    List<RegistroLogro> findByBimestreId(String bimestreId);

    /** Todos los logros de la asignación en el año (para la nota anual). */
    List<RegistroLogro> findByAsignacionId(String asignacionId);
}
