package pe.edu.gesedu.pagos.dominio;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Boleta emitida al validar un comprobante (UC-05, RF-08): {@code B001-00001}. */
@Entity
@Table(name = "registro_pago", schema = "pagos")
public class RegistroPago extends EntidadBase {

    @Column(name = "comprobante_id", nullable = false, unique = true, length = 36)
    private String comprobanteId;

    @Column(name = "obligacion_id", nullable = false, length = 36)
    private String obligacionId;

    @Column(name = "numero_boleta", nullable = false, unique = true, length = 12)
    private String numeroBoleta;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;

    @Column(name = "monto_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTotal;

    protected RegistroPago() {
    }

    public RegistroPago(String comprobanteId, String obligacionId, String numeroBoleta, Instant fechaRegistro,
                        BigDecimal montoTotal) {
        this.comprobanteId = comprobanteId;
        this.obligacionId = obligacionId;
        this.numeroBoleta = numeroBoleta;
        this.fechaRegistro = fechaRegistro;
        this.montoTotal = montoTotal;
    }

    public String getComprobanteId() {
        return comprobanteId;
    }

    public String getObligacionId() {
        return obligacionId;
    }

    public String getNumeroBoleta() {
        return numeroBoleta;
    }

    public Instant getFechaRegistro() {
        return fechaRegistro;
    }

    public BigDecimal getMontoTotal() {
        return montoTotal;
    }
}
