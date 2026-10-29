package pe.edu.gesedu.pagos.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Comprobante que el apoderado presenta para una obligación (UC-04, RF-08). Nace PENDIENTE; el administrador lo
 * aprueba, observa o rechaza (UC-05). Presentarlo no cambia la obligación.
 */
@Entity
@Table(name = "comprobante_pago", schema = "pagos")
public class ComprobantePago extends EntidadBase {

    @Column(name = "obligacion_id", nullable = false, length = 36)
    private String obligacionId;

    @Column(name = "apoderado_id", nullable = false, length = 36)
    private String apoderadoId;

    @Column(name = "numero_operacion", nullable = false, length = 40)
    private String numeroOperacion;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Column(name = "monto_presentado", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoPresentado;

    @Column(name = "medio_pago", nullable = false, length = 30)
    private String medioPago;

    /** Clave del voucher en el Gestor de Archivos. */
    @Column(name = "referencia_comprobante", nullable = false)
    private String referenciaComprobante;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_revision", nullable = false, length = 10)
    private EstadoRevision estadoRevision = EstadoRevision.PENDIENTE;

    @Column(columnDefinition = "text")
    private String observacion;

    @Column(name = "revisado_por", length = 36)
    private String revisadoPor;

    @Column(name = "revisado_en")
    private Instant revisadoEn;

    protected ComprobantePago() {
    }

    public ComprobantePago(String obligacionId, String apoderadoId, String numeroOperacion, LocalDate fechaPago,
                           BigDecimal montoPresentado, String medioPago, String referenciaComprobante) {
        this.obligacionId = obligacionId;
        this.apoderadoId = apoderadoId;
        this.numeroOperacion = numeroOperacion;
        this.fechaPago = fechaPago;
        this.montoPresentado = montoPresentado;
        this.medioPago = medioPago;
        this.referenciaComprobante = referenciaComprobante;
    }

    public String getObligacionId() {
        return obligacionId;
    }

    public String getApoderadoId() {
        return apoderadoId;
    }

    public String getNumeroOperacion() {
        return numeroOperacion;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public BigDecimal getMontoPresentado() {
        return montoPresentado;
    }

    public String getMedioPago() {
        return medioPago;
    }

    public String getReferenciaComprobante() {
        return referenciaComprobante;
    }

    public EstadoRevision getEstadoRevision() {
        return estadoRevision;
    }

    public String getObservacion() {
        return observacion;
    }

    public String getRevisadoPor() {
        return revisadoPor;
    }

    public Instant getRevisadoEn() {
        return revisadoEn;
    }
}
