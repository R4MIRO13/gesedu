package pe.edu.gesedu.transversal.reportes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import pe.edu.gesedu.compartido.dominio.Dinero;

/** Excel de los reportes (UC-25, UC-10): encabezados, tipos de celda y zona horaria institucional. */
class GeneradorExcelTest {

    private final GeneradorExcel generador = new GeneradorExcel(
            Clock.fixed(Instant.parse("2027-03-01T13:00:00Z"), ZoneId.of("America/Lima")));

    private Sheet leer(byte[] contenido) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(contenido)).getSheetAt(0);
    }

    @Test
    void generaEncabezadosYFilasConSusTipos() throws Exception {
        byte[] excel = generador.generar("Auditoría", List.of("Fecha y hora", "Usuario", "Monto", "Cantidad", "Activo", "Día"),
                List.of(
                        Arrays.asList(Instant.parse("2027-03-01T15:30:00Z"), "Carmen Salas", Dinero.de("350"), 3, true,
                                LocalDate.of(2027, 3, 1)),
                        Arrays.asList(OffsetDateTime.parse("2027-03-02T10:00:00-05:00"), null, new BigDecimal("17.5"), 0, false,
                                null)));

        Sheet hoja = leer(excel);
        assertThat(hoja.getSheetName()).isEqualTo("Auditoría");
        assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Fecha y hora");
        assertThat(hoja.getWorkbook().getFontAt(hoja.getRow(0).getCell(0).getCellStyle().getFontIndex()).getBold()).isTrue();
        // 15:30 UTC = 10:30 en Lima
        assertThat(hoja.getRow(1).getCell(0).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2027, 3, 1, 10, 30));
        assertThat(hoja.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Carmen Salas");
        assertThat(hoja.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(350.00);
        assertThat(hoja.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(3);
        assertThat(hoja.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Sí");
        assertThat(hoja.getRow(1).getCell(5).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(hoja.getRow(2).getCell(0).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2027, 3, 2, 10, 0));
        assertThat(hoja.getRow(2).getCell(1).getStringCellValue()).isEmpty();
        assertThat(hoja.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(17.5);
        assertThat(hoja.getRow(2).getCell(4).getStringCellValue()).isEqualTo("No");
    }

    @Test
    void unTextoQueEmpiezaConIgualNoSeEvaluaComoFormula() throws Exception {
        Sheet hoja = leer(generador.generar("Hoja", List.of("Descripción"), List.of(List.of("=1+1"))));

        assertThat(hoja.getRow(1).getCell(0).getCellType().name()).isEqualTo("STRING");
        assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).isEqualTo("=1+1");
    }

    @Test
    void elNombreDeLaHojaSeAjustaALasReglasDeExcel() throws Exception {
        Sheet hoja = leer(generador.generar("Reporte: asistencia / sección [3° A] con un nombre larguísimo", List.of("A"), List.of()));

        assertThat(hoja.getSheetName()).hasSizeLessThanOrEqualTo(31).doesNotContain(":", "/", "[", "]");
    }

    @Test
    void sinFilasGeneraSoloElEncabezado() throws Exception {
        Sheet hoja = leer(generador.generar("Vacío", List.of("A", "B"), List.of()));

        assertThat(hoja.getLastRowNum()).isZero();
    }
}
