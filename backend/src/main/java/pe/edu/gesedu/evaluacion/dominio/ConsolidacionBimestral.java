package pe.edu.gesedu.evaluacion.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Constancia de que un bimestre fue consolidado (UC-18, RF-27). Pasa a DESACTUALIZADA cuando se editan logros del
 * bimestre después de consolidarlo (S-68) y vuelve a COMPLETADA al reconsolidar.
 */
@Entity
@Table(name = "consolidacion_bimestral", schema = "evaluacion")
public class ConsolidacionBimestral extends EntidadBase {

    public static final String COMPLETADA = "COMPLETADA";
    public static final String DESACTUALIZADA = "DESACTUALIZADA";

    @Column(name = "bimestre_id", nullable = false, updatable = false, length = 36)
    private String bimestreId;

    @Column(name = "fecha_consolidacion", nullable = false)
    private Instant fechaConsolidacion;

    @Column(nullable = false, length = 14)
    private String estado = COMPLETADA;

    @Column(name = "ejecutada_por", length = 36)
    private String ejecutadaPor;

    protected ConsolidacionBimestral() {
    }

    public ConsolidacionBimestral(String bimestreId, Instant fechaConsolidacion, String ejecutadaPor) {
        this.bimestreId = bimestreId;
        this.fechaConsolidacion = fechaConsolidacion;
        this.ejecutadaPor = ejecutadaPor;
    }

    /** Devuelve {@code true} si cambió de estado (estaba al día). */
    public boolean marcarDesactualizada() {
        if (DESACTUALIZADA.equals(estado)) {
            return false;
        }
        this.estado = DESACTUALIZADA;
        return true;
    }

    /** Una nueva consolidación del mismo bimestre reemplaza a la anterior. */
    public void reconsolidar(Instant ahora, String usuarioId) {
        this.estado = COMPLETADA;
        this.fechaConsolidacion = ahora;
        this.ejecutadaPor = usuarioId;
    }

    public boolean estaDesactualizada() {
        return DESACTUALIZADA.equals(estado);
    }

    public String getBimestreId() {
        return bimestreId;
    }

    public Instant getFechaConsolidacion() {
        return fechaConsolidacion;
    }

    public String getEstado() {
        return estado;
    }

    public String getEjecutadaPor() {
        return ejecutadaPor;
    }
}
