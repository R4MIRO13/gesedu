package pe.edu.gesedu.auditoria.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/**
 * Registro de una operación crítica (RF-49). Tabla de solo inserción: la entidad es inmutable y
 * {@code gesedu_app} no tiene UPDATE ni DELETE (RNF-08).
 */
@Entity
@Table(name = "registro_auditoria", schema = "auditoria")
public class RegistroAuditoria {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(length = 36, updatable = false)
    private String id;

    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private Instant fechaHora;

    /** {@code null} = «Sistema» (tareas programadas y acciones automáticas). */
    @Column(name = "usuario_id", length = 36, updatable = false)
    private String usuarioId;

    @Column(nullable = false, length = 60, updatable = false)
    private String accion;

    @Column(nullable = false, length = 40, updatable = false)
    private String modulo;

    @Column(name = "tipo_entidad", nullable = false, length = 60, updatable = false)
    private String tipoEntidad;

    @Column(name = "id_entidad_afectada", length = 60, updatable = false)
    private String idEntidadAfectada;

    @Column(nullable = false, length = 500, updatable = false)
    private String descripcion;

    protected RegistroAuditoria() {
    }

    public RegistroAuditoria(Instant fechaHora, String usuarioId, String accion, String modulo, String tipoEntidad,
                             String idEntidadAfectada, String descripcion) {
        this.fechaHora = fechaHora;
        this.usuarioId = usuarioId;
        this.accion = accion;
        this.modulo = modulo;
        this.tipoEntidad = tipoEntidad;
        this.idEntidadAfectada = idEntidadAfectada;
        this.descripcion = descripcion;
    }

    public String getId() {
        return id;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public String getAccion() {
        return accion;
    }

    public String getModulo() {
        return modulo;
    }

    public String getTipoEntidad() {
        return tipoEntidad;
    }

    public String getIdEntidadAfectada() {
        return idEntidadAfectada;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
