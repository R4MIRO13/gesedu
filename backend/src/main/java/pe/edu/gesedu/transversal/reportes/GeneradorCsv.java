package pe.edu.gesedu.transversal.reportes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import pe.edu.gesedu.compartido.dominio.Dinero;

/**
 * CSV (RFC 4180) para exportar listados: UTF-8 con BOM (para que Excel respete las tildes), coma como separador y
 * CRLF. Los textos que empiezan con {@code = + - @} se escapan con un apóstrofo para que una hoja de cálculo no
 * los ejecute como fórmula.
 */
@Component
public class GeneradorCsv {

    public static final String TIPO_CONTENIDO = "text/csv; charset=UTF-8";

    private static final Set<Character> INICIOS_DE_FORMULA = Set.of('=', '+', '-', '@', '\t', '\r');
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZoneId zona;

    GeneradorCsv(Clock reloj) {
        this.zona = reloj.getZone();
    }

    public byte[] generar(List<String> encabezados, List<? extends List<?>> filas) {
        StringBuilder csv = new StringBuilder("﻿");
        linea(csv, encabezados.stream().map(this::escapar).toList());
        for (List<?> fila : filas) {
            linea(csv, fila.stream().map(valor -> escapar(texto(valor))).toList());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void linea(StringBuilder csv, List<String> celdas) {
        csv.append(String.join(",", celdas)).append("\r\n");
    }

    /** Texto de un valor: las fechas con hora salen en la zona institucional y los números sin notación científica. */
    private String texto(Object valor) {
        return switch (valor) {
            case null -> "";
            case Dinero dinero -> dinero.toString();
            case BigDecimal decimal -> decimal.toPlainString();
            case Number numero -> numero.toString();
            case Boolean booleano -> booleano ? "Sí" : "No";
            case Instant instante -> FECHA_HORA.format(LocalDateTime.ofInstant(instante, zona));
            case OffsetDateTime fechaHora -> FECHA_HORA.format(fechaHora.atZoneSameInstant(zona).toLocalDateTime());
            case LocalDateTime fechaHora -> FECHA_HORA.format(fechaHora);
            case LocalDate fecha -> fecha.toString();
            default -> {
                String texto = valor.toString();
                yield !texto.isEmpty() && INICIOS_DE_FORMULA.contains(texto.charAt(0)) ? "'" + texto : texto;
            }
        };
    }

    private String escapar(String texto) {
        if (texto.contains(",") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
