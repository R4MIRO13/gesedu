package pe.edu.gesedu.horarios.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Docente habilitado para dictar en un año escolar (RF-46). Sin vinculación habilitada no entra al horario. */
@Entity
@Table(name = "vinculacion_docente", schema = "horarios")
public class VinculacionDocente extends EntidadBase {

    @Column(name = "docente_id", nullable = false, length = 36)
    private String docenteId;

    @Column(name = "anio_escolar_id", nullable = false, length = 36)
    private String anioEscolarId;

    @Column(nullable = false)
    private boolean habilitado;

    @Column(name = "carga_semanal_referencia", nullable = false)
    private int cargaSemanalReferencia;

    protected VinculacionDocente() {
    }

    public VinculacionDocente(String docenteId, String anioEscolarId, boolean habilitado, int cargaSemanalReferencia) {
        this.docenteId = docenteId;
        this.anioEscolarId = anioEscolarId;
        this.habilitado = habilitado;
        this.cargaSemanalReferencia = cargaSemanalReferencia;
    }

    public void actualizar(boolean habilitado, int cargaSemanalReferencia) {
        this.habilitado = habilitado;
        this.cargaSemanalReferencia = cargaSemanalReferencia;
    }

    public String getDocenteId() {
        return docenteId;
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    public int getCargaSemanalReferencia() {
        return cargaSemanalReferencia;
    }
}
