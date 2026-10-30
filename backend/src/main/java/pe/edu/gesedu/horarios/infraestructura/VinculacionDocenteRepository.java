package pe.edu.gesedu.horarios.infraestructura;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.horarios.dominio.VinculacionDocente;

public interface VinculacionDocenteRepository extends JpaRepository<VinculacionDocente, String> {

    List<VinculacionDocente> findByAnioEscolarIdAndHabilitadoTrue(String anioEscolarId);

    List<VinculacionDocente> findByAnioEscolarId(String anioEscolarId);

    Optional<VinculacionDocente> findByDocenteIdAndAnioEscolarId(String docenteId, String anioEscolarId);

    /** La vinculación con {@code SELECT … FOR UPDATE}: serializa a quien confirma la disponibilidad dos veces a la vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM VinculacionDocente v WHERE v.docenteId = :docenteId AND v.anioEscolarId = :anioEscolarId")
    Optional<VinculacionDocente> bloquearPorDocenteYAnio(@Param("docenteId") String docenteId,
                                                         @Param("anioEscolarId") String anioEscolarId);
}
