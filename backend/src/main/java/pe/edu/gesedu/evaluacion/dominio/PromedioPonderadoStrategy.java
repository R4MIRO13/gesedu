package pe.edu.gesedu.evaluacion.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Component;

import pe.edu.gesedu.compartido.dominio.NivelLogro;

/**
 * Promedio ponderado (RF-38): AD = 4, A = 3, B = 2, C = 1; Σ(valor × peso) / Σ peso, redondeado al nivel más cercano con
 * HALF_UP (2,5 → A). La división redondea exactamente al entero, sin pasar por decimales intermedios.
 */
@Component
public class PromedioPonderadoStrategy implements EstrategiaConsolidacion {

    @Override
    public TipoRegla tipo() {
        return TipoRegla.PROMEDIO_PONDERADO;
    }

    @Override
    public NivelLogro consolidar(List<LogroPonderado> logros) {
        ValidacionLogros.exigirNoVacio(logros);
        BigDecimal suma = BigDecimal.ZERO;
        BigDecimal pesos = BigDecimal.ZERO;
        for (LogroPonderado logro : logros) {
            suma = suma.add(BigDecimal.valueOf(logro.nivel().valor()).multiply(logro.peso()));
            pesos = pesos.add(logro.peso());
        }
        return NivelLogro.desdePromedio(suma.divide(pesos, 0, RoundingMode.HALF_UP));
    }
}
