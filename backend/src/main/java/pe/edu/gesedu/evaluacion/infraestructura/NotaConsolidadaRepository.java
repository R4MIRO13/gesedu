package pe.edu.gesedu.evaluacion.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.evaluacion.dominio.NotaConsolidada;

public interface NotaConsolidadaRepository extends JpaRepository<NotaConsolidada, String> {

    List<NotaConsolidada> findByAsignacionIdAndTipo(String asignacionId, String tipo);

    /** Todas las notas de un alumno en un año (bimestrales y anuales). */
    List<NotaConsolidada> findByAlumnoIdAndAnioEscolarId(String alumnoId, String anioEscolarId);

    /** Las notas de un bimestre en el año, de todas las asignaciones. */
    List<NotaConsolidada> findByAnioEscolarIdAndBimestreId(String anioEscolarId, String bimestreId);
}
