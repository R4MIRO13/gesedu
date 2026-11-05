package pe.edu.gesedu.transversal.reportes;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/**
 * Genera documentos PDF con OpenPDF para los reportes (asistencia, UC-10; libretas en la fase 5): un título, unas líneas
 * de resumen y una tabla. Hoja A4 horizontal; las fechas con hora se escriben en la zona institucional. Solo usa las
 * fuentes estándar del formato PDF, así que no depende de las fuentes del sistema.
 */
@Component
public class GeneradorPdf {

    public static final String TIPO_CONTENIDO = "application/pdf";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ZoneId zona;

    GeneradorPdf(Clock reloj) {
        this.zona = reloj.getZone();
    }

    /**
     * @param titulo      encabezado del documento
     * @param resumen     líneas bajo el título (periodo, alcance, fecha de generación…)
     * @param encabezados títulos de las columnas
     * @param filas       valores por fila: texto, números, {@link BigDecimal}, fechas o nulos (se escribe «—»)
     */
    public byte[] generar(String titulo, List<String> resumen, List<String> encabezados, List<? extends List<?>> filas) {
        Document documento = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(documento, salida);
            documento.addTitle(titulo);
            documento.open();

            Font fuenteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font fuenteResumen = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font fuenteEncabezado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font fuenteCelda = FontFactory.getFont(FontFactory.HELVETICA, 9);

            Paragraph encabezado = new Paragraph(titulo, fuenteTitulo);
            encabezado.setSpacingAfter(6);
            documento.add(encabezado);
            for (String linea : resumen) {
                documento.add(new Paragraph(linea, fuenteResumen));
            }

            PdfPTable tabla = new PdfPTable(encabezados.size());
            tabla.setWidthPercentage(100);
            tabla.setSpacingBefore(10);
            tabla.setHeaderRows(1);
            for (String texto : encabezados) {
                PdfPCell celda = new PdfPCell(new Phrase(texto, fuenteEncabezado));
                celda.setBackgroundColor(new java.awt.Color(0xE8, 0xEA, 0xF6));
                celda.setPadding(4);
                tabla.addCell(celda);
            }
            for (List<?> fila : filas) {
                for (int c = 0; c < encabezados.size(); c++) {
                    Object valor = c < fila.size() ? fila.get(c) : null;
                    PdfPCell celda = new PdfPCell(new Phrase(texto(valor), fuenteCelda));
                    celda.setPadding(3);
                    if (valor instanceof Number) {
                        celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    }
                    tabla.addCell(celda);
                }
            }
            documento.add(tabla);
            documento.close();
            return salida.toByteArray();
        } catch (DocumentException | java.io.IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }
    }

    private String texto(Object valor) {
        return switch (valor) {
            case null -> "—";
            case BigDecimal decimal -> decimal.toPlainString();
            case OffsetDateTime fechaHora -> FECHA_HORA.format(fechaHora.atZoneSameInstant(zona));
            case LocalDateTime fechaHora -> FECHA_HORA.format(fechaHora);
            case LocalDate fecha -> FECHA.format(fecha);
            case Boolean booleano -> booleano ? "Sí" : "No";
            default -> valor.toString();
        };
    }
}
