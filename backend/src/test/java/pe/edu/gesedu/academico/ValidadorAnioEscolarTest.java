package pe.edu.gesedu.academico;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.academico.dominio.ValidadorAnioEscolar;
import pe.edu.gesedu.academico.dominio.ValidadorAnioEscolar.Problema;
import pe.edu.gesedu.academico.dominio.ValidadorAnioEscolar.RangoBimestre;
import pe.edu.gesedu.academico.dominio.ValidadorAnioEscolar.Tipo;

/** Reglas puras de UC-13: duración del año (FA-1) y bimestres dentro del año y sin cruces (FA-2). */
class ValidadorAnioEscolarTest {

    private static final LocalDate INICIO = LocalDate.of(2027, 2, 15);
    private static final LocalDate FIN = LocalDate.of(2027, 12, 20);

    private static RangoBimestre b(int numero, String inicio, String fin) {
        return new RangoBimestre(numero, LocalDate.parse(inicio), LocalDate.parse(fin));
    }

    private static List<RangoBimestre> bimestresCorrectos() {
        return List.of(b(1, "2027-02-15", "2027-04-30"), b(2, "2027-05-03", "2027-07-09"),
                b(3, "2027-07-26", "2027-10-01"), b(4, "2027-10-04", "2027-12-20"));
    }

    @Test
    void unAnioDeDiezMesesYCincoDiasEsValido() {
        assertThat(ValidadorAnioEscolar.validarAnio(INICIO, FIN)).isEmpty();
        assertThat(ValidadorAnioEscolar.duracion(INICIO, FIN)).isEqualTo(new ValidadorAnioEscolar.Duracion(10, 5));
    }

    @Test
    void losLimitesDeDiezYDoceMesesSonInclusivos() {
        assertThat(ValidadorAnioEscolar.validarAnio(INICIO, INICIO.plusMonths(10))).isEmpty();
        assertThat(ValidadorAnioEscolar.validarAnio(INICIO, INICIO.plusMonths(12))).isEmpty();
        assertThat(ValidadorAnioEscolar.validarAnio(INICIO, INICIO.plusMonths(10).minusDays(1))).hasSize(1);
        assertThat(ValidadorAnioEscolar.validarAnio(INICIO, INICIO.plusMonths(12).plusDays(1))).hasSize(1);
    }

    @Test
    void unAnioDeNueveMesesOConFinAnteriorAlInicioEsInvalido() {
        List<Problema> corto = ValidadorAnioEscolar.validarAnio(INICIO, LocalDate.of(2027, 11, 14));
        assertThat(corto).singleElement().satisfies(p -> {
            assertThat(p.tipo()).isEqualTo(Tipo.ANIO_FUERA_DE_DURACION);
            assertThat(p.esDelAnio()).isTrue();
            assertThat(p.mensaje()).isEqualTo("El año escolar debe durar entre 10 y 12 meses");
        });
        assertThat(ValidadorAnioEscolar.validarAnio(INICIO, INICIO.minusDays(1))).hasSize(1);
        assertThat(ValidadorAnioEscolar.validarAnio(INICIO, INICIO)).hasSize(1);
    }

    @Test
    void cuatroBimestresOrdenadosYDentroDelAnioSonValidos() {
        assertThat(ValidadorAnioEscolar.validarBimestres(INICIO, FIN, bimestresCorrectos())).isEmpty();
    }

    @Test
    void unBimestreQueTerminaElMismoDiaQueEmpiezaElSiguienteSeCruza() {
        List<RangoBimestre> pegados = List.of(b(1, "2027-02-15", "2027-04-30"), b(2, "2027-04-30", "2027-07-09"),
                b(3, "2027-07-26", "2027-10-01"), b(4, "2027-10-04", "2027-12-20"));
        assertThat(ValidadorAnioEscolar.validarBimestres(INICIO, FIN, pegados)).singleElement().satisfies(p -> {
            assertThat(p.tipo()).isEqualTo(Tipo.BIMESTRES_SUPERPUESTOS);
            assertThat(p.desde()).isEqualTo(LocalDate.of(2027, 4, 30));
            assertThat(p.hasta()).isEqualTo(LocalDate.of(2027, 4, 30));
        });
    }

    @Test
    void elCruceDelWireframeSeInformaConElTramoYElMensajeExactos() {
        List<RangoBimestre> cruzados = List.of(b(1, "2027-02-15", "2027-04-16"), b(2, "2027-04-26", "2027-07-02"),
                b(3, "2027-06-25", "2027-09-10"), b(4, "2027-09-20", "2027-12-20"));
        List<Problema> problemas = ValidadorAnioEscolar.validarBimestres(INICIO, FIN, cruzados);
        assertThat(problemas).singleElement().satisfies(p -> {
            assertThat(p.bimestre()).isEqualTo(3);
            assertThat(p.otroBimestre()).isEqualTo(2);
            assertThat(p.desde()).isEqualTo(LocalDate.of(2027, 6, 25));
            assertThat(p.hasta()).isEqualTo(LocalDate.of(2027, 7, 2));
            assertThat(p.mensaje()).isEqualTo(
                    "El Bimestre III se cruza con el Bimestre II. Corrige las fechas. (del 25/06/2027 al 02/07/2027)");
        });
    }

    @Test
    void unBimestreFueraDelAnioOConFechasInvertidasEsInvalido() {
        List<RangoBimestre> fuera = List.of(b(1, "2027-02-01", "2027-04-30"), b(2, "2027-05-03", "2027-07-09"),
                b(3, "2027-07-26", "2027-10-01"), b(4, "2027-10-04", "2027-12-31"));
        assertThat(ValidadorAnioEscolar.validarBimestres(INICIO, FIN, fuera)).extracting(Problema::tipo, Problema::bimestre)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(Tipo.BIMESTRE_FUERA_DEL_ANIO, 1),
                        org.assertj.core.groups.Tuple.tuple(Tipo.BIMESTRE_FUERA_DEL_ANIO, 4));

        List<RangoBimestre> invertido = List.of(b(1, "2027-04-30", "2027-02-15"), b(2, "2027-05-03", "2027-07-09"),
                b(3, "2027-07-26", "2027-10-01"), b(4, "2027-10-04", "2027-12-20"));
        assertThat(ValidadorAnioEscolar.validarBimestres(INICIO, FIN, invertido)).singleElement()
                .satisfies(p -> assertThat(p.tipo()).isEqualTo(Tipo.BIMESTRE_FECHAS_INVALIDAS));
    }

    @Test
    void unBimestrePosteriorQueEmpiezaAntesQueElAnteriorSeInformaComoDesorden() {
        List<RangoBimestre> desordenados = List.of(b(1, "2027-05-03", "2027-07-09"), b(2, "2027-02-15", "2027-04-30"),
                b(3, "2027-07-26", "2027-10-01"), b(4, "2027-10-04", "2027-12-20"));
        assertThat(ValidadorAnioEscolar.validarBimestres(INICIO, FIN, desordenados)).singleElement().satisfies(p -> {
            assertThat(p.tipo()).isEqualTo(Tipo.BIMESTRES_DESORDENADOS);
            assertThat(p.bimestre()).isEqualTo(2);
        });
    }

    @Test
    void losNumerosRomanosDeLosMensajes() {
        assertThat(List.of(1, 2, 3, 4).stream().map(ValidadorAnioEscolar::romano)).containsExactly("I", "II", "III", "IV");
    }
}
