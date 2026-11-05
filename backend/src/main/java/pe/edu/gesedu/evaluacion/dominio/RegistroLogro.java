package pe.edu.gesedu.evaluacion.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import pe.edu.gesedu.compartido.dominio.EntidadBase;
import pe.edu.gesedu.compartido.dominio.NivelLogro;

/**
 * Nivel de logro de un alumno en una competencia durante un bimestre (RF-22). Hay uno solo por
 * (alumno, competencia, bimestre), como la rejilla del wireframe (S-12, S-66): editar la celda actualiza el registro.
 */
@Entity
@Table(name = "registro_logro", schema = "evaluacion")
public class RegistroLogro extends EntidadBase {

    @Column(name = "asignacion_id", nullable = false, updatable = false, length = 36)
    private String asignacionId;

    // Referencias a otros módulos: solo ids
    @Column(name = "alumno_id", nullable = false, updatable = false, length = 36)
    private String alumnoId;

    @Column(name = "competencia_id", nullable = false, updatable = false, length = 36)
    private String competenciaId;

    @Column(name = "bimestre_id", nullable = false, updatable = false, length = 36)
    private String bimestreId;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_logro", nullable = false, length = 2)
    private NivelLogro nivelLogro;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;

    @Version
    @Column(nullable = false)
    private long version;

    protected RegistroLogro() {
    }

    public RegistroLogro(String asignacionId, String alumnoId, String competenciaId, String bimestreId,
                         NivelLogro nivelLogro, Instant fechaRegistro) {
        this.asignacionId = asignacionId;
        this.alumnoId = alumnoId;
        this.competenciaId = competenciaId;
        this.bimestreId = bimestreId;
        this.nivelLogro = nivelLogro;
        this.fechaRegistro = fechaRegistro;
    }

    /** Cambia el nivel; devuelve {@code false} si ya era ese (no hay modificación ni nueva fecha). */
    public boolean cambiarNivel(NivelLogro nuevo, Instant ahora) {
        if (nivelLogro == nuevo) {
            return false;
        }
        this.nivelLogro = nuevo;
        this.fechaRegistro = ahora;
        return true;
    }

    public String getAsignacionId() {
        return asignacionId;
    }

    public String getAlumnoId() {
        return alumnoId;
    }

    public String getCompetenciaId() {
        return competenciaId;
    }

    public String getBimestreId() {
        return bimestreId;
    }

    public NivelLogro getNivelLogro() {
        return nivelLogro;
    }

    public Instant getFechaRegistro() {
        return fechaRegistro;
    }

    public long getVersion() {
        return version;
    }
}
