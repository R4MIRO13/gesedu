package pe.edu.gesedu.pagos.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.pagos.dominio.CalculoPenalidad;
import pe.edu.gesedu.pagos.dominio.EstadoObligacion;
import pe.edu.gesedu.pagos.dominio.ObligacionPago;
import pe.edu.gesedu.pagos.dominio.PoliticaCobro;
import pe.edu.gesedu.pagos.infraestructura.ObligacionPagoRepository;
import pe.edu.gesedu.pagos.infraestructura.PoliticaCobroRepository;

/**
 * Vencimientos y penalidades (RF-09, RF-10, S-10): las obligaciones cuyo vencimiento ya pasó y no están pagadas
 * pasan a VENCIDA y reciben su penalidad, una sola vez. Es idempotente: una segunda ejecución del mismo día no
 * encuentra nada por procesar. Corre como «Sistema» (sin usuario autenticado) y bloquea cada obligación antes de
 * tocarla, para no pisar una validación de pago en curso (UC-05).
 */
@Service
public class VencimientosService {

    /** Resultado del proceso: cuántas obligaciones pasaron a VENCIDA, cuántas recibieron penalidad y su suma. */
    public record ResultadoVencimientos(int obligacionesVencidas, int penalidadesAplicadas, Dinero totalPenalidades) {
    }

    private final ObligacionPagoRepository obligaciones;
    private final PoliticaCobroRepository politicas;
    private final Clock reloj;

    VencimientosService(ObligacionPagoRepository obligaciones, PoliticaCobroRepository politicas, Clock reloj) {
        this.obligaciones = obligaciones;
        this.politicas = politicas;
        this.reloj = reloj;
    }

    /**
     * Procesa las obligaciones con vencimiento anterior a {@code hoy}. Solo se audita si cambió algo.
     */
    @Transactional
    @Auditable(accion = "APLICAR_VENCIMIENTOS", modulo = "PAGOS", tipoEntidad = "ObligacionPago",
            descripcion = "Vencimiento de obligaciones y cálculo de penalidades (proceso diario)",
            condicion = "#result.obligacionesVencidas > 0 or #result.penalidadesAplicadas > 0")
    public ResultadoVencimientos procesar(LocalDate hoy) {
        Instant ahora = reloj.instant();
        Map<String, BigDecimal> porcentajes = new HashMap<>();
        int vencidas = 0;
        int penalizadas = 0;
        Dinero total = Dinero.CERO;

        for (String id : obligaciones.porVencer(hoy, EstadoObligacion.PAGADA, EstadoObligacion.PENDIENTE)) {
            // El bloqueo espera a una validación de pago en curso; con la obligación ya pagada no se toca
            ObligacionPago o = obligaciones.bloquear(id).orElse(null);
            if (o == null || !correspondeProcesar(o, hoy)) {
                continue;
            }
            boolean eraPendiente = o.getEstado() == EstadoObligacion.PENDIENTE;
            boolean sinPenalidad = o.getPenalidadAplicadaEn() == null;
            BigDecimal porcentaje = porcentajes.computeIfAbsent(o.getAnioEscolarId(), this::porcentajeDelAnio);
            Dinero penalidad = porcentaje == null ? null : CalculoPenalidad.calcular(o.getMontoBase(), porcentaje);
            o.vencer(penalidad == null ? null : penalidad.valor(), ahora);
            if (eraPendiente) {
                vencidas++;
            }
            if (sinPenalidad && penalidad != null) {
                penalizadas++;
                total = total.sumar(penalidad);
            }
        }

        ContextoAuditoria.registrarCambio("obligacionesVencidas", null, vencidas);
        ContextoAuditoria.registrarCambio("penalidadesAplicadas", null, penalizadas);
        ContextoAuditoria.registrarCambio("totalPenalidades", null, total);
        return new ResultadoVencimientos(vencidas, penalizadas, total);
    }

    private static boolean correspondeProcesar(ObligacionPago o, LocalDate hoy) {
        return o.getEstado() != EstadoObligacion.PAGADA && o.getFechaVencimiento().isBefore(hoy)
                && (o.getEstado() == EstadoObligacion.PENDIENTE || o.getPenalidadAplicadaEn() == null);
    }

    /** Porcentaje de la política del año; nulo si el año no tiene política (todavía no se penaliza). */
    private BigDecimal porcentajeDelAnio(String anioEscolarId) {
        return politicas.findByAnioEscolarId(anioEscolarId).map(PoliticaCobro::getPorcentajePenalidad).orElse(null);
    }
}
