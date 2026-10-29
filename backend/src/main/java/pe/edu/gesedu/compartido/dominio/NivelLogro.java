package pe.edu.gesedu.compartido.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Escala de logro del colegio (RF-38): AD = 4, A = 3, B = 2, C = 1. */
public enum NivelLogro {

    C(1),
    B(2),
    A(3),
    AD(4);

    private final int valor;

    NivelLogro(int valor) {
        this.valor = valor;
    }

    public int valor() {
        return valor;
    }

    public static NivelLogro desdeValor(int valor) {
        for (NivelLogro nivel : values()) {
            if (nivel.valor == valor) {
                return nivel;
            }
        }
        throw new IllegalArgumentException("Valor de nivel de logro fuera de 1..4: " + valor);
    }

    /** Redondea el promedio al entero más cercano (HALF_UP: 2,5 → A) y lo acota a la escala (RF-38). */
    public static NivelLogro desdePromedio(BigDecimal promedio) {
        int entero = promedio.setScale(0, RoundingMode.HALF_UP).intValueExact();
        return desdeValor(Math.max(C.valor, Math.min(AD.valor, entero)));
    }
}
