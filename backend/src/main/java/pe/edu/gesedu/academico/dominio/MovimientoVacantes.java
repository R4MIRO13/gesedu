package pe.edu.gesedu.academico.dominio;

/**
 * Regla del traslado de un alumno entre secciones (RF-05): se descuenta una vacante del destino y se devuelve una al
 * origen, sin pasar de la capacidad. Función pura que se evalúa con las dos secciones ya bloqueadas; el
 * {@code UPDATE} condicional de la base sigue siendo la última defensa.
 */
public final class MovimientoVacantes {

    public enum Resultado {
        /** Origen y destino son la misma sección: no hay nada que mover. */
        SIN_CAMBIO,
        PERMITIDO,
        /** El destino no tiene vacantes: no se mueve nada y no se libera nada. */
        SIN_VACANTE,
        /** El origen ya está con todas sus vacantes libres: el alumno no podría haber estado en él. */
        ORIGEN_INCONSISTENTE
    }

    /** Estado de una sección para evaluar el movimiento. */
    public record Cupo(String seccionId, int capacidad, int vacantes) {
    }

    private MovimientoVacantes() {
    }

    public static Resultado evaluar(Cupo origen, Cupo destino) {
        if (origen.seccionId().equals(destino.seccionId())) {
            return Resultado.SIN_CAMBIO;
        }
        if (destino.vacantes() <= 0) {
            return Resultado.SIN_VACANTE;
        }
        if (origen.vacantes() >= origen.capacidad()) {
            return Resultado.ORIGEN_INCONSISTENTE;
        }
        return Resultado.PERMITIDO;
    }
}
