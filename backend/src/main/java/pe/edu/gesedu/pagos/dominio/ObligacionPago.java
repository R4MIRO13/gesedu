package pe.edu.gesedu.pagos.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Compromiso de pago de un alumno matriculado (RF-07): una por línea del cronograma. */
@Entity
@Table(name = "obligacion_pago", schema = "pagos")
public class ObligacionPago extends EntidadBase {

    // Entre módulos solo ids; la matrícula es de otro módulo
    @Column(name = "matricula_id", nullable = false, length = 36)
    private String matriculaId;

    @Column(name = "anio_escolar_id", nullable = false, length = 36)
    private String anioEscolarId;

    @Column(name = "alumno_id", nullable = false, length = 36)
    private String alumnoId;

    @Column(name = "apoderado_id", nullable = false, length = 36)
    private String apoderadoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConceptoPago concepto;

    @Column(nullable = false, length = 20)
    private String periodo;

    @Column(name = "monto_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoBase;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "monto_penalidad", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoPenalidad = BigDecimal.ZERO;

    @Column(name = "penalidad_aplicada_en")
    private Instant penalidadAplicadaEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoObligacion estado = EstadoObligacion.PENDIENTE;

    @Version
    private long version;

    protected ObligacionPago() {
    }

    public ObligacionPago(String matriculaId, String anioEscolarId, String alumnoId, String apoderadoId,
                          ConceptoPago concepto, String periodo, BigDecimal montoBase, LocalDate fechaVencimiento) {
        this.matriculaId = matriculaId;
        this.anioEscolarId = anioEscolarId;
        this.alumnoId = alumnoId;
        this.apoderadoId = apoderadoId;
        this.concepto = concepto;
        this.periodo = periodo;
        this.montoBase = montoBase;
        this.fechaVencimiento = fechaVencimiento;
    }

    public String getMatriculaId() {
        return matriculaId;
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

    public ConceptoPago getConcepto() {
        return concepto;
    }

    public String getPeriodo() {
        return periodo;
    }

    public BigDecimal getMontoBase() {
        return montoBase;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public EstadoObligacion getEstado() {
        return estado;
    }

    public BigDecimal getMontoPenalidad() {
        return montoPenalidad;
    }

    public Instant getPenalidadAplicadaEn() {
        return penalidadAplicadaEn;
    }

    public long getVersion() {
        return version;
    }

    /** Importe a pagar: monto base más la penalidad ya aplicada (RF-10). */
    public BigDecimal totalAPagar() {
        return montoBase.add(montoPenalidad);
    }
}
