package pe.edu.gesedu.seguridad.infraestructura;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.seguridad.dominio.TokenRecuperacion;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, String> {

    /** Con bloqueo de fila: dos confirmaciones simultáneas del mismo enlace se atienden una tras otra. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TokenRecuperacion> findByTokenHash(String tokenHash);

    /** Anula los enlaces pendientes de la cuenta: solo vale el último que se pidió. */
    @Modifying
    @Query("UPDATE TokenRecuperacion t SET t.usadoEn = :ahora WHERE t.usuarioId = :usuarioId AND t.usadoEn IS NULL")
    int anularPendientes(@Param("usuarioId") String usuarioId, @Param("ahora") Instant ahora);
}
