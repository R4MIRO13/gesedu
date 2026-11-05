package pe.edu.gesedu.evaluacion.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.evaluacion.dominio.NotaConsolidada;

public interface NotaConsolidadaRepository extends JpaRepository<NotaConsolidada, String> {

    List<NotaConsolidada> findByAsignacionIdAndTipo(String asignacionId, String tipo);
}
