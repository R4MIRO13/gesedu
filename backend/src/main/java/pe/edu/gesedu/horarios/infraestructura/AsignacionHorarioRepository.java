package pe.edu.gesedu.horarios.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.horarios.dominio.AsignacionHorario;

public interface AsignacionHorarioRepository extends JpaRepository<AsignacionHorario, String> {

    List<AsignacionHorario> findByHorarioBorradorId(String horarioBorradorId);
}
