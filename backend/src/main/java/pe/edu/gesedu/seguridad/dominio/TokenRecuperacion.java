package pe.edu.gesedu.seguridad.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Enlace de recuperación de contraseña (RF-52, S-01): un solo uso, vence a los 30 minutos y solo se guarda su hash
 * SHA-256. Pedir otro enlace anula los anteriores.
 */
@Entity
@Table(name = "token_recuperacion", schema = "seguridad")
public class TokenRecuperacion extends EntidadBase {

    @Column(name = "usuario_id", nullable = false, length = 36)
    private String usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    protected TokenRecuperacion() {
    }

    public TokenRecuperacion(String usuarioId, String tokenHash, Instant expiraEn) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.expiraEn = expiraEn;
    }

    public boolean sePuedeUsar(Instant ahora) {
        return usadoEn == null && expiraEn.isAfter(ahora);
    }

    public void marcarUsado(Instant ahora) {
        this.usadoEn = ahora;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getUsadoEn() {
        return usadoEn;
    }
}
