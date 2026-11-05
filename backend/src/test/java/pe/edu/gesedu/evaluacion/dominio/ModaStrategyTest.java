package pe.edu.gesedu.evaluacion.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.compartido.dominio.NivelLogro;

/** Moda: el nivel más frecuente; el empate lo gana el registro más reciente (S-04). */
class ModaStrategyTest {

    private final ModaStrategy estrategia = new ModaStrategy();

    private static LogroPonderado l(NivelLogro nivel, int orden) {
        return LogroPonderado.de(nivel, orden);
    }

    @Test
    void gobiernaElMasFrecuente() {
        assertThat(estrategia.tipo()).isEqualTo(TipoRegla.MODA);
        assertThat(estrategia.consolidar(List.of(l(NivelLogro.B, 1), l(NivelLogro.B, 2), l(NivelLogro.B, 3), l(NivelLogro.AD, 4))))
                .isEqualTo(NivelLogro.B);
    }

    @Test
    void elEmpateLoGanaElRegistroMasReciente() {
        // AD (bimestres 1 y 3) y A (2 y 4) empatan: gana A, registrado en el bimestre 4
        assertThat(estrategia.consolidar(List.of(l(NivelLogro.AD, 1), l(NivelLogro.A, 2), l(NivelLogro.AD, 3), l(NivelLogro.A, 4))))
                .isEqualTo(NivelLogro.A);
        // Dos niveles empatados: gana el más reciente aunque sea el menor
        assertThat(estrategia.consolidar(List.of(l(NivelLogro.AD, 1), l(NivelLogro.C, 2)))).isEqualTo(NivelLogro.C);
    }

    @Test
    void elOrdenDeLaListaNoImporta() {
        assertThat(estrategia.consolidar(List.of(l(NivelLogro.A, 4), l(NivelLogro.AD, 3), l(NivelLogro.A, 2), l(NivelLogro.AD, 1))))
                .isEqualTo(NivelLogro.A);
    }

    @Test
    void empateTriple() {
        assertThat(estrategia.consolidar(List.of(l(NivelLogro.AD, 2), l(NivelLogro.B, 3), l(NivelLogro.C, 1))))
                .isEqualTo(NivelLogro.B);
    }

    @Test
    void losPesosNoCuentan() {
        assertThat(estrategia.consolidar(List.of(
                new LogroPonderado(NivelLogro.AD, new BigDecimal("100"), 1),
                new LogroPonderado(NivelLogro.B, BigDecimal.ONE, 2),
                new LogroPonderado(NivelLogro.B, BigDecimal.ONE, 3)))).isEqualTo(NivelLogro.B);
    }

    @Test
    void unSoloLogroOSinLogros() {
        assertThat(estrategia.consolidar(List.of(l(NivelLogro.C, 1)))).isEqualTo(NivelLogro.C);
        assertThatThrownBy(() -> estrategia.consolidar(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
