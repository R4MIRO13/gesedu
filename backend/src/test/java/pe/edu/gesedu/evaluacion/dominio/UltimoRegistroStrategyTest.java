package pe.edu.gesedu.evaluacion.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.compartido.dominio.NivelLogro;

/** Último registro: el nivel del bimestre de mayor número. */
class UltimoRegistroStrategyTest {

    private final UltimoRegistroStrategy estrategia = new UltimoRegistroStrategy();

    @Test
    void tomaElDelBimestreDeMayorNumero() {
        assertThat(estrategia.tipo()).isEqualTo(TipoRegla.ULTIMO_REGISTRO);
        assertThat(estrategia.consolidar(List.of(
                LogroPonderado.de(NivelLogro.AD, 1), LogroPonderado.de(NivelLogro.AD, 2),
                LogroPonderado.de(NivelLogro.AD, 3), LogroPonderado.de(NivelLogro.C, 4)))).isEqualTo(NivelLogro.C);
    }

    @Test
    void noDependeDelOrdenDeLaLista() {
        assertThat(estrategia.consolidar(List.of(
                LogroPonderado.de(NivelLogro.B, 3), LogroPonderado.de(NivelLogro.AD, 1), LogroPonderado.de(NivelLogro.A, 2))))
                .isEqualTo(NivelLogro.B);
    }

    @Test
    void faltandoElUltimoBimestreGanaElMayorQueExiste() {
        assertThat(estrategia.consolidar(List.of(LogroPonderado.de(NivelLogro.A, 1), LogroPonderado.de(NivelLogro.B, 2))))
                .isEqualTo(NivelLogro.B);
    }

    @Test
    void sinLogrosEsUnError() {
        assertThatThrownBy(() -> estrategia.consolidar(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
