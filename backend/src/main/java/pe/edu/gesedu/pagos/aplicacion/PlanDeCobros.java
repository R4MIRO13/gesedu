package pe.edu.gesedu.pagos.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.pagos.dominio.CronogramaCobro;
import pe.edu.gesedu.pagos.dominio.EstadoObligacion;
import pe.edu.gesedu.pagos.dominio.GeneradorObligaciones;
import pe.edu.gesedu.pagos.dominio.ObligacionPago;
import pe.edu.gesedu.pagos.dominio.PoliticaCobro;
import pe.edu.gesedu.pagos.infraestructura.CronogramaCobroRepository;
import pe.edu.gesedu.pagos.infraestructura.ObligacionPagoRepository;
import pe.edu.gesedu.pagos.infraestructura.PoliticaCobroRepository;

/**
 * Lo que la matrícula necesita de pagos: el resumen de costos que acepta el apoderado, la generación de las
 * obligaciones al confirmar (RF-07) y la deuda vencida de años anteriores (UC-02 FA-3).
 */
@Service
public class PlanDeCobros {

    /** Una línea del resumen de costos; los montos viajan como string decimal. */
    public record LineaCosto(String concepto, String periodo, Dinero monto, LocalDate fechaVencimiento) {
    }

    public record ResumenCostos(List<LineaCosto> lineas, Dinero total, BigDecimal porcentajePenalidad) {
    }

    private final CronogramaCobroRepository cronograma;
    private final PoliticaCobroRepository politicas;
    private final ObligacionPagoRepository obligaciones;
    private final AnioEscolarService anios;
    private final Clock reloj;

    PlanDeCobros(CronogramaCobroRepository cronograma, PoliticaCobroRepository politicas,
                 ObligacionPagoRepository obligaciones, AnioEscolarService anios, Clock reloj) {
        this.cronograma = cronograma;
        this.politicas = politicas;
        this.obligaciones = obligaciones;
        this.anios = anios;
        this.reloj = reloj;
    }

    /** Costos del año para el tipo de estudiante; vacío si el año no tiene cronograma configurado. */
    @Transactional(readOnly = true)
    public ResumenCostos resumen(String anioEscolarId, boolean nuevoIngreso) {
        List<LineaCosto> lineas = GeneradorObligaciones
                .aplicables(cronograma.findByAnioEscolarId(anioEscolarId), nuevoIngreso).stream()
                .map(l -> new LineaCosto(l.getConcepto().name(), l.getPeriodo(), Dinero.de(l.getMonto()),
                        l.getFechaVencimiento()))
                .toList();
        Dinero total = lineas.stream().map(LineaCosto::monto).reduce(Dinero.CERO, Dinero::sumar);
        BigDecimal penalidad = politicas.findByAnioEscolarId(anioEscolarId)
                .map(PoliticaCobro::getPorcentajePenalidad).orElse(BigDecimal.ZERO);
        return new ResumenCostos(lineas, total, penalidad);
    }

    /**
     * Genera todas las obligaciones de la matrícula, en la transacción del llamador (si algo falla, la matrícula
     * se revierte entera).
     *
     * @return las líneas generadas
     */
    @Transactional
    public List<LineaCosto> generarObligaciones(String matriculaId, String anioEscolarId, String alumnoId,
                                                String apoderadoId, boolean nuevoIngreso) {
        List<CronogramaCobro> lineas = cronograma.findByAnioEscolarId(anioEscolarId);
        List<ObligacionPago> generadas = GeneradorObligaciones.generar(lineas, nuevoIngreso, matriculaId,
                anioEscolarId, alumnoId, apoderadoId);
        if (generadas.isEmpty()) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA,
                    "El año escolar no tiene un cronograma de cobros configurado");
        }
        obligaciones.saveAll(generadas);
        return generadas.stream()
                .map(o -> new LineaCosto(o.getConcepto().name(), o.getPeriodo(), Dinero.de(o.getMontoBase()),
                        o.getFechaVencimiento()))
                .toList();
    }

    /** Deuda vencida del alumno en años anteriores al indicado: cero si no debe nada. */
    @Transactional(readOnly = true)
    public Dinero deudaVencidaDeAniosAnteriores(String alumnoId, int anio) {
        List<String> anteriores = anios.idsAnterioresA(anio);
        if (anteriores.isEmpty()) {
            return Dinero.CERO;
        }
        return Dinero.de(obligaciones.deudaVencida(alumnoId, anteriores, EstadoObligacion.PAGADA,
                LocalDate.now(reloj)));
    }
}
