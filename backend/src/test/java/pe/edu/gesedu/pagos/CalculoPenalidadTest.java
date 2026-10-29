package pe.edu.gesedu.pagos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.pagos.dominio.CalculoPenalidad;

/** RF-10, RNF-11: la penalidad es monto base × porcentaje, a 2 decimales con HALF_UP y un solo redondeo. */
class CalculoPenalidadTest {

    private static Dinero penalidad(String base, String porcentaje) {
        return CalculoPenalidad.calcular(new BigDecimal(base), new BigDecimal(porcentaje));
    }

    @Test
    void cincoPorCientoDeUnaPensionDeTrescientosCincuenta() {
        assertThat(penalidad("350.00", "5.00")).hasToString("17.50");
    }

    @Test
    void redondeaHalfUpEnElTercerDecimal() {
        assertThat(penalidad("333.33", "5.00")).as("16.6665").hasToString("16.67");
        assertThat(penalidad("0.10", "5.00")).as("0.005 sube").hasToString("0.01");
        assertThat(penalidad("0.09", "5.00")).as("0.0045 baja").hasToString("0.00");
        assertThat(penalidad("100.10", "2.50")).as("2.5025").hasToString("2.50");
        assertThat(penalidad("10.10", "5.00")).as("0.505").hasToString("0.51");
    }

    @Test
    void elResultadoSiempreTieneEscalaDos() {
        assertThat(penalidad("350", "5").valor().scale()).isEqualTo(2);
        assertThat(penalidad("350.00", "0").valor()).isEqualByComparingTo("0.00");
        assertThat(penalidad("350.00", "0")).hasToString("0.00");
        assertThat(penalidad("350.00", "100.00")).hasToString("350.00");
    }

    @Test
    void sinPerdidaDePrecisionEnMontosGrandes() {
        assertThat(penalidad("9999999.99", "7.55")).hasToString("755000.00");
    }
}
