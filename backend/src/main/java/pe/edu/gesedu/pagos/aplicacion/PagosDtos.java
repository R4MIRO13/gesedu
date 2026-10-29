package pe.edu.gesedu.pagos.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.pagos.dominio.ConceptoPago;
import pe.edu.gesedu.pagos.dominio.EstadoObligacion;
import pe.edu.gesedu.pagos.dominio.EstadoRevision;
import pe.edu.gesedu.transversal.archivos.GestorArchivos.UrlPrefirmada;

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

    // ---- UC-05: validación del administrador ----

    /** Fila de la bandeja de comprobantes (paso 1). */
    public record ComprobanteBandeja(String id, OffsetDateTime enviado, String estudiante, String codigoAlumno,
                                     ConceptoPago concepto, String periodo, Dinero montoPresentado,
                                     String numeroOperacion, EstadoRevision estado) {
    }

    /** La obligación que el comprobante dice pagar, con su importe base, penalidad y total (paso 3). */
    public record ObligacionRevisada(String id, String alumno, String codigoAlumno, ConceptoPago concepto,
                                     String periodo, EstadoObligacion estado, LocalDate fechaVencimiento,
                                     Dinero montoBase, Dinero montoPenalidad, Dinero total) {
    }

    /** Lo que declaró el apoderado. */
    public record DatosDeclarados(String apoderado, String numeroOperacion, LocalDate fechaPago, String medioPago,
                                  Dinero montoPresentado) {
    }

    /**
     * Comprobaciones que el sistema hace por el administrador: {@code montoCubreTotal} (el monto presentado alcanza
     * base + penalidad), {@code montoCoincide} (es exactamente el total) y {@code numeroOperacionNuevo} (ningún otro
     * comprobante pendiente o aprobado lo usa).
     */
    public record Verificaciones(boolean montoCubreTotal, boolean montoCoincide, boolean numeroOperacionNuevo,
                                 boolean obligacionPorPagar) {
    }

    public record DetalleComprobante(String id, EstadoRevision estado, OffsetDateTime enviado, String observacion,
                                     ObligacionRevisada obligacion, DatosDeclarados declarado,
                                     Verificaciones verificaciones, boolean puedeRevisar, UrlPrefirmada voucher) {
    }

    /** Boleta emitida al validar (paso 9). */
    public record ResultadoValidacion(String comprobanteId, String numeroBoleta, Dinero montoTotal,
                                      OffsetDateTime fechaRegistro, EstadoObligacion estadoObligacion) {
    }

    /** Resultado de observar o rechazar: el comprobante cambia; la obligación no. */
    public record ResultadoRevision(String comprobanteId, EstadoRevision estado, String observacion,
                                    EstadoObligacion estadoObligacion, Dinero totalPendiente) {
    }

    // ---- UC-06: historial financiero del administrador ----

    public record EstudianteHistorial(String id, String codigo, String nombre, String documento, String apoderado) {
    }

    /** Matrícula del estudiante para la cabecera del historial; nula si no tiene ninguna confirmada. */
    public record MatriculaHistorial(int anio, String grado, String seccion, String estado) {
    }

    public record ResumenHistorial(Dinero totalPagado, Dinero totalPendiente, Dinero deudaVencida,
                                   Dinero penalidadesAplicadas, int obligaciones, int pagadas, int pendientes,
                                   int vencidas) {
    }

    /** Pago registrado: la boleta con el comprobante que la originó (paso 4). */
    public record PagoRegistrado(String numeroBoleta, OffsetDateTime fechaRegistro, ConceptoPago concepto,
                                 String periodo, Dinero monto, String numeroOperacion, LocalDate fechaPago,
                                 String medioPago) {
    }

    /** Obligación vencida con su penalidad (paso 5). */
    public record DeudaVencida(String obligacionId, ConceptoPago concepto, String periodo, LocalDate fechaVencimiento,
                               Dinero montoBase, Dinero montoPenalidad, Dinero total, OffsetDateTime penalidadAplicadaEn) {
    }

    /**
     * Historial completo (todos los años): {@code sinHistorial} es FA-1 (ni obligaciones ni pagos) y
     * {@code sinPagos} es FA-2 (hay obligaciones, pero ningún pago registrado).
     */
    public record HistorialFinanciero(EstudianteHistorial estudiante, MatriculaHistorial matricula,
                                      ResumenHistorial resumen, List<ObligacionDto> obligaciones,
                                      List<PagoRegistrado> pagos, List<DeudaVencida> deudas, boolean sinHistorial,
                                      boolean sinPagos) {
    }

    public record AlumnoBusqueda(String codigo, String nombre, String documento) {
    }
}
