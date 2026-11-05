package pe.edu.gesedu.asistencia.dominio;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Asistencia diaria de un alumno (UC-08, UC-09; RF-14, RF-15): como máximo una por alumno y fecha, garantizado por la
 * base ({@code ux_asistencia_alumno}). La sección y el tutor no se duplican aquí: se consultan por las relaciones
 * académicas. La ausencia de registro no equivale a FALTA. El único cambio posible es de FALTA a TARDANZA (UC-09), que
 * sube {@code updated_at}; no hay historial de estados en el módulo (C-07).
 */
@Entity
@Table(name = "asistencia_alumno", schema = "asistencia")
public class AsistenciaAlumno extends EntidadBase {

    // Relación entre módulos (seguridad): solo el id
    @Column(name = "alumno_id", nullable = false, updatable = false, length = 36)
    private String alumnoId;

    @Column(nullable = false, updatable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoAsistencia estado;

    protected AsistenciaAlumno() {
    }

    public String getAlumnoId() {
        return alumnoId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public EstadoAsistencia getEstado() {
        return estado;
    }
}
