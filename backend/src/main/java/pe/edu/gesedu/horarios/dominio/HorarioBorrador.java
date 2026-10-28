package pe.edu.gesedu.horarios.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Borrador del horario de un año: uno solo por año, compartido por las perspectivas Sección y Docente. Su
 * {@code version} aumenta con cada alta, cambio o baja de asignaciones; quien edita con una versión vieja recibe
 * 409 y debe recargar la matriz (CLAUDE.md, concurrencia). No es visible para alumnos (RF-44).
 */
@Entity
@Table(name = "horario_borrador", schema = "horarios")
public class HorarioBorrador extends EntidadBase {

    @Column(name = "anio_escolar_id", nullable = false, unique = true, length = 36)
    private String anioEscolarId;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @Version
    private long version;

    protected HorarioBorrador() {
    }

    public HorarioBorrador(String anioEscolarId, Instant ahora) {
        this.anioEscolarId = anioEscolarId;
        this.fechaCreacion = ahora;
        this.fechaActualizacion = ahora;
    }

    /**
     * Marca el borrador como modificado. El valor siempre cambia (aunque dos cambios caigan en el mismo instante)
     * para que Hibernate vea la entidad sucia y {@code @Version} suba exactamente una vez por cada alta, cambio o
     * baja de asignaciones.
     */
    public void tocar(Instant ahora) {
        this.fechaActualizacion = ahora.isAfter(fechaActualizacion) ? ahora : fechaActualizacion.plusNanos(1_000);
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    public long getVersion() {
        return version;
    }
}
