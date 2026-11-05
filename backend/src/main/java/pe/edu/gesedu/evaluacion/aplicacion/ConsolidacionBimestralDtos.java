package pe.edu.gesedu.evaluacion.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import pe.edu.gesedu.evaluacion.aplicacion.NotasFinalesDtos.CalculoDto;

/** DTO de UC-18 (consolidar las notas de un bimestre). */
public final class ConsolidacionBimestralDtos {

    private ConsolidacionBimestralDtos() {
    }

    /** Estado de un bimestre: SIN_CONSOLIDAR, COMPLETADA o DESACTUALIZADA (se editaron logros después de consolidarlo). */
    public record EstadoBimestreDto(int numero, String estado) {
    }

    public record BimestreAvanceDto(int numero, LocalDate fechaInicio, LocalDate fechaFin, boolean ultimo) {
    }

    /** Un curso en una sección con lo que lleva registrado: celdas con logro sobre celdas alumno × competencia. */
    public record CursoAvanceDto(String asignacionId, String curso, String seccion, String docente, int celdasTotales,
                                 int celdasCompletas, int porcentaje, boolean completo) {
    }

    public record TotalesAvanceDto(int cursos, int completos, int incompletos) {
    }

    /** Notas bimestrales creadas, cambiadas y sin cambios, y libretas puestas al día. */
    public record ResultadoDto(int creadas, int actualizadas, int sinCambios, int libretas, boolean finalizada,
                               CalculoDto notasAnuales) {
    }

    /**
     * El avance de las notas de un bimestre y si se puede consolidar. {@code motivo} explica por qué no (nulo si se puede);
     * {@code docentesPendientes} son los de los cursos incompletos; {@code resultado} solo viene tras consolidar.
     */
    public record AvanceDto(int anio, BimestreAvanceDto bimestre, List<EstadoBimestreDto> bimestres, String estado,
                            OffsetDateTime fechaConsolidacion, TotalesAvanceDto totales, List<CursoAvanceDto> cursos,
                            boolean puedeConsolidar, String motivo, List<String> docentesPendientes, boolean editable,
                            ResultadoDto resultado) {
    }
}
