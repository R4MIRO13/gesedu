package pe.edu.gesedu.horarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.horarios.dominio.ResponsablesPorCurso.Clase;
import pe.edu.gesedu.horarios.dominio.ResponsablesPorCurso.Responsable;

/** S-13: un solo docente responde por cada curso de una sección aunque varios dicten bloques de él. */
class ResponsablesPorCursoTest {

    private static Clase clase(String curso, String seccion, String docente) {
        return new Clase(curso, seccion, docente);
    }

    @Test
    void respondeElDocenteConMasBloques() {
        List<Responsable> r = ResponsablesPorCurso.elegir(List.of(
                clase("mat", "3A", "rosa"), clase("mat", "3A", "carlos"), clase("mat", "3A", "carlos")), Map.of());

        assertThat(r).containsExactly(new Responsable("mat", "3A", "carlos"));
    }

    @Test
    void unEmpateLoResuelveElResponsableActualYSinEseElDeIdMenor() {
        List<Clase> empate = List.of(clase("mat", "3A", "rosa"), clase("mat", "3A", "carlos"));

        assertThat(ResponsablesPorCurso.elegir(empate, Map.of("mat|3A", "rosa"))).containsExactly(new Responsable("mat", "3A", "rosa"));
        assertThat(ResponsablesPorCurso.elegir(empate, Map.of("mat|3A", "carlos"))).containsExactly(new Responsable("mat", "3A", "carlos"));
        assertThat(ResponsablesPorCurso.elegir(empate, Map.of())).containsExactly(new Responsable("mat", "3A", "carlos"));
    }

    @Test
    void elResponsableActualNoGanaSiOtroTieneMasBloques() {
        List<Responsable> r = ResponsablesPorCurso.elegir(List.of(
                clase("mat", "3A", "rosa"), clase("mat", "3A", "carlos"), clase("mat", "3A", "carlos")), Map.of("mat|3A", "rosa"));

        assertThat(r).containsExactly(new Responsable("mat", "3A", "carlos"));
    }

    @Test
    void sinImportarElOrdenDeLasFilasElResultadoEsElMismo() {
        List<Clase> a = List.of(clase("mat", "3A", "rosa"), clase("ing", "3A", "carmen"), clase("mat", "4B", "carlos"));
        List<Clase> b = List.of(clase("mat", "4B", "carlos"), clase("mat", "3A", "rosa"), clase("ing", "3A", "carmen"));

        assertThat(ResponsablesPorCurso.elegir(a, Map.of())).isEqualTo(ResponsablesPorCurso.elegir(b, Map.of()));
        assertThat(ResponsablesPorCurso.elegir(a, Map.of())).hasSize(3);
    }
}
