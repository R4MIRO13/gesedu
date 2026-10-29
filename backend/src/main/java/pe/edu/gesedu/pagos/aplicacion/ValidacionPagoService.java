package pe.edu.gesedu.pagos.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.compartido.paginacion.Pagina;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ComprobanteBandeja;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.DatosDeclarados;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.DetalleComprobante;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ObligacionRevisada;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ResultadoRevision;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ResultadoValidacion;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.Verificaciones;
import pe.edu.gesedu.pagos.dominio.ComprobantePago;
import pe.edu.gesedu.pagos.dominio.EstadoObligacion;
import pe.edu.gesedu.pagos.dominio.EstadoRevision;
import pe.edu.gesedu.pagos.dominio.ObligacionPago;
import pe.edu.gesedu.pagos.dominio.RegistroPago;
import pe.edu.gesedu.pagos.infraestructura.ComprobantePagoRepository;
import pe.edu.gesedu.pagos.infraestructura.ObligacionPagoRepository;
import pe.edu.gesedu.pagos.infraestructura.RegistroPagoRepository;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias.AlumnoResumen;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias.ApoderadoResumen;
import pe.edu.gesedu.transversal.archivos.GestorArchivos;
import pe.edu.gesedu.transversal.notificaciones.Notificacion;
import pe.edu.gesedu.transversal.notificaciones.Notificaciones;

/**
 * Validación de comprobantes por el administrador (UC-05, RF-08 a RF-10, RNF-10, RNF-11). Validar, observar y
 * rechazar bloquean la obligación con {@code SELECT … FOR UPDATE}: dos operaciones sobre la misma obligación se
 * ejecutan una tras otra. Validar es una sola transacción (boleta, comprobante aprobado, obligación pagada y
 * auditoría) y el aviso al apoderado sale después del commit (C-41).
 */
@Service
public class ValidacionPagoService {

    private static final String SERIE_BOLETA = "B001";

    private final ComprobantePagoRepository comprobantes;
    private final ObligacionPagoRepository obligaciones;
    private final RegistroPagoRepository registros;
    private final DirectorioFamilias familias;
    private final ContextoUsuario contexto;
    private final GestorArchivos archivos;
    private final Notificaciones notificaciones;
    private final Clock reloj;

    ValidacionPagoService(ComprobantePagoRepository comprobantes, ObligacionPagoRepository obligaciones,
                          RegistroPagoRepository registros, DirectorioFamilias familias, ContextoUsuario contexto,
                          GestorArchivos archivos, Notificaciones notificaciones, Clock reloj) {
        this.comprobantes = comprobantes;
        this.obligaciones = obligaciones;
        this.registros = registros;
        this.familias = familias;
        this.contexto = contexto;
        this.archivos = archivos;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
    }

    // ---- Consulta ----------------------------------------------------------------------------------------

    /** Bandeja paginada (paso 1): por defecto, los pendientes de revisión, del más antiguo al más reciente. */
    @Transactional(readOnly = true)
    public Pagina<ComprobanteBandeja> listar(EstadoRevision estado, Pageable paginacion) {
        Specification<ComprobantePago> filtro = (raiz, consulta, cb) -> cb.equal(raiz.get("estadoRevision"), estado);
        var pagina = comprobantes.findAll(filtro, paginacion);
        Map<String, ObligacionPago> obligacionesPorId = obligaciones
                .findByIdIn(pagina.getContent().stream().map(ComprobantePago::getObligacionId).distinct().toList())
                .stream().collect(Collectors.toMap(ObligacionPago::getId, Function.identity()));
        Map<String, AlumnoResumen> alumnos = familias.alumnos(
                obligacionesPorId.values().stream().map(ObligacionPago::getAlumnoId).distinct().toList());
        return Pagina.de(pagina, c -> {
            ObligacionPago o = obligacionesPorId.get(c.getObligacionId());
            AlumnoResumen a = alumnos.get(o.getAlumnoId());
            return new ComprobanteBandeja(c.getId(), local(c.getCreatedAt()), a == null ? "—" : a.nombreCompleto(),
                    a == null ? "—" : a.codigo(), o.getConcepto(), o.getPeriodo(), Dinero.de(c.getMontoPresentado()),
                    c.getNumeroOperacion(), c.getEstadoRevision());
        });
    }

    /** Detalle para revisar (pasos 2 a 4): la obligación con su penalidad, lo declarado y las comprobaciones. */
    @Transactional(readOnly = true)
    public DetalleComprobante obtener(String id) {
        ComprobantePago c = buscar(id);
        ObligacionPago o = obligaciones.findById(c.getObligacionId()).orElseThrow();
        AlumnoResumen alumno = familias.alumnos(List.of(o.getAlumnoId())).get(o.getAlumnoId());
        ApoderadoResumen apoderado = familias.apoderados(List.of(c.getApoderadoId())).get(c.getApoderadoId());
        Dinero total = Dinero.de(o.totalAPagar());
        Dinero presentado = Dinero.de(c.getMontoPresentado());
        boolean numeroNuevo = !otroUsaElNumero(c);
        boolean porPagar = o.getEstado() != EstadoObligacion.PAGADA;
        return new DetalleComprobante(c.getId(), c.getEstadoRevision(), local(c.getCreatedAt()), c.getObservacion(),
                new ObligacionRevisada(o.getId(), alumno == null ? "—" : alumno.nombreCompleto(),
                        alumno == null ? "—" : alumno.codigo(), o.getConcepto(), o.getPeriodo(), o.getEstado(),
                        o.getFechaVencimiento(), Dinero.de(o.getMontoBase()), Dinero.de(o.getMontoPenalidad()), total),
                new DatosDeclarados(apoderado == null ? "—" : apoderado.nombreCompleto(), c.getNumeroOperacion(),
                        c.getFechaPago(), c.getMedioPago(), presentado),
                new Verificaciones(!presentado.esMenorQue(total), presentado.compareTo(total) == 0, numeroNuevo, porPagar),
                c.estaPendiente() && porPagar, urlVoucher(c));
    }

    // ---- Revisión ----------------------------------------------------------------------------------------

    /**
     * Valida el pago (pasos 5 a 9): emite la boleta, aprueba el comprobante y deja la obligación PAGADA. El importe
     * debe coincidir con base + penalidad. Si algo falla después de insertar la boleta, todo se revierte.
     *
     * @throws ExcepcionNegocio {@code OBLIGACION_YA_PAGADA}, {@code MONTO_INSUFICIENTE} (FA-2: debe observarse),
     *                          {@code SOLICITUD_INVALIDA} si el monto excede el total, o
     *                          {@code CONFLICTO_CONCURRENCIA} si el comprobante ya no está pendiente
     */
    @Transactional
    @Auditable(accion = "VALIDAR_PAGO", modulo = "PAGOS", tipoEntidad = "RegistroPago",
            idEntidad = "#result.numeroBoleta", descripcion = "Validación de comprobante y registro de la boleta de pago")
    public ResultadoValidacion validar(String comprobanteId) {
        ObligacionPago o = bloquearObligacionDe(comprobanteId);
        if (o.getEstado() == EstadoObligacion.PAGADA) {
            throw new ExcepcionNegocio(CodigoError.OBLIGACION_YA_PAGADA);
        }
        ComprobantePago c = exigirPendiente(buscar(comprobanteId));
        Dinero total = Dinero.de(o.totalAPagar());
        Dinero presentado = Dinero.de(c.getMontoPresentado());
        if (presentado.esMenorQue(total)) {
            throw new ExcepcionNegocio(CodigoError.MONTO_INSUFICIENTE, "El monto presentado (S/ " + presentado
                    + ") no cubre el importe total (S/ " + total + "); observa el comprobante").con("total", total);
        }
        if (presentado.compareTo(total) > 0) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA, "El monto presentado (S/ " + presentado
                    + ") excede el importe total (S/ " + total + "); observa el comprobante").con("total", total);
        }

        Instant ahora = reloj.instant();
        String numero = "%s-%05d".formatted(SERIE_BOLETA, registros.siguienteNumeroDeBoleta());
        RegistroPago boleta = registros.saveAndFlush(new RegistroPago(c.getId(), o.getId(), numero, ahora, total.valor()));
        String usuario = contexto.idUsuarioActual().orElse(null);
        EstadoObligacion anterior = o.getEstado();
        c.aprobar(usuario, ahora);
        o.marcarPagada();

        ContextoAuditoria.registrarCambio("estadoObligacion", anterior, EstadoObligacion.PAGADA);
        ContextoAuditoria.registrarCambio("estadoComprobante", EstadoRevision.PENDIENTE, EstadoRevision.APROBADO);
        ContextoAuditoria.registrarCambio("boleta", null, numero);
        ContextoAuditoria.registrarCambio("montoTotal", null, total);

        AlumnoResumen alumno = familias.alumnos(List.of(o.getAlumnoId())).get(o.getAlumnoId());
        avisar(c, "Pago registrado: " + nombre(o), "pago-registrado", alumno, o,
                "Boleta " + numero + "\nImporte pagado: S/ " + total + "\nN.° de operación: " + c.getNumeroOperacion());
        return new ResultadoValidacion(c.getId(), boleta.getNumeroBoleta(), total, local(ahora), o.getEstado());
    }

    /**
     * Observa el comprobante (FA-2): la obligación sigue igual y el apoderado recibe el importe total pendiente para
     * que presente uno nuevo.
     */
    @Transactional
    @Auditable(accion = "OBSERVAR_COMPROBANTE", modulo = "PAGOS", tipoEntidad = "ComprobantePago", idEntidad = "#comprobanteId",
            descripcion = "Observación de un comprobante de pago")
    public ResultadoRevision observar(String comprobanteId, String observacion) {
        return revisar(comprobanteId, observacion, true);
    }

    /** Rechaza el comprobante (FA-1): no corresponde al estudiante o a la obligación. */
    @Transactional
    @Auditable(accion = "RECHAZAR_COMPROBANTE", modulo = "PAGOS", tipoEntidad = "ComprobantePago", idEntidad = "#comprobanteId",
            descripcion = "Rechazo de un comprobante de pago")
    public ResultadoRevision rechazar(String comprobanteId, String motivo) {
        return revisar(comprobanteId, motivo, false);
    }

    private ResultadoRevision revisar(String comprobanteId, String nota, boolean observar) {
        ObligacionPago o = bloquearObligacionDe(comprobanteId);
        ComprobantePago c = exigirPendiente(buscar(comprobanteId));
        Instant ahora = reloj.instant();
        String usuario = contexto.idUsuarioActual().orElse(null);
        String texto = nota.trim();
        if (observar) {
            c.observar(usuario, ahora, texto);
        } else {
            c.rechazar(usuario, ahora, texto);
        }
        ContextoAuditoria.registrarCambio("estadoComprobante", EstadoRevision.PENDIENTE, c.getEstadoRevision());
        ContextoAuditoria.registrarCambio("observacion", null, texto);

        Dinero total = Dinero.de(o.totalAPagar());
        AlumnoResumen alumno = familias.alumnos(List.of(o.getAlumnoId())).get(o.getAlumnoId());
        if (observar) {
            avisar(c, "Comprobante observado: " + nombre(o), "comprobante-observado", alumno, o,
                    texto + "\nImporte total pendiente que debes regularizar: S/ " + total);
        } else {
            avisar(c, "Comprobante rechazado: " + nombre(o), "comprobante-rechazado", alumno, o, texto);
        }
        return new ResultadoRevision(c.getId(), c.getEstadoRevision(), texto, o.getEstado(), total);
    }

    // ---- Apoyo -------------------------------------------------------------------------------------------

    private ComprobantePago buscar(String id) {
        return comprobantes.findById(id).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el comprobante"));
    }

    /** Bloquea la obligación del comprobante antes de leer el estado de este: así se ve lo último que se confirmó. */
    private ObligacionPago bloquearObligacionDe(String comprobanteId) {
        String obligacionId = comprobantes.obligacionIdDe(comprobanteId).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el comprobante"));
        return obligaciones.bloquear(obligacionId).orElseThrow();
    }

    private static ComprobantePago exigirPendiente(ComprobantePago c) {
        if (!c.estaPendiente()) {
            throw new ExcepcionNegocio(CodigoError.CONFLICTO_CONCURRENCIA,
                    "El comprobante ya fue revisado: " + c.getEstadoRevision());
        }
        return c;
    }

    private boolean otroUsaElNumero(ComprobantePago c) {
        return comprobantes.findAll((raiz, consulta, cb) -> cb.and(
                cb.equal(raiz.get("numeroOperacion"), c.getNumeroOperacion()),
                cb.notEqual(raiz.get("id"), c.getId()),
                raiz.get("estadoRevision").in(EstadoRevision.PENDIENTE, EstadoRevision.APROBADO))).size() > 0;
    }

    private GestorArchivos.UrlPrefirmada urlVoucher(ComprobantePago c) {
        String clave = c.getReferenciaComprobante();
        String archivo = clave.substring(clave.lastIndexOf('/') + 1);
        // Las claves son «prefijo/<uuid>-<nombre>»: el nombre original viene después del uuid
        String nombre = archivo.length() > 37 && archivo.charAt(36) == '-' ? archivo.substring(37) : archivo;
        String minusculas = nombre.toLowerCase();
        String tipo = minusculas.endsWith(".png") ? "image/png"
                : minusculas.endsWith(".jpg") || minusculas.endsWith(".jpeg") ? "image/jpeg" : "application/pdf";
        return archivos.urlDescarga(clave, tipo, nombre);
    }

    private void avisar(ComprobantePago c, String asunto, String plantilla, AlumnoResumen alumno, ObligacionPago o,
                        String detalle) {
        ApoderadoResumen apoderado = familias.apoderados(List.of(c.getApoderadoId())).get(c.getApoderadoId());
        if (apoderado == null || apoderado.correo() == null) {
            return;
        }
        notificaciones.notificar(new Notificacion(apoderado.correo(), asunto, plantilla, Map.of(
                "nombre", apoderado.nombreCompleto(),
                "alumno", alumno == null ? "—" : alumno.nombreCompleto(),
                "concepto", nombre(o),
                "detalle", detalle)));
    }

    private static String nombre(ObligacionPago o) {
        String concepto = switch (o.getConcepto()) {
            case MATRICULA -> "Matrícula";
            case PENSION -> "Pensión";
            case CUOTA_INGRESO -> "Cuota de ingreso";
        };
        return concepto + " " + o.getPeriodo();
    }

    private OffsetDateTime local(Instant instante) {
        return instante.atZone(reloj.getZone()).toOffsetDateTime();
    }
}
