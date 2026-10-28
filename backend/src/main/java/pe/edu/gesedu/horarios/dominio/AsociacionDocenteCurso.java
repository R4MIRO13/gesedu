package pe.edu.gesedu.horarios.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Curso que un docente puede dictar. Solo se ofrecen los docentes asociados al curso (RF-47). */
@Entity
@Table(name = "asociacion_docente_curso", schema = "horarios")
public class AsociacionDocenteCurso extends EntidadBase {

    @Column(name = "vinculacion_id", nullable = false, length = 36)
    private String vinculacionId;

    // Relación entre módulos (academico): solo el id
    @Column(name = "curso_id", nullable = false, length = 36)
    private String cursoId;

    protected AsociacionDocenteCurso() {
    }

    public String getVinculacionId() {
        return vinculacionId;
    }

    public String getCursoId() {
        return cursoId;
    }
}
