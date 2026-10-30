package pe.edu.gesedu.horarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.horarios.dominio.ValidadorCompletitud.CursoEnSeccion;
import pe.edu.gesedu.horarios.dominio.ValidadorCompletitud.Estado;
import pe.edu.gesedu.horarios.dominio.ValidadorCompletitud.Resultado;

/** RF-43: la carga semanal de cada curso se compara bloque a bloque; faltar en uno no se compensa con sobrar en otro. */
class ValidadorCompletitudTest {

    private static CursoEnSeccion curso(String nombre, int asignadas, int requeridas) {
        return new CursoEnSeccion("c-" + nombre, nombre, asignadas, requeridas);
    }

    @Test
    void unaSeccionConTodosSusCursosExactosEstaCompleta() {
        Resultado r = ValidadorCompletitud.evaluar("s1", "1° A", List.of(curso("Matemática", 5, 5), curso("Inglés", 3, 3)));

        assertThat(r.estado()).isEqualTo(Estado.COMPLETO);
        assertThat(r.estaCompleta()).isTrue();
        assertThat(r.asignadas()).isEqualTo(8);
        assertThat(r.requeridas()).isEqualTo(8);
        assertThat(r.faltan()).isZero();
        assertThat(r.sobran()).isZero();
        assertThat(r.diferencias()).isEmpty();
    }

    @Test
    void faltarEnUnCursoInformaLasHorasPendientesYElDetalle() {
        Resultado r = ValidadorCompletitud.evaluar("s3", "3° A", List.of(curso("Matemática", 4, 5), curso("Inglés", 2, 3),
                curso("Comunicación", 5, 5)));

        assertThat(r.estado()).isEqualTo(Estado.FALTAN);
        assertThat(r.faltan()).isEqualTo(2);
        assertThat(r.sobran()).isZero();
        assertThat(r.asignadas()).isEqualTo(11);
        assertThat(r.requeridas()).isEqualTo(13);
        assertThat(r.diferencias()).extracting(CursoEnSeccion::curso).containsExactly("Matemática", "Inglés");
    }

    @Test
    void sobrarEnUnCursoEsExceso() {
        Resultado r = ValidadorCompletitud.evaluar("s4", "4° B", List.of(curso("Educación Física", 3, 2), curso("Inglés", 3, 3)));

        assertThat(r.estado()).isEqualTo(Estado.EXCEDE);
        assertThat(r.sobran()).isEqualTo(1);
        assertThat(r.faltan()).isZero();
        assertThat(r.diferencias()).singleElement().satisfies(d -> {
            assertThat(d.curso()).isEqualTo("Educación Física");
            assertThat(d.asignadas()).isEqualTo(3);
            assertThat(d.requeridas()).isEqualTo(2);
        });
    }

    @Test
    void elTotalCuadraPeroUnCursoFaltaYOtroSobraNoEstaCompleta() {
        // 5 + 3 = 8 bloques asignados y 8 requeridos, pero Matemática tiene uno de menos e Inglés uno de más
        Resultado r = ValidadorCompletitud.evaluar("s2", "2° A", List.of(curso("Matemática", 4, 5), curso("Inglés", 4, 3)));

        assertThat(r.asignadas()).isEqualTo(r.requeridas());
        assertThat(r.estado()).isEqualTo(Estado.FALTAN_Y_EXCEDE);
        assertThat(r.estaCompleta()).isFalse();
        assertThat(r.faltan()).isEqualTo(1);
        assertThat(r.sobran()).isEqualTo(1);
    }

    @Test
    void unCursoSinNingunBloqueFaltaPorCompleto() {
        Resultado r = ValidadorCompletitud.evaluar("s5", "5° A", List.of(curso("Matemática", 0, 5)));

        assertThat(r.estado()).isEqualTo(Estado.FALTAN);
        assertThat(r.faltan()).isEqualTo(5);
    }

    @Test
    void sePuedePublicarSoloSiHaySeccionesYTodasEstanCompletas() {
        Resultado completa = ValidadorCompletitud.evaluar("a", "1° A", List.of(curso("Matemática", 5, 5)));
        Resultado incompleta = ValidadorCompletitud.evaluar("b", "1° B", List.of(curso("Matemática", 4, 5)));

        assertThat(ValidadorCompletitud.publicable(List.of(completa, completa))).isTrue();
        assertThat(ValidadorCompletitud.publicable(List.of(completa, incompleta))).isFalse();
        assertThat(ValidadorCompletitud.publicable(List.of())).isFalse();
    }
}
