package pe.edu.gesedu.seguridad.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Perfil de un usuario administrativo; el Directivo es un Administrador con cargo de dirección. */
@Entity
@Table(name = "administrador", schema = "seguridad")
public class Administrador {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "usuario_id", nullable = false, unique = true, length = 36)
    private String usuarioId;

    @Column(nullable = false, length = 80)
    private String cargo;

    protected Administrador() {
    }

    public String getCargo() {
        return cargo;
    }
}
