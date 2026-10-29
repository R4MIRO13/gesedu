package pe.edu.gesedu.compartido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.compartido.dominio.NivelLogro;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.compartido.web.Etags;
import pe.edu.gesedu.compartido.web.ParametrosPaginacion;

/** Núcleo compartido sin Spring: Dinero (RNF-11), NivelLogro (RF-38), ETag/If-Match y paginación (§ 5.1). */
class NucleoCompartidoTest {

    // --- Dinero ---

    @Test
    void dineroUsaEscalaDosYHalfUp() {
        assertThat(Dinero.de("350").toString()).isEqualTo("350.00");
        assertThat(Dinero.de("0.125").toString()).isEqualTo("0.13");
        assertThat(Dinero.de(new BigDecimal("2.675")).toString()).isEqualTo("2.68");
    }

    @Test
    void penalidadDelCincoPorCientoSobre350Es17con50() {
        assertThat(Dinero.de("350.00").porcentaje(new BigDecimal("5")).toString()).isEqualTo("17.50");
    }

    @Test
    void porcentajeRedondeaUnaSolaVezAlFinal() {
        // 33.33 × 5 % = 1.6665 → 1.67 (HALF_UP)
        assertThat(Dinero.de("33.33").porcentaje(new BigDecimal("5")).toString()).isEqualTo("1.67");
    }

    @Test
    void sumarRestarYComparar() {
        Dinero base = Dinero.de("350.00");
        Dinero penalidad = Dinero.de("17.50");
        assertThat(base.sumar(penalidad).toString()).isEqualTo("367.50");
        assertThat(base.restar(penalidad).toString()).isEqualTo("332.50");
        assertThat(penalidad.esMenorQue(base)).isTrue();
        assertThat(Dinero.CERO.toString()).isEqualTo("0.00");
    }

    // --- NivelLogro ---

    @Test
    void nivelLogroDesdePromedioConHalfUp() {
        assertThat(NivelLogro.desdePromedio(new BigDecimal("3.5"))).isEqualTo(NivelLogro.AD);
        assertThat(NivelLogro.desdePromedio(new BigDecimal("2.5"))).isEqualTo(NivelLogro.A);
        assertThat(NivelLogro.desdePromedio(new BigDecimal("2.49"))).isEqualTo(NivelLogro.B);
        assertThat(NivelLogro.desdePromedio(new BigDecimal("1.49"))).isEqualTo(NivelLogro.C);
    }

    @Test
    void nivelLogroSeAcotaALaEscala() {
        assertThat(NivelLogro.desdePromedio(new BigDecimal("0.2"))).isEqualTo(NivelLogro.C);
        assertThat(NivelLogro.desdePromedio(new BigDecimal("4.4"))).isEqualTo(NivelLogro.AD);
        assertThat(NivelLogro.AD.valor()).isEqualTo(4);
        assertThat(NivelLogro.desdeValor(1)).isEqualTo(NivelLogro.C);
        assertThatThrownBy(() -> NivelLogro.desdeValor(5)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- ETag / If-Match ---

    @Test
    void etagSeFormateaEntreComillas() {
        assertThat(Etags.de(7)).isEqualTo("\"7\"");
    }

    @Test
    void ifMatchAceptaComillasYEtiquetaDebil() {
        assertThat(Etags.leerVersion("\"3\"")).isEqualTo(3);
        assertThat(Etags.leerVersion("W/\"3\"")).isEqualTo(3);
        assertThat(Etags.leerVersion("3")).isEqualTo(3);
    }

    @Test
    void sinIfMatchResponde400VersionRequerida() {
        assertThatThrownBy(() -> Etags.leerVersion(null)).isInstanceOfSatisfying(ExcepcionNegocio.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoError.VERSION_REQUERIDA));
        assertThatThrownBy(() -> Etags.leerVersion("abc")).isInstanceOfSatisfying(ExcepcionNegocio.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoError.VERSION_REQUERIDA));
    }

    @Test
    void versionDesactualizadaResponde409() {
        Etags.verificar(4, "\"4\"");
        assertThatThrownBy(() -> Etags.verificar(5, "\"4\"")).isInstanceOfSatisfying(ExcepcionNegocio.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoError.CONFLICTO_CONCURRENCIA));
    }

    // --- Paginación ---

    @Test
    void paginacionUsaValoresPorDefectoYLaListaBlancaDeOrden() {
        Set<String> campos = Set.of("fechaHora", "accion");
        Sort porDefecto = Sort.by(Sort.Direction.DESC, "fechaHora");

        Pageable sinParametros = ParametrosPaginacion.de(null, null, null, campos, porDefecto);
        assertThat(sinParametros.getPageNumber()).isZero();
        assertThat(sinParametros.getPageSize()).isEqualTo(20);
        assertThat(sinParametros.getSort()).isEqualTo(porDefecto);

        Pageable ordenado = ParametrosPaginacion.de(2, 50, "accion,asc", campos, porDefecto);
        assertThat(ordenado.getPageNumber()).isEqualTo(2);
        assertThat(ordenado.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "accion"));
    }

    @Test
    void paginacionRechazaTamanoExcesivoYCamposAjenos() {
        Set<String> campos = Set.of("accion");
        Sort porDefecto = Sort.by("accion");
        assertThatThrownBy(() -> ParametrosPaginacion.de(0, 101, null, campos, porDefecto))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoError.VALIDACION_INVALIDA));
        assertThatThrownBy(() -> ParametrosPaginacion.de(0, 10, "password,asc", campos, porDefecto))
                .isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> ParametrosPaginacion.de(0, 10, "accion,sideways", campos, porDefecto))
                .isInstanceOf(ExcepcionNegocio.class);
    }
}
