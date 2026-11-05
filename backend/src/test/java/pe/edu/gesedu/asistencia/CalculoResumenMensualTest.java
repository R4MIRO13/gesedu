package pe.edu.gesedu.asistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.asistencia.dominio.CalculoResumenMensual;
import pe.edu.gesedu.asistencia.dominio.CalculoResumenMensual.Resumen;

/** UC-11, RF-16: % = 100 × (P + T) / registrados, con 2 decimales; los días sin registro quedan fuera y «No disponible» sin datos. */
class CalculoResumenMensualTest {

    @Test
    void laTardanzaCuentaComoPresencia() {
        assertThat(new Resumen(18, 2, 1, 21).porcentaje()).isEqualByComparingTo("95.24");
        assertThat(new Resumen(0, 5, 0, 5).porcentaje()).isEqualByComparingTo("100.00");
    }

    @Test
    void todoFaltaEsCeroPorCiento() {
        assertThat(new Resumen(0, 0, 4, 4).porcentaje()).isEqualByComparingTo("0.00");
    }

    @Test
    void redondeaAlCentesimoConHalfUp() {
        // 2 / 3 = 66.666… → 66.67; 1 / 8 = 12.5 → 12.50; 1 / 16 = 6.25 → 6.25; 1 / 32 = 3.125 → 3.13 (mitad hacia arriba)
        assertThat(CalculoResumenMensual.porcentaje(2, 0, 1)).isEqualByComparingTo("66.67");
        assertThat(CalculoResumenMensual.porcentaje(1, 0, 7)).isEqualByComparingTo("12.50");
        assertThat(CalculoResumenMensual.porcentaje(1, 0, 15)).isEqualByComparingTo("6.25");
        assertThat(CalculoResumenMensual.porcentaje(1, 0, 31)).isEqualByComparingTo("3.13");
    }

    @Test
    void elResultadoSiempreTieneEscalaDos() {
        BigDecimal p = CalculoResumenMensual.porcentaje(1, 0, 0);
        assertThat(p.scale()).isEqualTo(2);
        assertThat(p.toPlainString()).isEqualTo("100.00");
    }

    @Test
    void sinDiasRegistradosNoSeDivideNiSeInformaCero() {
        assertThat(CalculoResumenMensual.porcentaje(0, 0, 0)).isNull();
        Resumen sinDatos = new Resumen(0, 0, 0, 20);
        assertThat(sinDatos.porcentaje()).as("No disponible, no un 0 % ficticio").isNull();
        assertThat(sinDatos.registrados()).isZero();
    }

    @Test
    void losDiasSinRegistroSeMuestranAparteYNoEntranEnElDenominador() {
        Resumen r = new Resumen(10, 2, 1, 20);

        assertThat(r.registrados()).isEqualTo(13);
        assertThat(r.noRegistrados()).isEqualTo(7);
        // El denominador son los 13 registrados, no los 20 esperados: 12 / 13 = 92.31 %, no 12 / 20 = 60 %
        assertThat(r.porcentaje()).isEqualByComparingTo("92.31");
    }

    @Test
    void unRegistroFueraDeLosDiasEsperadosNoDaNoRegistradosNegativos() {
        assertThat(new Resumen(5, 0, 0, 3).noRegistrados()).isZero();
    }
}
