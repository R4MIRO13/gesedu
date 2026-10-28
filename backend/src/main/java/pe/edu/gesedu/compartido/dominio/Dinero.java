package pe.edu.gesedu.compartido.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Monto en soles con escala 2 y redondeo HALF_UP (RNF-11). Viaja en JSON como string decimal ({@code "350.00"})
 * para no perder precisión en JavaScript (S-23).
 */
public record Dinero(BigDecimal valor) implements Comparable<Dinero> {

    public static final Dinero CERO = new Dinero(BigDecimal.ZERO);

    public Dinero {
        valor = valor.setScale(2, RoundingMode.HALF_UP);
    }

    @JsonCreator
    public static Dinero de(String texto) {
        return new Dinero(new BigDecimal(texto));
    }

    public static Dinero de(BigDecimal valor) {
        return new Dinero(valor);
    }

    public Dinero sumar(Dinero otro) {
        return new Dinero(valor.add(otro.valor));
    }

    public Dinero restar(Dinero otro) {
        return new Dinero(valor.subtract(otro.valor));
    }

    /** {@code monto × porcentaje / 100}, redondeado una sola vez al final (penalidad, RF-10). */
    public Dinero porcentaje(BigDecimal porcentaje) {
        return new Dinero(valor.multiply(porcentaje).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
    }

    public boolean esMenorQue(Dinero otro) {
        return valor.compareTo(otro.valor) < 0;
    }

    @Override
    public int compareTo(Dinero otro) {
        return valor.compareTo(otro.valor);
    }

    @JsonValue
    @Override
    public String toString() {
        return valor.toPlainString();
    }
}
