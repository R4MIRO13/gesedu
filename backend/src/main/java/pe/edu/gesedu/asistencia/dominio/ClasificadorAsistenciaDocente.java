package pe.edu.gesedu.asistencia.dominio;

import java.time.LocalTime;

/**
 * Regla de puntualidad del docente (RF-13, RF-19, UC-07 paso 6): la marcación es PUNTUAL si ocurre a más tardar a la
 * hora de inicio de su primera clase del día más la tolerancia; después de ese límite es TARDANZA. El límite es
 * inclusivo. La falta no se decide aquí: la crea el cierre diario para quien no marcó (UC-12).
 * <p>
 * Dominio puro: recibe horas locales ya calculadas en el servidor con la zona institucional; nunca la del dispositivo.
 */
public final class ClasificadorAsistenciaDocente {

    private ClasificadorAsistenciaDocente() {
    }

    /** Hora límite para ser puntual: inicio de la primera clase más la tolerancia. */
    public static LocalTime limitePuntual(LocalTime inicioPrimerBloque, int toleranciaMinutos) {
        return inicioPrimerBloque.plusMinutes(toleranciaMinutos);
    }

    public static EstadoAsistencia clasificar(LocalTime horaMarcacion, LocalTime inicioPrimerBloque, int toleranciaMinutos) {
        return horaMarcacion.isAfter(limitePuntual(inicioPrimerBloque, toleranciaMinutos))
                ? EstadoAsistencia.TARDANZA
                : EstadoAsistencia.PUNTUAL;
    }
}
