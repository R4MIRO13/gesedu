package pe.edu.gesedu.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.gesedu.pagos.dominio.ConceptoPago;
import pe.edu.gesedu.pagos.dominio.CronogramaCobro;
import pe.edu.gesedu.pagos.dominio.GeneradorObligaciones;
import pe.edu.gesedu.pagos.dominio.ObligacionPago;

/** RF-07, C-11, S-18: la matrícula genera todas las obligaciones del cronograma; la cuota de ingreso, solo al nuevo. */
class GeneradorObligacionesTest {

    private static CronogramaCobro linea(ConceptoPago concepto, String periodo, String monto, String vence) {
        CronogramaCobro l = instanciar(CronogramaCobro.class);
        ReflectionTestUtils.setField(l, "concepto", concepto);
        ReflectionTestUtils.setField(l, "periodo", periodo);
        ReflectionTestUtils.setField(l, "monto", new BigDecimal(monto));
        ReflectionTestUtils.setField(l, "fechaVencimiento", LocalDate.parse(vence));
        return l;
    }

    private static <T> T instanciar(Class<T> tipo) {
        try {
            var constructor = tipo.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<CronogramaCobro> cronograma(boolean conCuotaDeIngreso) {
        List<CronogramaCobro> lineas = new ArrayList<>();
        // Desordenadas a propósito: el generador ordena por vencimiento
        for (int mes = 12; mes >= 3; mes--) {
            lineas.add(linea(ConceptoPago.PENSION, "2027-%02d".formatted(mes), "350.00", "2027-%02d-05".formatted(mes)));
        }
        lineas.add(linea(ConceptoPago.MATRICULA, "2027", "350.00", "2027-02-28"));
        if (conCuotaDeIngreso) {
            lineas.add(linea(ConceptoPago.CUOTA_INGRESO, "2027", "500.00", "2027-02-28"));
        }
        return lineas;
    }

    @Test
    void generaUnaObligacionPorLineaConSusMontosExactos() {
        List<ObligacionPago> obligaciones = GeneradorObligaciones.generar(cronograma(false), false, "mat", "anio", "alu", "apo");

        assertThat(obligaciones).hasSize(11);
        assertThat(obligaciones.getFirst().getConcepto()).isEqualTo(ConceptoPago.MATRICULA);
        assertThat(obligaciones.getLast().getPeriodo()).isEqualTo("2027-12");
        assertThat(obligaciones).extracting(ObligacionPago::getMontoBase)
                .allSatisfy(m -> assertThat(m).isEqualByComparingTo("350.00"));
        assertThat(obligaciones.stream().map(ObligacionPago::getMontoBase).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("3850.00");
        assertThat(obligaciones).allSatisfy(o -> {
            assertThat(o.getMatriculaId()).isEqualTo("mat");
            assertThat(o.getAlumnoId()).isEqualTo("alu");
            assertThat(o.getAnioEscolarId()).isEqualTo("anio");
        });
    }

    @Test
    void laCuotaDeIngresoSoloSeCobraAlNuevoIngreso() {
        assertThat(GeneradorObligaciones.generar(cronograma(true), false, "m", "a", "al", "ap")).hasSize(11)
                .noneMatch(o -> o.getConcepto() == ConceptoPago.CUOTA_INGRESO);
        assertThat(GeneradorObligaciones.generar(cronograma(true), true, "m", "a", "al", "ap")).hasSize(12)
                .anyMatch(o -> o.getConcepto() == ConceptoPago.CUOTA_INGRESO);
    }

    @Test
    void sinCronogramaNoGeneraNada() {
        assertThat(GeneradorObligaciones.generar(List.of(), true, "m", "a", "al", "ap")).isEmpty();
    }
}
