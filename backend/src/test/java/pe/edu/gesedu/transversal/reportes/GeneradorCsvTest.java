package pe.edu.gesedu.transversal.reportes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.compartido.dominio.Dinero;

/** CSV de los reportes (UC-25): codificación, comillas y protección contra fórmulas. */
class GeneradorCsvTest {

    private final GeneradorCsv generador = new GeneradorCsv(
            Clock.fixed(Instant.parse("2027-03-01T13:00:00Z"), ZoneId.of("America/Lima")));

    private String csv(List<String> encabezados, List<? extends List<?>> filas) {
        return new String(generador.generar(encabezados, filas), StandardCharsets.UTF_8);
    }

    @Test
    void empiezaConBomUtf8YUsaCrlf() {
        byte[] bytes = generador.generar(List.of("Acción", "Módulo"), List.of(List.of("Crear", "Horarios")));

        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
        assertThat(csv(List.of("Acción", "Módulo"), List.of(List.of("Crear", "Horarios"))))
                .isEqualTo("﻿Acción,Módulo\r\nCrear,Horarios\r\n");
    }

    @Test
    void entrecomillaLosTextosConComasComillasYSaltosDeLinea() {
        String texto = csv(List.of("A"), List.of(List.of("uno, dos"), List.of("dijo \"hola\""), List.of("l1\nl2")));

        assertThat(texto).contains("\"uno, dos\"\r\n").contains("\"dijo \"\"hola\"\"\"\r\n").contains("\"l1\nl2\"\r\n");
    }

    @Test
    void escapaLosTextosQueUnaHojaDeCalculoTomariaPorFormula() {
        String texto = csv(List.of("A"), List.of(List.of("=1+1"), List.of("+cmd"), List.of("-x"), List.of("@SUMA(A1)")));

        assertThat(texto).contains("'=1+1").contains("'+cmd").contains("'-x").contains("'@SUMA(A1)");
    }

    @Test
    void losNumerosNegativosNoSeEscapan() {
        assertThat(csv(List.of("A"), List.of(List.of(-5)))).contains("-5").doesNotContain("'-5");
    }

    @Test
    void formateaFechasEnLaZonaInstitucionalMontosYNulos() {
        String texto = csv(List.of("a", "b", "c", "d", "e"), List.of(Arrays.asList(
                Instant.parse("2027-03-01T15:30:00Z"), LocalDate.of(2027, 3, 1), Dinero.de("350"), null, true)));

        assertThat(texto).contains("2027-03-01 10:30:00,2027-03-01,350.00,,Sí\r\n");
    }
}
