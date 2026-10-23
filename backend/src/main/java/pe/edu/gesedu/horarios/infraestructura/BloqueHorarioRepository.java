package pe.edu.gesedu.horarios.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.horarios.dominio.BloqueHorario;
import pe.edu.gesedu.horarios.dominio.DiaSemana;

public interface BloqueHorarioRepository extends JpaRepository<BloqueHorario, String> {

    List<BloqueHorario> findByAnioEscolarId(String anioEscolarId);

    List<BloqueHorario> findByAnioEscolarIdAndDiaSemana(String anioEscolarId, DiaSemana diaSemana);
}
