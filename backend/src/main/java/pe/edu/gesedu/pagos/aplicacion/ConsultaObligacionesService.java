package pe.edu.gesedu.pagos.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.BoletaDto;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ComprobanteResumen;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ObligacionDto;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ObligacionesAlumno;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ProximoVencimiento;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ResumenObligaciones;
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

/**
 * Consulta de obligaciones del apoderado (UC-03, RF-07, RF-09, RF-11). Solo lectura. El alcance es «mis hijos»:
 * un apoderado no ve las obligaciones de alumnos ajenos (403).
 */
@Service
public class ConsultaObligacionesService {

    private final ObligacionPagoRepository obligaciones;
    private final ComprobantePagoRepository comprobantes;
    private final RegistroPagoRepository registros;
    private final AnioEscolarService anios;
    private final DirectorioFamilias familias;
    private final ContextoUsuario contexto;
    private final Clock reloj;

    ConsultaObligacionesService(ObligacionPagoRepository obligaciones, ComprobantePagoRepository comprobantes,
                                RegistroPagoRepository registros, AnioEscolarService anios,
                                DirectorioFamilias familias, ContextoUsuario contexto, Clock reloj) {
        this.obligaciones = obligaciones;
        this.comprobantes = comprobantes;
        this.registros = registros;
        this.anios = anios;
        this.familias = familias;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    /**
     * Obligaciones de un hijo en los años no cerrados (pasos 3 a 5). Sin obligaciones devuelve la lista vacía
     * (FA-1); si el alumno no tiene matrícula vigente tampoco hay obligaciones de un año activo (FA-2).
     */
    @Transactional(readOnly = true)
    public ObligacionesAlumno deMiHijo(String alumnoId) {
        AlumnoResumen alumno = exigirHijo(alumnoId);
        List<ObligacionPago> filas = obligaciones.findByAlumnoIdAndAnioEscolarIdIn(alumnoId, anios.idsNoCerrados());
        return armar(alumno, filas);
    }

    /** Arma la vista de las obligaciones con su boleta, su último comprobante y el resumen. */
    @Transactional(readOnly = true)
    public ObligacionesAlumno armar(AlumnoResumen alumno, List<ObligacionPago> filas) {
        List<String> ids = filas.stream().map(ObligacionPago::getId).toList();
        Map<String, ComprobantePago> ultimoComprobante = ids.isEmpty() ? Map.of()
                : comprobantes.findByObligacionIdIn(ids).stream().collect(Collectors.toMap(
                        ComprobantePago::getObligacionId, c -> c,
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));
        Map<String, RegistroPago> boletas = ids.isEmpty() ? Map.of()
                : registros.findByObligacionIdIn(ids).stream().collect(Collectors.toMap(
                        RegistroPago::getObligacionId, b -> b, (a, b) -> a));

        List<ObligacionDto> lista = filas.stream()
                .sorted(Comparator.comparing(ObligacionPago::getFechaVencimiento)
                        .thenComparing(o -> o.getConcepto().ordinal()))
                .map(o -> aDto(o, ultimoComprobante.get(o.getId()), boletas.get(o.getId())))
                .toList();
        return new ObligacionesAlumno(alumno.id(), alumno.codigo(), alumno.nombreCompleto(), lista, resumen(filas));
    }

    private ObligacionDto aDto(ObligacionPago o, ComprobantePago comprobante, RegistroPago boleta) {
        boolean pendienteDeRevision = comprobante != null && comprobante.getEstadoRevision() == EstadoRevision.PENDIENTE;
        return new ObligacionDto(o.getId(), o.getConcepto(), o.getPeriodo(), Dinero.de(o.getMontoBase()),
                Dinero.de(o.getMontoPenalidad()), Dinero.de(o.totalAPagar()), o.getFechaVencimiento(), o.getEstado(),
                local(o.getPenalidadAplicadaEn()),
                boleta == null ? null : new BoletaDto(boleta.getNumeroBoleta(), local(boleta.getFechaRegistro()),
                        Dinero.de(boleta.getMontoTotal())),
                comprobante == null ? null : new ComprobanteResumen(comprobante.getId(), comprobante.getEstadoRevision(),
                        comprobante.getObservacion(), comprobante.getNumeroOperacion(), comprobante.getFechaPago(),
                        Dinero.de(comprobante.getMontoPresentado()), comprobante.getMedioPago()),
                o.getEstado() != EstadoObligacion.PAGADA && !pendienteDeRevision);
    }

    private ResumenObligaciones resumen(List<ObligacionPago> filas) {
        Dinero pagado = Dinero.CERO;
        Dinero pendiente = Dinero.CERO;
        Dinero vencido = Dinero.CERO;
        int pagadas = 0;
        int pendientes = 0;
        int vencidas = 0;
        for (ObligacionPago o : filas) {
            Dinero total = Dinero.de(o.totalAPagar());
            switch (o.getEstado()) {
                case PAGADA -> {
                    pagado = pagado.sumar(total);
                    pagadas++;
                }
                case PENDIENTE -> {
                    pendiente = pendiente.sumar(total);
                    pendientes++;
                }
                case VENCIDA -> {
                    vencido = vencido.sumar(total);
                    vencidas++;
                }
            }
        }
        ProximoVencimiento proximo = filas.stream()
                .filter(o -> o.getEstado() == EstadoObligacion.PENDIENTE)
                .min(Comparator.comparing(ObligacionPago::getFechaVencimiento)
                        .thenComparing(o -> o.getConcepto().ordinal()))
                .map(o -> new ProximoVencimiento(o.getConcepto().name(), o.getPeriodo(), o.getFechaVencimiento(),
                        Dinero.de(o.totalAPagar())))
                .orElse(null);
        return new ResumenObligaciones(pagado, pendiente.sumar(vencido), vencido, filas.size(), pagadas, pendientes,
                vencidas, proximo);
    }

    /** El alumno debe ser hijo del apoderado que consulta; si no, queda fuera de su alcance. */
    AlumnoResumen exigirHijo(String alumnoId) {
        return contexto.idUsuarioActual().flatMap(familias::apoderadoDeUsuario)
                .flatMap(ap -> familias.hijosDe(ap.id()).stream().filter(h -> h.id().equals(alumnoId)).findFirst())
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE,
                        "El estudiante no está a tu cargo"));
    }

    private OffsetDateTime local(Instant instante) {
        return instante == null ? null : instante.atZone(reloj.getZone()).toOffsetDateTime();
    }
}
