package pe.edu.gesedu.evaluacion.dominio;

/** Regla con la que se consolida una competencia (RF-26). El nombre coincide con el CHECK de {@code regla_consolidacion}. */
public enum TipoRegla {
    PROMEDIO_PONDERADO,
    MODA,
    ULTIMO_REGISTRO;

    /** Regla por defecto cuando el docente no eligió ninguna (UC-16 FA-2, RF-38). */
    public static final TipoRegla POR_DEFECTO = PROMEDIO_PONDERADO;
}
