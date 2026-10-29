package pe.edu.gesedu.pagos.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.pagos.aplicacion.MatriculasDeAlumnos.MatriculaDeAlumno;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.AlumnoBusqueda;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.DeudaVencida;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.EstudianteHistorial;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.HistorialFinanciero;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.MatriculaHistorial;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ObligacionDto;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.PagoRegistrado;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ResumenHistorial;
import pe.edu.gesedu.pagos.dominio.ComprobantePago;
import pe.edu.gesedu.pagos.dominio.EstadoObligacion;
import pe.edu.gesedu.pagos.dominio.EstadoRevision;
import pe.edu.gesedu.pagos.dominio.ObligacionPago;
import pe.edu.gesedu.pagos.dominio.RegistroPago;
import pe.edu.gesedu.pagos.infraestructura.ComprobantePagoRepository;
import pe.edu.gesedu.pagos.infraestructura.ObligacionPagoRepository;
import pe.edu.gesedu.pagos.infraestructura.RegistroPagoRepository;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias.AlumnoResumen;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias.ApoderadoResumen;

/**
 * Historial financiero de un estudiante para el administrador (UC-06, RF-11): obligaciones, boletas y pagos, deudas
 * vencidas y penalidades de todos los años. Solo lectura: no modifica obligaciones, pagos ni boletas.
 */
@Service
public class HistorialFinancieroService {

    private static final int MAXIMO_RESULTADOS_BUSQUEDA = 15;

    private final ObligacionPagoRepository obligaciones;
    private final ComprobantePagoRepository comprobantes;
    private final RegistroPagoRepository registros;
    private final ConsultaObligacionesService consulta;
    private final MatriculasDeAlumnos matriculas;
    private final DirectorioFamilias familias;
    private final Clock reloj;

    HistorialFinancieroService(ObligacionPagoRepository obligaciones, ComprobantePagoRepository comprobantes,
                               RegistroPagoRepository registros, ConsultaObligacionesService consulta,
                               MatriculasDeAlumnos matriculas, DirectorioFamilias familias, Clock reloj) {
        this.obligaciones = obligaciones;
        this.comprobantes = comprobantes;
        this.registros = registros;
        this.consulta = consulta;
        this.matriculas = matriculas;
        this.familias = familias;
        this.reloj = reloj;
    }

    /** Estudiantes cuyo nombre, DNI o código contiene el texto (para elegir el historial que se consulta). */
    @Transactional(readOnly = true)
    public List<AlumnoBusqueda> buscar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        List<String> ids = familias.buscarAlumnoIds(texto).stream().limit(MAXIMO_RESULTADOS_BUSQUEDA).toList();
        return familias.alumnos(ids).values().stream()
                .sorted(Comparator.comparing(AlumnoResumen::nombreCompleto))
                .map(a -> new AlumnoBusqueda(a.codigo(), a.nombreCompleto(), a.documento()))
                .toList();
    }

    @Transactional(readOnly = true)
    public HistorialFinanciero deAlumno(String codigoAlumno) {
        AlumnoResumen alumno = familias.alumnoPorCodigo(codigoAlumno).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el estudiante " + codigoAlumno));
        List<ObligacionPago> filas = obligaciones.findByAlumnoId(alumno.id());
        List<ObligacionDto> lista = consulta.armar(alumno, filas).obligaciones();

        List<String> ids = filas.stream().map(ObligacionPago::getId).toList();
        Map<String, ObligacionPago> porId = filas.stream().collect(Collectors.toMap(ObligacionPago::getId, Function.identity()));
        Map<String, ComprobantePago> aprobados = ids.isEmpty() ? Map.of()
                : comprobantes.findByObligacionIdIn(ids).stream()
                        .filter(c -> c.getEstadoRevision() == EstadoRevision.APROBADO)
                        .collect(Collectors.toMap(ComprobantePago::getId, Function.identity()));
        List<RegistroPago> boletas = ids.isEmpty() ? List.of() : registros.findByObligacionIdIn(ids);
        List<PagoRegistrado> pagos = boletas.stream()
                .sorted(Comparator.comparing(RegistroPago::getFechaRegistro).reversed())
                .map(b -> aPago(b, porId.get(b.getObligacionId()), aprobados.get(b.getComprobanteId())))
                .toList();
        List<DeudaVencida> deudas = filas.stream()
                .filter(o -> o.getEstado() == EstadoObligacion.VENCIDA)
                .sorted(Comparator.comparing(ObligacionPago::getFechaVencimiento))
                .map(this::aDeuda)
                .toList();

        ApoderadoResumen apoderado = familias.apoderados(List.of(alumno.apoderadoId())).get(alumno.apoderadoId());
        MatriculaHistorial matricula = matriculas.ultimaDe(alumno.id()).map(HistorialFinancieroService::aMatricula).orElse(null);
        return new HistorialFinanciero(
                new EstudianteHistorial(alumno.id(), alumno.codigo(), alumno.nombreCompleto(), alumno.documento(),
                        apoderado == null ? null : apoderado.nombreCompleto()),
                matricula, resumen(filas, boletas), lista, pagos, deudas, filas.isEmpty() && boletas.isEmpty(),
                !filas.isEmpty() && boletas.isEmpty());
    }

    private PagoRegistrado aPago(RegistroPago boleta, ObligacionPago o, ComprobantePago c) {
        return new PagoRegistrado(boleta.getNumeroBoleta(), local(boleta.getFechaRegistro()), o.getConcepto(),
                o.getPeriodo(), Dinero.de(boleta.getMontoTotal()), c == null ? null : c.getNumeroOperacion(),
                c == null ? null : c.getFechaPago(), c == null ? null : c.getMedioPago());
    }

    private DeudaVencida aDeuda(ObligacionPago o) {
        return new DeudaVencida(o.getId(), o.getConcepto(), o.getPeriodo(), o.getFechaVencimiento(),
                Dinero.de(o.getMontoBase()), Dinero.de(o.getMontoPenalidad()), Dinero.de(o.totalAPagar()),
                local(o.getPenalidadAplicadaEn()));
    }

    private static ResumenHistorial resumen(List<ObligacionPago> filas, List<RegistroPago> boletas) {
        Dinero pagado = boletas.stream().map(b -> Dinero.de(b.getMontoTotal())).reduce(Dinero.CERO, Dinero::sumar);
        Dinero pendiente = Dinero.CERO;
        Dinero vencido = Dinero.CERO;
        Dinero penalidades = Dinero.CERO;
        int pagadas = 0;
        int pendientes = 0;
        int vencidas = 0;
        for (ObligacionPago o : filas) {
            penalidades = penalidades.sumar(Dinero.de(o.getMontoPenalidad()));
            switch (o.getEstado()) {
                case PAGADA -> pagadas++;
                case PENDIENTE -> {
                    pendiente = pendiente.sumar(Dinero.de(o.totalAPagar()));
                    pendientes++;
                }
                case VENCIDA -> {
                    vencido = vencido.sumar(Dinero.de(o.totalAPagar()));
                    vencidas++;
                }
            }
        }
        return new ResumenHistorial(pagado, pendiente, vencido, penalidades, filas.size(), pagadas, pendientes, vencidas);
    }

    private static MatriculaHistorial aMatricula(MatriculaDeAlumno m) {
        return new MatriculaHistorial(m.anio(), m.grado(), m.seccion(), m.estado());
    }

    private OffsetDateTime local(Instant instante) {
        return instante == null ? null : instante.atZone(reloj.getZone()).toOffsetDateTime();
    }
}
