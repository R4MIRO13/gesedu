package pe.edu.gesedu.pagos.dominio;

import java.math.BigDecimal;

import pe.edu.gesedu.compartido.dominio.Dinero;

/**
 * Penalidad por vencimiento (RF-10, RNF-11): {@code monto base × porcentaje / 100}, redondeada una sola vez a 2
 * decimales con {@code HALF_UP}. Función pura; el porcentaje es el de la política de cobro del año.
 */
public final class CalculoPenalidad {

    private CalculoPenalidad() {
    }

    public static Dinero calcular(BigDecimal montoBase, BigDecimal porcentaje) {
        return Dinero.de(montoBase).porcentaje(porcentaje);
    }
}
