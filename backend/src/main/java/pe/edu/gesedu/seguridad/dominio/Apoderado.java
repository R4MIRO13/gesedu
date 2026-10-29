package pe.edu.gesedu.seguridad.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Perfil de apoderado de una cuenta. */
@Entity
@Table(name = "apoderado", schema = "seguridad")
public class Apoderado extends EntidadBase {

    @Column(name = "usuario_id", nullable = false, unique = true, length = 36)
    private String usuarioId;

    @Column(name = "numero_telefono", length = 20)
    private String numeroTelefono;

    protected Apoderado() {
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public String getNumeroTelefono() {
        return numeroTelefono;
    }
}
