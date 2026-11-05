package pe.edu.gesedu.evaluacion.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import pe.edu.gesedu.compartido.dominio.NivelLogro;
import pe.edu.gesedu.evaluacion.dominio.EstrategiaConsolidacion;
import pe.edu.gesedu.evaluacion.dominio.LogroPonderado;
import pe.edu.gesedu.evaluacion.dominio.ModaStrategy;
import pe.edu.gesedu.evaluacion.dominio.PromedioPonderadoStrategy;
import pe.edu.gesedu.evaluacion.dominio.TipoRegla;
import pe.edu.gesedu.evaluacion.dominio.UltimoRegistroStrategy;

/** DA-09: el servicio despacha por regla y exige una estrategia por cada {@link TipoRegla}. */
class ConsolidacionServiceTest {

    private static final List<LogroPonderado> LOGROS = List.of(
            LogroPonderado.de(NivelLogro.AD, 1), LogroPonderado.de(NivelLogro.A, 2),
            LogroPonderado.de(NivelLogro.AD, 3), LogroPonderado.de(NivelLogro.C, 4));

    private final ConsolidacionService servicio = new ConsolidacionService(
            List.of(new PromedioPonderadoStrategy(), new ModaStrategy(), new UltimoRegistroStrategy()));

    @Test
    void cadaReglaUsaSuEstrategia() {
        // AD, A, AD, C: promedio 3,0 -> A ; moda AD (2 veces) ; ultimo C
        assertThat(servicio.consolidar(TipoRegla.PROMEDIO_PONDERADO, LOGROS)).contains(NivelLogro.A);
        assertThat(servicio.consolidar(TipoRegla.MODA, LOGROS)).contains(NivelLogro.AD);
        assertThat(servicio.consolidar(TipoRegla.ULTIMO_REGISTRO, LOGROS)).contains(NivelLogro.C);
    }

    @Test
    void sinLogrosNoSeInventaUnaNota() {
        for (TipoRegla regla : TipoRegla.values()) {
            assertThat(servicio.consolidar(regla, List.of())).isEmpty();
        }
    }

    @Test
    void faltaUnaEstrategiaOHayDuplicadas() {
        assertThatThrownBy(() -> new ConsolidacionService(List.of(new PromedioPonderadoStrategy(), new ModaStrategy())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ULTIMO_REGISTRO");
        assertThatThrownBy(() -> new ConsolidacionService(
                List.of(new PromedioPonderadoStrategy(), new PromedioPonderadoStrategy(), new ModaStrategy(),
                        new UltimoRegistroStrategy())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PROMEDIO_PONDERADO");
    }

    @Test
    void springInyectaTodasLasImplementaciones() {
        try (var contexto = new AnnotationConfigApplicationContext()) {
            contexto.scan("pe.edu.gesedu.evaluacion.dominio");
            contexto.register(ConsolidacionService.class);
            contexto.refresh();

            assertThat(contexto.getBeansOfType(EstrategiaConsolidacion.class)).hasSize(TipoRegla.values().length);
            assertThat(contexto.getBean(ConsolidacionService.class).consolidar(TipoRegla.MODA, LOGROS)).contains(NivelLogro.AD);
        }
    }
}
