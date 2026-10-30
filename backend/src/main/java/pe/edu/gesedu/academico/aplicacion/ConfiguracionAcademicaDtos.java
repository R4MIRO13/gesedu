package pe.edu.gesedu.academico.aplicacion;

import java.time.LocalDate;
import java.util.List;

/** Respuestas de la configuración académica (UC-13 y UC-14). Son {@code record}: las entidades no salen de la capa. */
public final class ConfiguracionAcademicaDtos {

    private ConfiguracionAcademicaDtos() {
    }

    public record BimestreDto(int numero, LocalDate fechaInicio, LocalDate fechaFin) {
    }

    public record AnioResumenDto(String id, int anio, LocalDate fechaInicio, LocalDate fechaFin, String estado) {
    }

    public record AnioDetalleDto(String id, int anio, LocalDate fechaInicio, LocalDate fechaFin, String estado,
                                 List<BimestreDto> bimestres) {
    }

    public record DuracionDto(int meses, int dias) {
    }

    /**
     * Un problema de las fechas. {@code bimestre} es el afectado y {@code otroBimestre} aquel con el que se cruza;
     * {@code desde} y {@code hasta} marcan el tramo en conflicto (para dibujarlo en la línea de tiempo).
     */
    public record ProblemaDto(String tipo, Integer bimestre, Integer otroBimestre, LocalDate desde, LocalDate hasta,
                              String mensaje) {
    }

    /** Resultado de validar las fechas sin guardar nada (pasos 4 y 7 de UC-13). */
    public record ValidacionAnioDto(boolean valido, DuracionDto duracion, List<ProblemaDto> problemas) {
    }

    /** Fechas pedidas de un bimestre. */
    public record RangoBimestreSolicitud(int numero, LocalDate fechaInicio, LocalDate fechaFin) {
    }
}
