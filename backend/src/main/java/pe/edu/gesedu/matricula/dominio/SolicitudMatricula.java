package pe.edu.gesedu.matricula.dominio;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Solicitud de matrícula que el apoderado presenta (UC-01) y el administrador evalúa (UC-02). Una vez aprobada o
 * rechazada ya no cambia. {@code version} permite el {@code If-Match} de la confirmación.
 */
@Entity
@Table(name = "solicitud_matricula", schema = "matricula")
public class SolicitudMatricula extends EntidadBase {

    @Column(name = "codigo_seguimiento", nullable = false, unique = true, length = 20)
    private String codigoSeguimiento;

    // Entre módulos solo ids
    @Column(name = "anio_escolar_id", nullable = false, length = 36)
    private String anioEscolarId;

    @Column(name = "alumno_id", nullable = false, length = 36)
    private String alumnoId;

    @Column(name = "apoderado_id", nullable = false, length = 36)
    private String apoderadoId;

    @Column(name = "grado_id", nullable = false, length = 36)
    private String gradoId;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDate fechaSolicitud;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_estudiante", nullable = false, length = 15)
    private TipoEstudiante tipoEstudiante;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_documentacion", nullable = false, length = 12)
    private EstadoDocumentacion estadoDocumentacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_admision", nullable = false, length = 12)
    private EstadoAdmision estadoAdmision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE_REVISION;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_rechazo", length = 25)
    private MotivoRechazo motivoRechazo;

    @Column(columnDefinition = "text")
    private String observacion;

    @Column(name = "costos_aceptados", nullable = false)
    private boolean costosAceptados;

    @Column(name = "evaluado_por", length = 36)
    private String evaluadoPor;

    @Version
    private long version;

    protected SolicitudMatricula() {
    }

    public boolean estaPendiente() {
        return estado == EstadoSolicitud.PENDIENTE_REVISION;
    }

    /** El administrador corrige el tipo de estudiante y registra el resultado de la admisión (C-37, S-08). */
    public void registrarEvaluacion(TipoEstudiante tipo, EstadoAdmision admision) {
        this.tipoEstudiante = tipo;
        this.estadoAdmision = admision;
    }

    public void aprobar(String usuarioId) {
        this.estado = EstadoSolicitud.APROBADA;
        this.costosAceptados = true;
        this.evaluadoPor = usuarioId;
    }

    public void rechazar(MotivoRechazo motivo, String observacion, String usuarioId) {
        this.estado = EstadoSolicitud.RECHAZADA;
        this.motivoRechazo = motivo;
        this.observacion = observacion;
        this.evaluadoPor = usuarioId;
    }

    public String getCodigoSeguimiento() {
        return codigoSeguimiento;
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public String getAlumnoId() {
        return alumnoId;
    }

    public String getApoderadoId() {
        return apoderadoId;
    }

    public String getGradoId() {
        return gradoId;
    }

    public LocalDate getFechaSolicitud() {
        return fechaSolicitud;
    }

    public TipoEstudiante getTipoEstudiante() {
        return tipoEstudiante;
    }

    public EstadoDocumentacion getEstadoDocumentacion() {
        return estadoDocumentacion;
    }

    public EstadoAdmision getEstadoAdmision() {
        return estadoAdmision;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public MotivoRechazo getMotivoRechazo() {
        return motivoRechazo;
    }

    public String getObservacion() {
        return observacion;
    }

    public long getVersion() {
        return version;
    }
}
