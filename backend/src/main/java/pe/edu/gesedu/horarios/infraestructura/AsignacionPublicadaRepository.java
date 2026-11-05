package pe.edu.gesedu.horarios.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.horarios.dominio.AsignacionPublicada;

public interface AsignacionPublicadaRepository extends JpaRepository<AsignacionPublicada, String> {

    List<AsignacionPublicada> findByHorarioPublicadoId(String horarioPublicadoId);

    long countByHorarioPublicadoId(String horarioPublicadoId);

    List<AsignacionPublicada> findByHorarioPublicadoIdAndDocenteId(String horarioPublicadoId, String docenteId);
}
