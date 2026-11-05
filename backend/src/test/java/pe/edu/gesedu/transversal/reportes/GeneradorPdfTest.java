package pe.edu.gesedu.transversal.reportes;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

/** PDF de los reportes (UC-10): documento válido, título, resumen, tabla y zona horaria institucional. */
class GeneradorPdfTest {

    private final GeneradorPdf generador = new GeneradorPdf(Clock.fixed(Instant.parse("2027-03-01T13:00:00Z"), ZoneId.of("America/Lima")));

    private static String texto(byte[] pdf) throws Exception {
        PdfReader lector = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            StringBuilder todo = new StringBuilder();
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                todo.append(extractor.getTextFromPage(pagina)).append('\n');
            }
            return todo.toString();
        } finally {
            lector.close();
        }
    }

    @Test
    void generaUnPdfValidoConTituloResumenYTabla() throws Exception {
        byte[] pdf = generador.generar("Reporte de asistencia de alumnos",
                List.of("Alcance: 3° A", "Periodo: marzo 2027", "Generado el 15/03/2027 16:02"),
                List.of("Código", "Apellidos y nombres", "% de asistencia"),
                List.of(Arrays.asList("2027-0001", "Alva, Lucía", new BigDecimal("95.24")),
                        Arrays.asList("2027-0002", "Bravo, Tomás", "No disponible")));

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        String contenido = texto(pdf);
        assertThat(contenido).contains("Reporte de asistencia de alumnos", "Alcance: 3° A", "Periodo: marzo 2027", "Generado el 15/03/2027 16:02");
        assertThat(contenido).contains("Apellidos y nombres", "% de asistencia", "Alva, Lucía", "95.24", "No disponible");
    }

    @Test
    void losValoresNulosFechasYBooleanosSeEscribenLegibles() throws Exception {
        byte[] pdf = generador.generar("Prueba", List.of(), List.of("Fecha", "Hora", "Activo", "Nada"),
                List.of(Arrays.asList(LocalDate.of(2027, 3, 1), OffsetDateTime.parse("2027-03-01T10:30:00-05:00"), true, null)));

        String contenido = texto(pdf);

        assertThat(contenido).contains("01/03/2027", "01/03/2027 10:30", "Sí", "—");
    }

    @Test
    void conMuchasFilasPasaALaSiguientePaginaYRepiteElEncabezado() throws Exception {
        List<List<Object>> filas = new ArrayList<>();
        for (int i = 1; i <= 120; i++) {
            filas.add(Arrays.asList("Docente " + i, i));
        }

        byte[] pdf = generador.generar("Reporte", List.of("Resumen"), List.of("Nombre", "Cantidad"), filas);

        PdfReader lector = new PdfReader(pdf);
        try {
            assertThat(lector.getNumberOfPages()).isGreaterThan(1);
        } finally {
            lector.close();
        }
        assertThat(texto(pdf)).contains("Docente 1", "Docente 120");
    }
}
