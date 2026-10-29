package pe.edu.gesedu.pagos.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Una línea del cronograma de cobros del año: matrícula, cada pensión o la cuota de ingreso (RF-06). */
@Entity
@Table(name = "cronograma_cobro", schema = "pagos")
public class CronogramaCobro extends EntidadBase {

    @Column(name = "anio_escolar_id", nullable = false, length = 36)
    private String anioEscolarId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConceptoPago concepto;

    @Column(nullable = false, length = 20)
    private String periodo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    protected CronogramaCobro() {
    }

    public CronogramaCobro(String anioEscolarId, ConceptoPago concepto, String periodo, BigDecimal monto,
                           LocalDate fechaVencimiento) {
        this.anioEscolarId = anioEscolarId;
        this.concepto = concepto;
        this.periodo = periodo;
        this.monto = monto;
        this.fechaVencimiento = fechaVencimiento;
    }

    public void actualizar(BigDecimal monto, LocalDate fechaVencimiento) {
        this.monto = monto;
        this.fechaVencimiento = fechaVencimiento;
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public ConceptoPago getConcepto() {
        return concepto;
    }

    public String getPeriodo() {
        return periodo;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }
}
