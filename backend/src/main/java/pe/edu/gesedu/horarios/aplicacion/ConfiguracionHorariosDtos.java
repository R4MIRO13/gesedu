package pe.edu.gesedu.horarios.aplicacion;

import java.time.OffsetDateTime;
import java.util.List;

/** Configuración de horarios del año: periodo de disponibilidad, vinculaciones y cargas (RF-46, RF-47, RF-43). */
public final class ConfiguracionHorariosDtos {

    private ConfiguracionHorariosDtos() {
    }

    /**
     * Periodo en que los docentes registran su disponibilidad. {@code abierto} es lo que decide el servidor ahora:
     * habilitado y dentro de las fechas. Sin configurar, {@code configurado} es falso y el resto viene vacío.
     */
    public record PeriodoDto(int anio, boolean configurado, OffsetDateTime fechaHoraInicio, OffsetDateTime fechaHoraFin,
                             String estado, boolean abierto) {
    }

    public record CursoOpcionDto(String id, String nombre) {
    }

    /**
     * Un docente frente al año. Si no está vinculado, {@code vinculacionId} es nulo y el resto de datos de la
     * vinculación vienen vacíos; la disponibilidad solo existe si el docente la confirmó.
     */
    public record VinculacionDto(String docenteId, String nombre, String vinculacionId, boolean vinculado,
                                 boolean habilitado, int cargaSemanalReferencia, List<String> cursoIds,
                                 boolean disponibilidadConfirmada, int bloquesDisponibles,
                                 OffsetDateTime ultimaConfirmacion) {
    }

    public record VinculacionesAnioDto(int anio, String estado, List<VinculacionDto> docentes, List<CursoOpcionDto> cursos) {
    }

    public record GradoOpcionDto(String id, int numeroNivel) {
    }

    /** Bloques semanales de un curso en un grado; {@code personalizada} si difiere de las horas lectivas del curso. */
    public record CeldaCargaDto(String gradoId, int bloques, boolean personalizada) {
    }

    public record FilaCargaDto(String cursoId, String curso, int horasLectivas, List<CeldaCargaDto> grados) {
    }

    /** Total de bloques de un grado frente a los bloques que tiene la semana: si lo supera, no se puede completar. */
    public record TotalGradoDto(String gradoId, int total, boolean excedeLaSemana) {
    }

    public record CargasHorariasDto(int anio, String estado, int bloquesPorSemana, List<GradoOpcionDto> grados,
                                    List<FilaCargaDto> cursos, List<TotalGradoDto> totales) {
    }

    public record CargaSolicitud(String gradoId, String cursoId, int bloques) {
    }
}
