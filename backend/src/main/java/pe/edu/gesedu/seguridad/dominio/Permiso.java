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

    @Column(nullable = false, length = 30)
    private String modulo;

    @Column(nullable = false, length = 30)
    private String accion;

    @Column(length = 255)
    private String descripcion;

    protected Permiso() {
    }

    public String getModulo() {
        return modulo;
    }

    public String getAccion() {
        return accion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }
}
