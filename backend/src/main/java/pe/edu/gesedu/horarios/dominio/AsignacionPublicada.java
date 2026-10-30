package pe.edu.gesedu.horarios.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Una clase de un horario publicado: copia inmutable de una asignación del borrador (RF-44). Un trigger de la base
 * rechaza cualquier {@code UPDATE} o {@code DELETE}.
 */
@Entity
@Table(name = "asignacion_publicada", schema = "horarios")
public class AsignacionPublicada extends EntidadBase {

    @Column(name = "horario_publicado_id", nullable = false, updatable = false, length = 36)
    private String horarioPublicadoId;

    @Column(name = "bloque_id", nullable = false, updatable = false, length = 36)
    private String bloqueId;

    // Relaciones entre módulos (academico y seguridad): solo ids
    @Column(name = "seccion_id", nullable = false, updatable = false, length = 36)
    private String seccionId;

    @Column(name = "curso_id", nullable = false, updatable = false, length = 36)
    private String cursoId;

    @Column(name = "docente_id", nullable = false, updatable = false, length = 36)
    private String docenteId;

    protected AsignacionPublicada() {
    }

    public AsignacionPublicada(String horarioPublicadoId, String bloqueId, String seccionId, String cursoId,
                               String docenteId) {
        this.horarioPublicadoId = horarioPublicadoId;
        this.bloqueId = bloqueId;
        this.seccionId = seccionId;
        this.cursoId = cursoId;
        this.docenteId = docenteId;
    }

    public String getHorarioPublicadoId() {
        return horarioPublicadoId;
    }

    public String getBloqueId() {
        return bloqueId;
    }

    public String getSeccionId() {
        return seccionId;
    }

    public String getCursoId() {
        return cursoId;
    }

    public String getDocenteId() {
        return docenteId;
    }
}
