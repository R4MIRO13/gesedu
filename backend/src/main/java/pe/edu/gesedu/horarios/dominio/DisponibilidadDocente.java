package pe.edu.gesedu.horarios.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Disponibilidad horaria confirmada por un docente; solo se conserva la última versión (RF-46, UC-20 FA-1). */
@Entity
@Table(name = "disponibilidad_docente", schema = "horarios")
public class DisponibilidadDocente extends EntidadBase {

    @Column(name = "vinculacion_id", nullable = false, unique = true, length = 36)
    private String vinculacionId;

    @Column(name = "fecha_hora_confirmacion", nullable = false)
    private Instant fechaHoraConfirmacion;

    @Column(columnDefinition = "text")
    private String observaciones;

    protected DisponibilidadDocente() {
    }

    public String getVinculacionId() {
        return vinculacionId;
    }

    public Instant getFechaHoraConfirmacion() {
        return fechaHoraConfirmacion;
    }

    public String getObservaciones() {
        return observaciones;
    }
}
