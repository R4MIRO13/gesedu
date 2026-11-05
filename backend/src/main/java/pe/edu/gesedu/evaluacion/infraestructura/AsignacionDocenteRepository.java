package pe.edu.gesedu.evaluacion.infraestructura;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.evaluacion.dominio.AsignacionDocente;

public interface AsignacionDocenteRepository extends JpaRepository<AsignacionDocente, String> {

    List<AsignacionDocente> findBySeccionIdIn(Collection<String> seccionIds);

    List<AsignacionDocente> findByDocenteId(String docenteId);

    /** Bloquea la asignación (SELECT … FOR UPDATE): serializa los guardados de la misma rejilla. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AsignacionDocente a where a.id = :id")
    Optional<AsignacionDocente> buscarBloqueando(@Param("id") String id);
}
