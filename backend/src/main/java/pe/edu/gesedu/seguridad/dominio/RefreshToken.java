package pe.edu.gesedu.seguridad.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Refresh token rotativo (DA-04, S-06). Solo se guarda el hash SHA-256 del valor. Los tokens de una cadena de
 * rotaciones comparten la {@code familia}: presentar uno ya rotado revoca la familia entera.
 */
@Entity
@Table(name = "refresh_token", schema = "seguridad")
public class RefreshToken extends EntidadBase {

    @Column(name = "usuario_id", nullable = false, length = 36)
    private String usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 36)
    private String familia;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    @Column(name = "reemplazado_por", length = 36)
    private String reemplazadoPor;

    protected RefreshToken() {
    }

    public RefreshToken(String usuarioId, String tokenHash, String familia, Instant expiraEn) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.familia = familia;
        this.expiraEn = expiraEn;
    }

    public boolean estaRevocado() {
        return revocadoEn != null;
    }

    public boolean haVencido(Instant ahora) {
        return !expiraEn.isAfter(ahora);
    }

    /** Marca el token como rotado: queda revocado y apunta al token que lo reemplazó. */
    public void rotar(String reemplazo, Instant ahora) {
        this.revocadoEn = ahora;
        this.reemplazadoPor = reemplazo;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public String getFamilia() {
        return familia;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getRevocadoEn() {
        return revocadoEn;
    }

    public String getReemplazadoPor() {
        return reemplazadoPor;
    }
}
