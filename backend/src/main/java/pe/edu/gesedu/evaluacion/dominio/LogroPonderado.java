package pe.edu.gesedu.evaluacion.dominio;

import java.math.BigDecimal;
import java.util.Objects;

import pe.edu.gesedu.compartido.dominio.NivelLogro;

/**
 * Un logro que entra a una consolidación: el nivel, el peso de su bimestre (RF-38) y su {@code orden} cronológico
 * (número del bimestre; mayor = más reciente), que decide el empate de la moda (S-04) y el último registro.
 */
public record LogroPonderado(NivelLogro nivel, BigDecimal peso, int orden) {

    public LogroPonderado {
        Objects.requireNonNull(nivel, "nivel");
        Objects.requireNonNull(peso, "peso");
        if (peso.signum() <= 0) {
            throw new IllegalArgumentException("El peso debe ser mayor que cero: " + peso);
        }
    }

    /** Logro con el peso por defecto (1). */
    public static LogroPonderado de(NivelLogro nivel, int orden) {
        return new LogroPonderado(nivel, BigDecimal.ONE, orden);
    }
}
