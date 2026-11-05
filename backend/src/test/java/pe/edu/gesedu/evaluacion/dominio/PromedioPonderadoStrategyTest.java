package pe.edu.gesedu.evaluacion.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pe.edu.gesedu.compartido.dominio.NivelLogro;

/** RF-38: AD=4, A=3, B=2, C=1; pesos iguales por defecto; redondeo HALF_UP al nivel más cercano. */
class PromedioPonderadoStrategyTest {

    private final PromedioPonderadoStrategy estrategia = new PromedioPonderadoStrategy();

    private static LogroPonderado logro(NivelLogro nivel, String peso) {
        return new LogroPonderado(nivel, new BigDecimal(peso), 1);
    }

    @Test
    void esLaReglaPorDefecto() {
        assertThat(estrategia.tipo()).isEqualTo(TipoRegla.PROMEDIO_PONDERADO).isEqualTo(TipoRegla.POR_DEFECTO);
    }

    @ParameterizedTest(name = "{0} con pesos iguales → {1}")
    @CsvSource({
            "'AD,AD,AD,AD', AD",
            "'AD,A',        AD",
            "'A,B',         A",
            "'B,C',         B",
            "'A,A,B,B',     A",
            "'AD,A,A,B',    A",
            "'B,B,B,A',     B",
            "'C,C,C,C',     C",
            "'B',           B"
    })
    void tablaDeCasosConPesosIguales(String niveles, NivelLogro esperado) {
        List<LogroPonderado> logros = Arrays.stream(niveles.split(","))
                .map(String::trim).map(NivelLogro::valueOf).map(n -> LogroPonderado.de(n, 1)).toList();

        assertThat(estrategia.consolidar(logros)).isEqualTo(esperado);
    }

    @Test
    void limitesExactos() {
        assertThat(estrategia.consolidar(List.of(LogroPonderado.de(NivelLogro.B, 1), LogroPonderado.de(NivelLogro.C, 2))))
                .as("1,5 → B").isEqualTo(NivelLogro.B);
        assertThat(estrategia.consolidar(List.of(LogroPonderado.de(NivelLogro.A, 1), LogroPonderado.de(NivelLogro.B, 2))))
                .as("2,5 → A").isEqualTo(NivelLogro.A);
        assertThat(estrategia.consolidar(List.of(LogroPonderado.de(NivelLogro.AD, 1), LogroPonderado.de(NivelLogro.A, 2))))
                .as("3,5 → AD").isEqualTo(NivelLogro.AD);
    }

    @Test
    void justoDebajoDelLimiteNoSube() {
        // (1x51 + 2x49) / 100 = 1,49 -> C ; (1x49 + 2x51) / 100 = 1,51 -> B
        assertThat(estrategia.consolidar(List.of(logro(NivelLogro.C, "51"), logro(NivelLogro.B, "49"))))
                .isEqualTo(NivelLogro.C);
        assertThat(estrategia.consolidar(List.of(logro(NivelLogro.C, "49"), logro(NivelLogro.B, "51"))))
                .isEqualTo(NivelLogro.B);
        // 2,49998 no debe subir por un redondeo intermedio: (2x50001 + 3x49999) / 100000
        assertThat(estrategia.consolidar(List.of(logro(NivelLogro.B, "50001"), logro(NivelLogro.A, "49999"))))
                .isEqualTo(NivelLogro.B);
    }

    @Test
    void losPesosDistintosCambianElResultadoFrenteAlPromedioSimple() {
        // Simple: (4 + 1) / 2 = 2,5 -> A. Ponderado con pesos 1 y 3: (4x1 + 1x3) / 4 = 1,75 -> B.
        assertThat(estrategia.consolidar(List.of(LogroPonderado.de(NivelLogro.AD, 1), LogroPonderado.de(NivelLogro.C, 1))))
                .as("promedio simple").isEqualTo(NivelLogro.A);
        assertThat(estrategia.consolidar(List.of(logro(NivelLogro.AD, "1"), logro(NivelLogro.C, "3"))))
                .as("con pesos 1 y 3").isEqualTo(NivelLogro.B);
    }

    @Test
    void pesosConDecimales() {
        // (4x0,5 + 3x1,5) / 2,0 = 3,25 -> A
        assertThat(estrategia.consolidar(List.of(logro(NivelLogro.AD, "0.50"), logro(NivelLogro.A, "1.50"))))
                .isEqualTo(NivelLogro.A);
    }

    @Test
    void sinLogrosOConPesoInvalidoEsUnError() {
        assertThatThrownBy(() -> estrategia.consolidar(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LogroPonderado(NivelLogro.A, BigDecimal.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LogroPonderado(NivelLogro.A, new BigDecimal("-1"), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
