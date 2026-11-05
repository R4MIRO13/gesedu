package pe.edu.gesedu.asistencia.dominio;

import java.time.LocalTime;

/**
 * Plazo para cambiar de FALTA a TARDANZA a un alumno que se incorpora tarde (UC-09, RF-13, RF-15): se admite hasta
 * {@code StudentLateCutoffTime}, inclusive. Como esa hora es anterior al cierre de la jornada (lo valida
 * {@code AsistenciaProperties}), pasada la hora límite también quedó atrás el cierre.
 * <p>
 * Dominio puro: recibe la hora local del servidor ya calculada con la zona institucional.
 */
public final class PlazoTardanza {

    private PlazoTardanza() {
    }

    /** {@code true} si a esa hora todavía se admite el cambio a TARDANZA. */
    public static boolean admite(LocalTime horaServidor, LocalTime limite) {
        return !horaServidor.isAfter(limite);
    }
}
