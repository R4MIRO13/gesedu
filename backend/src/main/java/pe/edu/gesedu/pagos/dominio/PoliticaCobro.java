package pe.edu.gesedu.pagos.dominio;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Política de cobro del año: porcentaje de penalidad por vencimiento (RF-06, RF-10). */
@Entity
@Table(name = "politica_cobro", schema = "pagos")
public class PoliticaCobro extends EntidadBase {

    @Column(name = "anio_escolar_id", nullable = false, unique = true, length = 36)
    private String anioEscolarId;

    @Column(name = "porcentaje_penalidad", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajePenalidad;

    protected PoliticaCobro() {
    }

    public PoliticaCobro(String anioEscolarId, BigDecimal porcentajePenalidad) {
        this.anioEscolarId = anioEscolarId;
        this.porcentajePenalidad = porcentajePenalidad;
    }

    public void actualizar(BigDecimal porcentajePenalidad) {
        this.porcentajePenalidad = porcentajePenalidad;
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public BigDecimal getPorcentajePenalidad() {
        return porcentajePenalidad;
    }
}
