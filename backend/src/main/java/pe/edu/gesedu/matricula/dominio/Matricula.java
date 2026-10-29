package pe.edu.gesedu.matricula.dominio;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Matrícula de un alumno en una sección del año. A lo sumo una confirmada por alumno y año (RF-05, RNF-09). */
@Entity
@Table(name = "matricula", schema = "matricula")
public class Matricula extends EntidadBase {

    @Column(name = "solicitud_id", nullable = false, unique = true, length = 36)
    private String solicitudId;

    @Column(name = "alumno_id", nullable = false, length = 36)
    private String alumnoId;

    @Column(name = "anio_escolar_id", nullable = false, length = 36)
    private String anioEscolarId;

    @Column(name = "seccion_id", nullable = false, length = 36)
    private String seccionId;

    @Column(name = "fecha_matricula", nullable = false)
    private LocalDate fechaMatricula;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoMatricula estado = EstadoMatricula.CONFIRMADA;

    @Column(name = "confirmada_por", length = 36)
    private String confirmadaPor;

    @Version
    private long version;

    protected Matricula() {
    }

    public Matricula(String solicitudId, String alumnoId, String anioEscolarId, String seccionId,
                     LocalDate fechaMatricula, String confirmadaPor) {
        this.solicitudId = solicitudId;
        this.alumnoId = alumnoId;
        this.anioEscolarId = anioEscolarId;
        this.seccionId = seccionId;
        this.fechaMatricula = fechaMatricula;
        this.confirmadaPor = confirmadaPor;
    }

    public String getSolicitudId() {
        return solicitudId;
    }

    public String getAlumnoId() {
        return alumnoId;
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public String getSeccionId() {
        return seccionId;
    }

    /** El administrador traslada al alumno a otra sección del mismo año (RF-05). */
    public void cambiarSeccion(String seccionId) {
        this.seccionId = seccionId;
    }

    public long getVersion() {
        return version;
    }

    public LocalDate getFechaMatricula() {
        return fechaMatricula;
    }

    public EstadoMatricula getEstado() {
        return estado;
    }
}
