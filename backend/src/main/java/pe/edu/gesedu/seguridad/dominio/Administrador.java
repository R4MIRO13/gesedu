package pe.edu.gesedu.seguridad.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Perfil de un usuario administrativo; el Directivo es un Administrador con cargo de dirección. */
@Entity
@Table(name = "administrador", schema = "seguridad")
public class Administrador extends EntidadBase {

    @Column(name = "usuario_id", nullable = false, unique = true, length = 36)
    private String usuarioId;

    @Column(nullable = false, length = 80)
    private String cargo;

    protected Administrador() {
    }

    public Administrador(String usuarioId, String cargo) {
        this.usuarioId = usuarioId;
        this.cargo = cargo;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public String getCargo() {
        return cargo;
    }

    public void cambiarCargo(String cargo) {
        this.cargo = cargo;
    }
}
