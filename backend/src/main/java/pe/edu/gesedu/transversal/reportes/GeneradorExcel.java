package pe.edu.gesedu.transversal.reportes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import pe.edu.gesedu.compartido.dominio.Dinero;

/**
 * Genera libros de Excel (.xlsx) con Apache POI para los reportes de auditoría y de asistencia (UC-25, UC-10).
 * Las fechas con hora se escriben en la zona institucional; los montos y números, como celdas numéricas.
 */
@Component
public class GeneradorExcel {

    public static final String TIPO_CONTENIDO = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final int ANCHO_MAXIMO = 60;

    private final ZoneId zona;

    GeneradorExcel(Clock reloj) {
        this.zona = reloj.getZone();
    }

    /**
     * @param hoja         nombre de la hoja
     * @param encabezados  títulos de las columnas (primera fila, en negrita)
     * @param filas        valores por fila: texto, número, {@link BigDecimal}, {@link Dinero}, fechas, booleanos o nulos
     */
    public byte[] generar(String hoja, List<String> encabezados, List<? extends List<?>> filas) {
        return generar(hoja, List.of(), encabezados, filas);
    }

    /**
     * Igual que {@link #generar(String, List, List)}, con unas líneas de {@code resumen} (periodo, alcance, fecha de
     * generación…) sobre la tabla, separadas de ella por una fila en blanco.
     */
    public byte[] generar(String hoja, List<String> resumen, List<String> encabezados, List<? extends List<?>> filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet pagina = libro.createSheet(nombreSeguro(hoja));
            CreationHelper ayuda = libro.getCreationHelper();

            Font negrita = libro.createFont();
            negrita.setBold(true);
            CellStyle estiloEncabezado = libro.createCellStyle();
            estiloEncabezado.setFont(negrita);
            CellStyle estiloFechaHora = libro.createCellStyle();
            estiloFechaHora.setDataFormat(ayuda.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
            CellStyle estiloFecha = libro.createCellStyle();
            estiloFecha.setDataFormat(ayuda.createDataFormat().getFormat("dd/mm/yyyy"));
            CellStyle estiloMonto = libro.createCellStyle();
            estiloMonto.setDataFormat(ayuda.createDataFormat().getFormat("#,##0.00"));

            int[] anchos = new int[encabezados.size()];
            int filaCabecera = resumen.isEmpty() ? 0 : resumen.size() + 1;
            for (int i = 0; i < resumen.size(); i++) {
                pagina.createRow(i).createCell(0).setCellValue(resumen.get(i));
            }
            Row cabecera = pagina.createRow(filaCabecera);
            for (int c = 0; c < encabezados.size(); c++) {
                Cell celda = cabecera.createCell(c);
                celda.setCellValue(encabezados.get(c));
                celda.setCellStyle(estiloEncabezado);
                anchos[c] = encabezados.get(c).length();
            }
            int numeroFila = filaCabecera + 1;
            for (List<?> fila : filas) {
                Row row = pagina.createRow(numeroFila++);
                for (int c = 0; c < fila.size() && c < anchos.length; c++) {
                    int largo = escribir(row.createCell(c), fila.get(c), estiloFechaHora, estiloFecha, estiloMonto);
                    anchos[c] = Math.max(anchos[c], largo);
                }
            }
            for (int c = 0; c < anchos.length; c++) {
                // Ancho calculado sin AWT (autoSizeColumn necesita fuentes del sistema, que un servidor puede no tener)
                pagina.setColumnWidth(c, (Math.min(ANCHO_MAXIMO, anchos[c]) + 2) * 256);
            }
            pagina.createFreezePane(0, filaCabecera + 1);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el archivo de Excel", e);
        }
    }

    /** @return la cantidad de caracteres aproximada del valor, para el ancho de la columna */
    private int escribir(Cell celda, Object valor, CellStyle fechaHora, CellStyle fecha, CellStyle monto) {
        switch (valor) {
            case null -> {
                return 0;
            }
            case Dinero dinero -> {
                celda.setCellValue(dinero.valor().doubleValue());
                celda.setCellStyle(monto);
                return dinero.toString().length();
            }
            case BigDecimal decimal -> {
                celda.setCellValue(decimal.doubleValue());
                celda.setCellStyle(monto);
                return decimal.toPlainString().length();
            }
            case Number numero -> {
                celda.setCellValue(numero.doubleValue());
                return numero.toString().length();
            }
            case Boolean booleano -> {
                celda.setCellValue(booleano ? "Sí" : "No");
                return 2;
            }
            case Instant instante -> {
                celda.setCellValue(LocalDateTime.ofInstant(instante, zona));
                celda.setCellStyle(fechaHora);
                return 16;
            }
            case OffsetDateTime fechaHoraConDesfase -> {
                celda.setCellValue(fechaHoraConDesfase.atZoneSameInstant(zona).toLocalDateTime());
                celda.setCellStyle(fechaHora);
                return 16;
            }
            case LocalDateTime fechaHoraLocal -> {
                celda.setCellValue(fechaHoraLocal);
                celda.setCellStyle(fechaHora);
                return 16;
            }
            case LocalDate soloFecha -> {
                celda.setCellValue(soloFecha);
                celda.setCellStyle(fecha);
                return 10;
            }
            default -> {
                String texto = valor.toString();
                celda.setCellValue(texto);
                return texto.length();
            }
        }
    }

    /** Excel limita el nombre de hoja a 31 caracteres y prohíbe : \ / ? * [ ]. */
    private static String nombreSeguro(String nombre) {
        String limpio = nombre.replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
        return limpio.isEmpty() ? "Hoja1" : limpio.substring(0, Math.min(31, limpio.length()));
    }
}
