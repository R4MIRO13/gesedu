package pe.edu.gesedu.seguridad.dominio;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/** Rol de acceso (ADMINISTRADOR, DOCENTE, ALUMNO, APODERADO) con sus permisos (RF-48). */
@Entity
@Table(name = "rol_acceso", schema = "seguridad")
public class RolAcceso {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "rol_permiso", schema = "seguridad",
            joinColumns = @JoinColumn(name = "rol_acceso_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    private Set<Permiso> permisos = new HashSet<>();

    protected RolAcceso() {
    }

    public String getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Set<Permiso> getPermisos() {
        return Set.copyOf(permisos);
    }

    /** Reemplaza el conjunto completo de permisos del rol (RF-48). */
    public void reemplazarPermisos(Set<Permiso> nuevos) {
        permisos.retainAll(nuevos);
        permisos.addAll(nuevos);
    }
}
