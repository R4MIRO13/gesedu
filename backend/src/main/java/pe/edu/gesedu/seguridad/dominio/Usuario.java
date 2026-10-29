package pe.edu.gesedu.seguridad.dominio;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Cuenta de usuario. Contiene las reglas del bloqueo temporal por intentos fallidos (RNF-06, S-03).
 * Las cuentas no se eliminan: se desactivan (RF-01).
 */
@Entity
@Table(name = "usuario", schema = "seguridad")
public class Usuario extends EntidadBase {

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 100)
    private String apellidos;

    // CITEXT: la comparación ignora mayúsculas; columnDefinition hace que Flyway validate lo acepte
    @Column(columnDefinition = "citext")
    private String correo;

    @Column(name = "hash_contrasena", length = 100)
    private String hashContrasena;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    @Column(name = "ultimo_acceso")
    private Instant ultimoAcceso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rol_acceso_id", nullable = false)
    private RolAcceso rolAcceso;

    @Version
    private long version;

    protected Usuario() {
    }

    public boolean estaBloqueada(Instant ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    /**
     * Cuenta un intento fallido. Al llegar a {@code maxIntentos} bloquea la cuenta por {@code duracion} y
     * reinicia el contador, para que al vencer el bloqueo vuelvan a ser 5 intentos y no uno solo.
     *
     * @return {@code true} si este intento provocó el bloqueo
     */
    public boolean registrarIntentoFallido(Instant ahora, int maxIntentos, Duration duracion) {
        intentosFallidos++;
        if (intentosFallidos >= maxIntentos) {
            bloqueadoHasta = ahora.plus(duracion);
            intentosFallidos = 0;
            return true;
        }
        return false;
    }

    /**
     * Define una contraseña nueva (recuperación, RF-52). Reinicia el contador de intentos fallidos; un bloqueo
     * temporal vigente no se levanta: lo levanta el paso del tiempo o el administrador.
     */
    public void cambiarContrasena(String nuevoHash) {
        this.hashContrasena = nuevoHash;
        this.intentosFallidos = 0;
    }

    /** Levanta el bloqueo temporal y reinicia el contador de intentos (UC-24: el administrador desbloquea la cuenta). */
    public void desbloquear() {
        this.intentosFallidos = 0;
        this.bloqueadoHasta = null;
    }

    /** Un inicio de sesión correcto reinicia el contador y levanta cualquier bloqueo vencido. */
    public void registrarAccesoExitoso(Instant ahora) {
        intentosFallidos = 0;
        bloqueadoHasta = null;
        ultimoAcceso = ahora;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public String getCorreo() {
        return correo;
    }

    public String getHashContrasena() {
        return hashContrasena;
    }

    public boolean isActivo() {
        return activo;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public Instant getUltimoAcceso() {
        return ultimoAcceso;
    }

    public Instant getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    public RolAcceso getRolAcceso() {
        return rolAcceso;
    }
}
