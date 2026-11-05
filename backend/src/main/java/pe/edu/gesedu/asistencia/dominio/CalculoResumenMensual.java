package pe.edu.gesedu.asistencia.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculo del resumen mensual de asistencia de una persona (UC-11, RF-16): días registrados = PUNTUAL + TARDANZA + FALTA
 * y porcentaje de asistencia = 100 × (PUNTUAL + TARDANZA) / días registrados, con dos decimales. La tardanza cuenta como
 * presencia. Los días esperados sin registro se muestran aparte como «no registrados» y quedan fuera del denominador:
 * no se convierten en faltas. Sin días registrados el porcentaje no existe («No disponible»): no se divide entre cero ni
 * se informa un 0 % ficticio.
 * <p>
 * Dominio puro: recibe los conteos ya filtrados al periodo y a los días considerados.
 */
public final class CalculoResumenMensual {

    private CalculoResumenMensual() {
    }

    /** Conteos de una persona en el periodo; {@code diasEsperados} son los días lectivos en que le correspondía asistir. */
    public record Resumen(int puntual, int tardanza, int falta, int diasEsperados) {

        public int registrados() {
            return puntual + tardanza + falta;
        }

        /** Días esperados sin registro; nunca negativo (un registro fuera de los días esperados no resta). */
        public int noRegistrados() {
            return Math.max(0, diasEsperados - registrados());
        }

        /** @return el porcentaje con escala 2, o {@code null} si no hay días registrados */
        public BigDecimal porcentaje() {
            return CalculoResumenMensual.porcentaje(puntual, tardanza, falta);
        }
    }

    /** @return {@code 100 × (puntual + tardanza) / (puntual + tardanza + falta)} con 2 decimales (HALF_UP), o {@code null} */
    public static BigDecimal porcentaje(int puntual, int tardanza, int falta) {
        int registrados = puntual + tardanza + falta;
        if (registrados == 0) {
            return null;
        }
        return BigDecimal.valueOf(100L * (puntual + tardanza)).divide(BigDecimal.valueOf(registrados), 2, RoundingMode.HALF_UP);
    }
}
