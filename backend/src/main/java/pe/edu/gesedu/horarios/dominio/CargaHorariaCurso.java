package pe.edu.gesedu.horarios.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Bloques semanales de un curso en un grado (RF-43, C-10): lo que el horario de la sección debe completar. */
@Entity
@Table(name = "carga_horaria_curso", schema = "horarios")
public class CargaHorariaCurso extends EntidadBase {

    @Column(name = "grado_id", nullable = false, length = 36)
    private String gradoId;

    @Column(name = "curso_id", nullable = false, length = 36)
    private String cursoId;

    @Column(name = "bloques_semanales", nullable = false)
    private int bloquesSemanales;

    protected CargaHorariaCurso() {
    }

    public CargaHorariaCurso(String gradoId, String cursoId, int bloquesSemanales) {
        this.gradoId = gradoId;
        this.cursoId = cursoId;
        this.bloquesSemanales = bloquesSemanales;
    }

    public void setBloquesSemanales(int bloquesSemanales) {
        this.bloquesSemanales = bloquesSemanales;
    }

    public String getGradoId() {
        return gradoId;
    }

    public String getCursoId() {
        return cursoId;
    }

    public int getBloquesSemanales() {
        return bloquesSemanales;
    }
}
