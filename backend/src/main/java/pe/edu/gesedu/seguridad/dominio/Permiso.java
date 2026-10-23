package pe.edu.gesedu.seguridad.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Permiso del catálogo (RF-48), con el formato {@code MODULO_ACCION}. Solo lectura desde la aplicación. */
@Entity
@Table(name = "permiso", schema = "seguridad")
public class Permiso {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 60)
    private String codigo;

    protected Permiso() {
    }

    public String getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }
}
