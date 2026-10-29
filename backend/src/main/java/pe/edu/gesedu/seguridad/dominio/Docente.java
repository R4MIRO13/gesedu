package pe.edu.gesedu.seguridad.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Perfil de docente de una cuenta (el docente tutor es una asignación a una sección, no otro rol). */
@Entity
@Table(name = "docente", schema = "seguridad")
public class Docente extends EntidadBase {

    @Column(name = "usuario_id", nullable = false, unique = true, length = 36)
    private String usuarioId;

    @Column(name = "codigo_docente", nullable = false, unique = true, length = 20)
    private String codigoDocente;

    @Column(length = 80)
    private String especialidad;

    protected Docente() {
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public String getCodigoDocente() {
        return codigoDocente;
    }

    public String getEspecialidad() {
        return especialidad;
    }
}
