package pe.edu.gesedu.evaluacion.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Libreta de notas de un alumno en un año escolar (RF-35, RF-37): una sola, que agrupa las notas bimestrales ya
 * consolidadas. Es PARCIAL mientras falte algún bimestre y FINAL cuando se consolida el último y se calculan las notas
 * anuales (UC-18 FA-1).
 */
@Entity
@Table(name = "libreta", schema = "evaluacion")
public class Libreta extends EntidadBase {

    public static final String PARCIAL = "PARCIAL";
    public static final String FINAL = "FINAL";

    // Referencias a otros módulos: solo ids
    @Column(name = "alumno_id", nullable = false, updatable = false, length = 36)
    private String alumnoId;

    @Column(name = "anio_escolar_id", nullable = false, updatable = false, length = 36)
    private String anioEscolarId;

    @Column(nullable = false, length = 10)
    private String estado = PARCIAL;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    protected Libreta() {
    }

    public Libreta(String alumnoId, String anioEscolarId, Instant fechaActualizacion) {
        this.alumnoId = alumnoId;
        this.anioEscolarId = anioEscolarId;
        this.fechaActualizacion = fechaActualizacion;
    }

    /** Pone la libreta al día; devuelve {@code true} si cambió el estado. */
    public boolean actualizar(boolean finalizada, Instant ahora) {
        String nuevo = finalizada ? FINAL : PARCIAL;
        boolean cambio = !nuevo.equals(estado);
        this.estado = nuevo;
        this.fechaActualizacion = ahora;
        return cambio;
    }

    public String getAlumnoId() {
        return alumnoId;
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public String getEstado() {
        return estado;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }
}
