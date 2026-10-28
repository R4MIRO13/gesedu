package pe.edu.gesedu.horarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.horarios.dominio.ValidadorConflictos.Candidata;
import pe.edu.gesedu.horarios.dominio.ValidadorConflictos.Conflicto;
import pe.edu.gesedu.horarios.dominio.ValidadorConflictos.Existente;
import pe.edu.gesedu.horarios.dominio.ValidadorConflictos.Tipo;

/** Conflictos de una clase del borrador (UC-21, RF-41, RF-42, RF-47): reglas puras, sin Spring ni base de datos. */
class ValidadorConflictosTest {

    private static final String MARTES_915 = "bloque-martes-915";
    private static final String MARTES_1015 = "bloque-martes-1015";

    private static Candidata candidata(String bloque, String seccion, String docente, String asignacionId) {
        return new Candidata(bloque, seccion, "matematica", docente, asignacionId);
    }

    private static Existente existente(String id, String bloque, String seccion, String docente) {
        return new Existente(id, bloque, seccion, "ingles", docente);
    }

    private static List<Tipo> tipos(List<Conflicto> conflictos) {
        return conflictos.stream().map(Conflicto::tipo).toList();
    }

    @Test
    void unaClaseLibreYPermitidaNoTieneConflictos() {
        List<Conflicto> conflictos = ValidadorConflictos.validar(candidata(MARTES_915, "3A", "rosa", null),
                List.of(existente("a1", MARTES_1015, "3A", "rosa")), true, true);

        assertThat(conflictos).isEmpty();
    }

    @Test
    void elMismoDocenteEnElMismoBloqueEnOtraSeccionEsCruceDeDocente() {
        // Rosa ya tiene 4° B el martes de 9:15 (el caso de la demo)
        Existente rosaEn4B = existente("a1", MARTES_915, "4B", "rosa");

        List<Conflicto> conflictos = ValidadorConflictos.validar(candidata(MARTES_915, "3A", "rosa", null),
                List.of(rosaEn4B), true, true);

        assertThat(tipos(conflictos)).containsExactly(Tipo.CRUCE_DOCENTE);
        assertThat(conflictos.getFirst().con()).isEqualTo(rosaEn4B);
        assertThat(conflictos.getFirst().esDeCruce()).isTrue();
    }

    @Test
    void laMismaSeccionEnElMismoBloqueConOtroDocenteEsCruceDeSeccion() {
        Existente yaTieneClase = existente("a1", MARTES_915, "3A", "carlos");

        List<Conflicto> conflictos = ValidadorConflictos.validar(candidata(MARTES_915, "3A", "rosa", null),
                List.of(yaTieneClase), true, true);

        assertThat(tipos(conflictos)).containsExactly(Tipo.CRUCE_SECCION);
        assertThat(conflictos.getFirst().con().id()).isEqualTo("a1");
    }

    @Test
    void misDocenteYSeccionEnOtroBloqueNoChocan() {
        List<Conflicto> conflictos = ValidadorConflictos.validar(candidata(MARTES_915, "3A", "rosa", null),
                List.of(existente("a1", MARTES_1015, "3A", "rosa"), existente("a2", MARTES_1015, "4B", "rosa")),
                true, true);

        assertThat(conflictos).isEmpty();
    }

    @Test
    void alModificarUnaClaseNoChocaConsigoMisma() {
        Existente editada = existente("a1", MARTES_915, "3A", "rosa");

        // Misma sección y mismo docente en el mismo bloque, pero es la clase que se está editando
        assertThat(ValidadorConflictos.validar(candidata(MARTES_915, "3A", "rosa", "a1"), List.of(editada), true, true))
                .isEmpty();
        // Sin indicar que se edita, sí choca (como una clase nueva duplicada)
        assertThat(tipos(ValidadorConflictos.validar(candidata(MARTES_915, "3A", "rosa", null), List.of(editada),
                true, true))).containsExactly(Tipo.CRUCE_DOCENTE, Tipo.CRUCE_SECCION);
    }

    @Test
    void excluirLaClaseEditadaNoOcultaOtrosCruces() {
        List<Conflicto> conflictos = ValidadorConflictos.validar(candidata(MARTES_915, "3A", "rosa", "a1"),
                List.of(existente("a1", MARTES_1015, "3A", "rosa"), existente("a2", MARTES_915, "4B", "rosa")),
                true, true);

        assertThat(tipos(conflictos)).containsExactly(Tipo.CRUCE_DOCENTE);
        assertThat(conflictos.getFirst().con().id()).isEqualTo("a2");
    }

    @Test
    void unDocenteNoAsociadoAlCursoSeInformaPrimero() {
        List<Conflicto> conflictos = ValidadorConflictos.validar(candidata(MARTES_915, "3A", "rosa", null),
                List.of(existente("a1", MARTES_915, "4B", "rosa")), false, true);

        assertThat(tipos(conflictos)).containsExactly(Tipo.DOCENTE_NO_ASOCIADO, Tipo.CRUCE_DOCENTE);
        assertThat(conflictos.getFirst().esDeCruce()).isFalse();
    }

    @Test
    void unDocenteNoDisponibleSeInformaAntesQueLosCruces() {
        List<Conflicto> conflictos = ValidadorConflictos.validar(candidata(MARTES_915, "3A", "carlos", null),
                List.of(existente("a1", MARTES_915, "3A", "rosa")), true, false);

        assertThat(tipos(conflictos)).containsExactly(Tipo.DOCENTE_NO_DISPONIBLE, Tipo.CRUCE_SECCION);
    }

    @Test
    void varioConflictosJuntosSeListanTodos() {
        List<Conflicto> conflictos = ValidadorConflictos.validar(candidata(MARTES_915, "3A", "rosa", null),
                List.of(existente("a1", MARTES_915, "4B", "rosa"), existente("a2", MARTES_915, "3A", "carlos")),
                false, false);

        assertThat(tipos(conflictos)).containsExactly(Tipo.DOCENTE_NO_ASOCIADO, Tipo.DOCENTE_NO_DISPONIBLE,
                Tipo.CRUCE_DOCENTE, Tipo.CRUCE_SECCION);
    }
}
