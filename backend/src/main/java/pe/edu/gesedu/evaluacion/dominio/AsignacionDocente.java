package pe.edu.gesedu.evaluacion.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Docente responsable de un curso en una sección (S-13). La crea o actualiza la publicación del horario; en la fase de
 * evaluación llevará la regla de consolidación del curso (UC-16). Un curso en una sección tiene un solo responsable.
 */
@Entity
@Table(name = "asignacion_docente", schema = "evaluacion")
public class AsignacionDocente extends EntidadBase {

    public static final String REGLA_POR_DEFECTO = "PROMEDIO_PONDERADO";

    // Relaciones entre módulos (seguridad y academico): solo ids
    @Column(name = "docente_id", nullable = false, length = 36)
    private String docenteId;

    @Column(name = "curso_id", nullable = false, updatable = false, length = 36)
    private String cursoId;

    @Column(name = "seccion_id", nullable = false, updatable = false, length = 36)
    private String seccionId;

    @Column(name = "regla_consolidacion", nullable = false, length = 20)
    private String reglaConsolidacion = REGLA_POR_DEFECTO;

    protected AsignacionDocente() {
    }

    public AsignacionDocente(String docenteId, String cursoId, String seccionId) {
        this.docenteId = docenteId;
        this.cursoId = cursoId;
        this.seccionId = seccionId;
    }

    /** Cambia al responsable; la regla de consolidación que el docente anterior definió se conserva. */
    public void asignarA(String nuevoDocenteId) {
        this.docenteId = nuevoDocenteId;
    }

    public String getDocenteId() {
        return docenteId;
    }

    public String getCursoId() {
        return cursoId;
    }

    public String getSeccionId() {
        return seccionId;
    }

    public String getReglaConsolidacion() {
        return reglaConsolidacion;
    }

    /** La regla como tipo; un valor desconocido en la base se trata como la regla por defecto. */
    public TipoRegla tipoRegla() {
        try {
            return TipoRegla.valueOf(reglaConsolidacion);
        } catch (IllegalArgumentException e) {
            return TipoRegla.POR_DEFECTO;
        }
    }

    /** Cambia la regla de consolidación (UC-16); devuelve {@code false} si ya era esa. */
    public boolean cambiarRegla(TipoRegla nueva) {
        if (nueva.name().equals(reglaConsolidacion)) {
            return false;
        }
        this.reglaConsolidacion = nueva.name();
        return true;
    }
}
