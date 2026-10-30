package pe.edu.gesedu.horarios.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.horarios.dominio.AsignacionHorario;

public interface AsignacionHorarioRepository extends JpaRepository<AsignacionHorario, String> {

    List<AsignacionHorario> findByHorarioBorradorId(String horarioBorradorId);

    boolean existsByAsociacionDocenteCursoId(String asociacionDocenteCursoId);

    /** Clases del docente en el borrador del año (para no deshabilitarlo ni desasociarlo con clases asignadas). */
    @Query("SELECT COUNT(a) FROM AsignacionHorario a, HorarioBorrador b WHERE a.horarioBorradorId = b.id "
            + "AND b.anioEscolarId = :anioEscolarId AND a.docenteId = :docenteId")
    long contarClasesDelDocenteEnElBorrador(@Param("anioEscolarId") String anioEscolarId, @Param("docenteId") String docenteId);
}
