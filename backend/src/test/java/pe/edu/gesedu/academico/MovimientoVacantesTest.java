package pe.edu.gesedu.academico;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.academico.dominio.MovimientoVacantes;
import pe.edu.gesedu.academico.dominio.MovimientoVacantes.Cupo;
import pe.edu.gesedu.academico.dominio.MovimientoVacantes.Resultado;

/** RF-05: reglas del traslado de una vacante entre secciones (función pura, sin Spring). */
class MovimientoVacantesTest {

    private static Cupo cupo(String id, int capacidad, int vacantes) {
        return new Cupo(id, capacidad, vacantes);
    }

    @Test
    void conVacanteEnElDestinoElMovimientoSePermite() {
        assertThat(MovimientoVacantes.evaluar(cupo("A", 30, 4), cupo("B", 30, 1))).isEqualTo(Resultado.PERMITIDO);
    }

    @Test
    void elDestinoLlenoNoPermiteElMovimiento() {
        assertThat(MovimientoVacantes.evaluar(cupo("A", 30, 4), cupo("B", 30, 0))).isEqualTo(Resultado.SIN_VACANTE);
    }

    @Test
    void lasMismaSeccionNoSeMueve() {
        assertThat(MovimientoVacantes.evaluar(cupo("A", 30, 4), cupo("A", 30, 4))).isEqualTo(Resultado.SIN_CAMBIO);
        assertThat(MovimientoVacantes.evaluar(cupo("A", 30, 0), cupo("A", 30, 0))).as("aunque esté llena").isEqualTo(Resultado.SIN_CAMBIO);
    }

    @Test
    void elOrigenNuncaPasaDeSuCapacidad() {
        assertThat(MovimientoVacantes.evaluar(cupo("A", 30, 30), cupo("B", 30, 5))).isEqualTo(Resultado.ORIGEN_INCONSISTENTE);
        assertThat(MovimientoVacantes.evaluar(cupo("A", 30, 29), cupo("B", 30, 5))).isEqualTo(Resultado.PERMITIDO);
    }

    @Test
    void laUltimaVacanteDelDestinoSePuedeTomar() {
        assertThat(MovimientoVacantes.evaluar(cupo("A", 30, 0), cupo("B", 25, 1))).isEqualTo(Resultado.PERMITIDO);
    }

    @Test
    void sinVacantesEnElDestinoGanaSobreLaInconsistenciaDelOrigen() {
        assertThat(MovimientoVacantes.evaluar(cupo("A", 30, 30), cupo("B", 30, 0))).isEqualTo(Resultado.SIN_VACANTE);
    }
}
