package pe.edu.gesedu.evaluacion.dominio;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Peso de un bimestre en la nota anual de una asignación (RF-38, S-14). Solo se guardan los pesos distintos de 1: sin
 * fila, el bimestre pesa lo mismo que los demás.
 */
@Entity
@Table(name = "peso_bimestre", schema = "evaluacion")
public class PesoBimestre extends EntidadBase {

    /** Peso que rige cuando el docente no ajustó nada. */
    public static final BigDecimal PESO_POR_DEFECTO = new BigDecimal("1.00");

    @Column(name = "asignacion_id", nullable = false, updatable = false, length = 36)
    private String asignacionId;

    @Column(name = "bimestre_id", nullable = false, updatable = false, length = 36)
    private String bimestreId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal peso;

    protected PesoBimestre() {
    }

    public PesoBimestre(String asignacionId, String bimestreId, BigDecimal peso) {
        this.asignacionId = asignacionId;
        this.bimestreId = bimestreId;
        this.peso = peso;
    }

    public void cambiarPeso(BigDecimal nuevo) {
        this.peso = nuevo;
    }

    public String getAsignacionId() {
        return asignacionId;
    }

    public String getBimestreId() {
        return bimestreId;
    }

    public BigDecimal getPeso() {
        return peso;
    }
}
