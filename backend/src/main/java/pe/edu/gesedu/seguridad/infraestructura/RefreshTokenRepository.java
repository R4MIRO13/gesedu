package pe.edu.gesedu.seguridad.infraestructura;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.seguridad.dominio.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /** Con bloqueo de fila: dos refrescos simultáneos del mismo token se atienden uno tras otro. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revocadoEn = :ahora WHERE t.familia = :familia AND t.revocadoEn IS NULL")
    int revocarFamilia(@Param("familia") String familia, @Param("ahora") Instant ahora);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revocadoEn = :ahora WHERE t.usuarioId = :usuarioId AND t.revocadoEn IS NULL")
    int revocarTodosDelUsuario(@Param("usuarioId") String usuarioId, @Param("ahora") Instant ahora);
}
