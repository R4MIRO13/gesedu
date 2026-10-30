package pe.edu.gesedu.horarios.infraestructura;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.horarios.dominio.HorarioBorrador;

public interface HorarioBorradorRepository extends JpaRepository<HorarioBorrador, String> {

    Optional<HorarioBorrador> findByAnioEscolarId(String anioEscolarId);

    /** El borrador con {@code SELECT … FOR UPDATE}: serializa las publicaciones del año (UC-22). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM HorarioBorrador b WHERE b.anioEscolarId = :anioEscolarId")
    Optional<HorarioBorrador> bloquearPorAnio(@Param("anioEscolarId") String anioEscolarId);
}
