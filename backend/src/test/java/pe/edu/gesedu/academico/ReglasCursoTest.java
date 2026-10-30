package pe.edu.gesedu.academico;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.academico.dominio.ReglasCurso;

class ReglasCursoTest {

    @Test
    void elSlugQuitaTildesYSeparaConGuiones() {
        assertThat(ReglasCurso.slug("Ciencia y Tecnología")).isEqualTo("ciencia-y-tecnologia");
        assertThat(ReglasCurso.slug("  Educación Física ")).isEqualTo("educacion-fisica");
        assertThat(ReglasCurso.slug("Inglés")).isEqualTo(ReglasCurso.slug("Ingles"));
        assertThat(ReglasCurso.slug("¿¿??")).isEqualTo("curso");
    }

    @Test
    void elSiguienteCodigoNoReutilizaLosYaUsados() {
        assertThat(ReglasCurso.siguienteCodigo(List.of())).isEqualTo("C1");
        assertThat(ReglasCurso.siguienteCodigo(List.of("C1", "C2"))).isEqualTo("C3");
        assertThat(ReglasCurso.siguienteCodigo(List.of("C1", "C3"))).isEqualTo("C4");
        assertThat(ReglasCurso.siguienteCodigo(List.of("X", "C9"))).isEqualTo("C10");
    }

    @Test
    void lasHorasVanDeUnaACuarenta() {
        assertThat(ReglasCurso.horasValidas(0)).isFalse();
        assertThat(ReglasCurso.horasValidas(1)).isTrue();
        assertThat(ReglasCurso.horasValidas(40)).isTrue();
        assertThat(ReglasCurso.horasValidas(41)).isFalse();
    }
}
