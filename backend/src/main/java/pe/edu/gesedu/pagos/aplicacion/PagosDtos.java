package pe.edu.gesedu.pagos.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.pagos.dominio.ConceptoPago;
import pe.edu.gesedu.pagos.dominio.EstadoObligacion;
import pe.edu.gesedu.pagos.dominio.EstadoRevision;

/** Contrato de la consulta de obligaciones y pagos (UC-03, UC-04, UC-05, UC-06). Los montos viajan como texto decimal. */
public final class PagosDtos {

    private PagosDtos() {
    }

    /** Boleta emitida al validar el pago de la obligación. */
    public record BoletaDto(String numero, OffsetDateTime fechaRegistro, Dinero monto) {
    }

    /** Último comprobante presentado para la obligación y cómo va su revisión. */
    public record ComprobanteResumen(String id, EstadoRevision estado, String observacion, String numeroOperacion,
                                     LocalDate fechaPago, Dinero montoPresentado, String medioPago) {
    }

    /**
     * Una obligación con su importe actualizado: {@code total = montoBase + montoPenalidad} (UC-03, paso 5).
     * {@code puedePresentarComprobante} es falso si ya está pagada o tiene un comprobante pendiente de revisión.
     */
    public record ObligacionDto(String id, ConceptoPago concepto, String periodo, Dinero montoBase,
                                Dinero montoPenalidad, Dinero total, LocalDate fechaVencimiento,
                                EstadoObligacion estado, OffsetDateTime penalidadAplicadaEn, BoletaDto boleta,
                                ComprobanteResumen comprobante, boolean puedePresentarComprobante) {
    }

    public record ProximoVencimiento(String concepto, String periodo, LocalDate fechaVencimiento, Dinero total) {
    }

    public record ResumenObligaciones(Dinero totalPagado, Dinero totalPendiente, Dinero deudaVencida,
                                      int obligaciones, int pagadas, int pendientes, int vencidas,
                                      ProximoVencimiento proximoVencimiento) {
    }

    /** Lo que ve el apoderado de un hijo: obligaciones del año vigente y su resumen. */
    public record ObligacionesAlumno(String alumnoId, String codigoAlumno, String alumno,
                                     List<ObligacionDto> obligaciones, ResumenObligaciones resumen) {
    }
}
