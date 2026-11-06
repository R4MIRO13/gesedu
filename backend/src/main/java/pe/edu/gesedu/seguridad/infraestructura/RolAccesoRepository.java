package pe.edu.gesedu.seguridad.infraestructura;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.seguridad.dominio.RolAcceso;

public interface RolAccesoRepository extends JpaRepository<RolAcceso, String> {

    Optional<RolAcceso> findByCodigo(String codigo);

    @EntityGraph(attributePaths = "permisos")
    @Query("SELECT r FROM RolAcceso r ORDER BY r.nombre")
    List<RolAcceso> listarConPermisos();

    /** Con bloqueo de fila: dos cambios simultáneos de permisos del mismo rol se atienden uno tras otro. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RolAcceso r WHERE r.id = :id")
    Optional<RolAcceso> bloquearPorId(@Param("id") String id);
}
