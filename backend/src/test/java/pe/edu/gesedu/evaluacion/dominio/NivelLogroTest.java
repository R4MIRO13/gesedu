package pe.edu.gesedu.evaluacion.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pe.edu.gesedu.compartido.dominio.NivelLogro;

/** Escala de RF-38 y conversión de un promedio al nivel más cercano (HALF_UP). */
class NivelLogroTest {

    @Test
    void valoresDeLaEscala() {
        assertThat(NivelLogro.AD.valor()).isEqualTo(4);
        assertThat(NivelLogro.A.valor()).isEqualTo(3);
        assertThat(NivelLogro.B.valor()).isEqualTo(2);
        assertThat(NivelLogro.C.valor()).isEqualTo(1);
        assertThat(NivelLogro.desdeValor(3)).isEqualTo(NivelLogro.A);
        assertThatThrownBy(() -> NivelLogro.desdeValor(5)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"1.00,C", "1.49,C", "1.50,B", "2.49,B", "2.50,A", "3.49,A", "3.50,AD", "4.00,AD", "0.40,C", "4.60,AD"})
    void promedioAlNivelMasCercano(String promedio, NivelLogro esperado) {
        assertThat(NivelLogro.desdePromedio(new BigDecimal(promedio))).isEqualTo(esperado);
    }
}
