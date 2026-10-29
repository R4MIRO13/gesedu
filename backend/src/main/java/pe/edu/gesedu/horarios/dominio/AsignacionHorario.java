package pe.edu.gesedu.horarios.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Una clase del borrador: un curso de una sección, con un docente, en un bloque. Los índices únicos
 * (borrador, bloque, docente) y (borrador, bloque, sección) son la última defensa contra los cruces (RF-41).
 */
@Entity
@Table(name = "asignacion_horario", schema = "horarios")
public class AsignacionHorario extends EntidadBase {

    @Column(name = "horario_borrador_id", nullable = false, length = 36)
    private String horarioBorradorId;

    @Column(name = "bloque_id", nullable = false, length = 36)
    private String bloqueId;

    // Relaciones entre módulos (academico y seguridad): solo ids
    @Column(name = "seccion_id", nullable = false, length = 36)
    private String seccionId;

    @Column(name = "curso_id", nullable = false, length = 36)
    private String cursoId;

    @Column(name = "docente_id", nullable = false, length = 36)
    private String docenteId;

    @Column(name = "asociacion_docente_curso_id", nullable = false, length = 36)
    private String asociacionDocenteCursoId;

    protected AsignacionHorario() {
    }

    public AsignacionHorario(String horarioBorradorId, String bloqueId, String seccionId, String cursoId,
                             String docenteId, String asociacionDocenteCursoId) {
        this.horarioBorradorId = horarioBorradorId;
        this.bloqueId = bloqueId;
        this.seccionId = seccionId;
        this.cursoId = cursoId;
        this.docenteId = docenteId;
        this.asociacionDocenteCursoId = asociacionDocenteCursoId;
    }

    /** Cambia el curso, el docente y/o el bloque (UC-21, FA-1). La sección no cambia. */
    public void reasignar(String nuevoBloqueId, String nuevoCursoId, String nuevoDocenteId,
                          String nuevaAsociacionId) {
        this.bloqueId = nuevoBloqueId;
        this.cursoId = nuevoCursoId;
        this.docenteId = nuevoDocenteId;
        this.asociacionDocenteCursoId = nuevaAsociacionId;
    }

    public String getHorarioBorradorId() {
        return horarioBorradorId;
    }

    public String getBloqueId() {
        return bloqueId;
    }

    public String getSeccionId() {
        return seccionId;
    }

    public String getCursoId() {
        return cursoId;
    }

    public String getDocenteId() {
        return docenteId;
    }

    public String getAsociacionDocenteCursoId() {
        return asociacionDocenteCursoId;
    }
}
