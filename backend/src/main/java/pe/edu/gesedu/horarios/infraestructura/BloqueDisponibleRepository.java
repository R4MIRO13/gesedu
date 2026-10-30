package pe.edu.gesedu.horarios.infraestructura;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.horarios.dominio.BloqueDisponible;

public interface BloqueDisponibleRepository extends JpaRepository<BloqueDisponible, String> {

    List<BloqueDisponible> findByDisponibilidadIdIn(Collection<String> disponibilidadIds);

    /**
     * Docentes habilitados en el año que confirmaron su disponibilidad e incluyeron ese bloque. Un docente que
     * no confirmó ninguna disponibilidad no aparece: no está disponible en ningún bloque (UC-20).
     */
    @Query("SELECT v.docenteId FROM VinculacionDocente v, DisponibilidadDocente d, BloqueDisponible b "
            + "WHERE d.vinculacionId = v.id AND b.disponibilidadId = d.id AND b.bloqueId = :bloqueId "
            + "AND v.anioEscolarId = :anioEscolarId AND v.habilitado = TRUE")
    Set<String> docentesDisponiblesEn(@Param("anioEscolarId") String anioEscolarId, @Param("bloqueId") String bloqueId);
}
