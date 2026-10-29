package pe.edu.gesedu.pagos.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ComprobanteResumen;
import pe.edu.gesedu.pagos.dominio.ComprobantePago;
import pe.edu.gesedu.pagos.dominio.EstadoObligacion;
import pe.edu.gesedu.pagos.dominio.EstadoRevision;
import pe.edu.gesedu.pagos.dominio.ObligacionPago;
import pe.edu.gesedu.pagos.infraestructura.ComprobantePagoRepository;
import pe.edu.gesedu.pagos.infraestructura.ObligacionPagoRepository;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias.AlumnoResumen;
import pe.edu.gesedu.transversal.archivos.GestorArchivos;
import pe.edu.gesedu.transversal.archivos.GestorArchivos.UrlPrefirmada;
import pe.edu.gesedu.transversal.archivos.ReglasArchivo;

/**
 * El apoderado presenta el comprobante de un pago hecho fuera del sistema (UC-04, RF-08). Queda PENDIENTE de revisión
 * y la obligación no cambia hasta que el administrador lo valida (UC-05).
 */
@Service
public class PresentacionComprobanteService {

    /** URL para subir el voucher y la clave con la que se declara después en el comprobante. */
    public record ArchivoSubida(String claveObjeto, UrlPrefirmada subida) {
    }

    /** Datos del comprobante tal como los ingresa el apoderado (paso 4). */
    public record DatosComprobante(String numeroOperacion, LocalDate fechaPago, Dinero montoPagado, String medioPago,
                                   String claveObjeto, String contentType, long tamanoBytes) {
    }

    private final ObligacionPagoRepository obligaciones;
    private final ComprobantePagoRepository comprobantes;
    private final ConsultaObligacionesService consulta;
    private final GestorArchivos archivos;
    private final Clock reloj;

    PresentacionComprobanteService(ObligacionPagoRepository obligaciones, ComprobantePagoRepository comprobantes,
                                   ConsultaObligacionesService consulta, GestorArchivos archivos, Clock reloj) {
        this.obligaciones = obligaciones;
        this.comprobantes = comprobantes;
        this.consulta = consulta;
        this.archivos = archivos;
        this.reloj = reloj;
    }

    /** URL prefirmada de 5 minutos para subir el voucher (PDF, JPG o PNG de hasta 5 MB). */
    @Transactional(readOnly = true)
    public ArchivoSubida solicitarSubida(String obligacionId, String nombreArchivo, String contentType, long tamanoBytes) {
        AlumnoResumen alumno = consulta.exigirHijo(obligacion(obligacionId).getAlumnoId());
        ReglasArchivo.validar(contentType, tamanoBytes);
        String clave = ReglasArchivo.nuevaClave(prefijo(alumno.apoderadoId()), nombreArchivo);
        return new ArchivoSubida(clave, archivos.urlSubida(clave, contentType, tamanoBytes));
    }

    /**
     * Registra el comprobante como PENDIENTE (pasos 5 y 6).
     *
     * @throws ExcepcionNegocio {@code OBLIGACION_YA_PAGADA} (FA-2), {@code COMPROBANTE_PENDIENTE},
     *                          {@code NUMERO_OPERACION_DUPLICADO}, {@code ARCHIVO_INVALIDO} o {@code VALIDACION_INVALIDA}
     */
    @Transactional
    @Auditable(accion = "PRESENTAR_COMPROBANTE", modulo = "PAGOS", tipoEntidad = "ComprobantePago",
            idEntidad = "#result.id", descripcion = "Comprobante de pago presentado por el apoderado")
    public ComprobanteResumen presentar(String obligacionId, DatosComprobante datos) {
        ObligacionPago obligacion = obligacion(obligacionId);
        AlumnoResumen alumno = consulta.exigirHijo(obligacion.getAlumnoId());
        if (obligacion.getEstado() == EstadoObligacion.PAGADA) {
            throw new ExcepcionNegocio(CodigoError.OBLIGACION_YA_PAGADA,
                    "La obligación ya está pagada: no corresponde presentar un nuevo comprobante");
        }
        validarDatos(datos);
        ReglasArchivo.validar(datos.contentType(), datos.tamanoBytes());
        if (!datos.claveObjeto().startsWith(prefijo(alumno.apoderadoId()) + "/") || !archivos.existe(datos.claveObjeto())) {
            throw new ExcepcionNegocio(CodigoError.ARCHIVO_INVALIDO,
                    "El voucher no se subió correctamente; súbelo de nuevo");
        }
        if (comprobantes.existsByObligacionIdAndEstadoRevision(obligacionId, EstadoRevision.PENDIENTE)) {
            throw pendiente();
        }
        if (comprobantes.existsByNumeroOperacionAndEstadoRevisionIn(datos.numeroOperacion(),
                List.of(EstadoRevision.PENDIENTE, EstadoRevision.APROBADO))) {
            throw duplicado();
        }
        try {
            ComprobantePago c = comprobantes.saveAndFlush(new ComprobantePago(obligacionId, alumno.apoderadoId(),
                    datos.numeroOperacion(), datos.fechaPago(), datos.montoPagado().valor(), datos.medioPago().trim(),
                    datos.claveObjeto()));
            return new ComprobanteResumen(c.getId(), c.getEstadoRevision(), c.getObservacion(), c.getNumeroOperacion(),
                    c.getFechaPago(), Dinero.de(c.getMontoPresentado()), c.getMedioPago());
        } catch (DataIntegrityViolationException e) {
            // Envíos simultáneos: los índices únicos parciales dejan pasar solo uno
            String causa = String.valueOf(e.getMostSpecificCause().getMessage());
            throw causa.contains("ux_comprobante_operacion_vigente") ? duplicado()
                    : causa.contains("ux_comprobante_aprobado") ? new ExcepcionNegocio(CodigoError.OBLIGACION_YA_PAGADA)
                    : pendiente();
        }
    }

    private void validarDatos(DatosComprobante datos) {
        if (datos.fechaPago().isAfter(LocalDate.now(reloj))) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "La fecha de pago no puede ser futura");
        }
        if (datos.montoPagado().valor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "El monto pagado debe ser mayor que cero");
        }
    }

    private ObligacionPago obligacion(String id) {
        // Una obligación inexistente y una ajena responden igual: no se revela cuáles existen
        return obligaciones.findById(id).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "La obligación no está a tu cargo"));
    }

    private static String prefijo(String apoderadoId) {
        return "comprobantes/" + apoderadoId;
    }

    private static ExcepcionNegocio pendiente() {
        return new ExcepcionNegocio(CodigoError.COMPROBANTE_PENDIENTE,
                "Esta obligación ya tiene un comprobante pendiente de revisión");
    }

    private static ExcepcionNegocio duplicado() {
        return new ExcepcionNegocio(CodigoError.NUMERO_OPERACION_DUPLICADO,
                "El número de operación ya fue presentado en otro comprobante");
    }
}
