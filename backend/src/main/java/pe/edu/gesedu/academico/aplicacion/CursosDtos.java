package pe.edu.gesedu.academico.aplicacion;

import java.util.List;

/** Cursos y competencias de un año escolar (UC-14, RF-23, RF-24). Son {@code record}: las entidades no salen de la capa. */
public final class CursosDtos {

    private CursosDtos() {
    }

    public record CompetenciaDto(String id, String codigo, String nombre, String nivelEsperado, int orden) {
    }

    public record CursoDto(String id, String nombre, String slug, int horasLectivas, boolean habilitado,
                           List<CompetenciaDto> competencias) {
    }

    /** Lista del paso 14: los cursos del año y el total de horas semanales («6 cursos · 22 h semanales»). */
    public record CursosAnioDto(int anio, String estado, int totalHoras, List<CursoDto> cursos) {
    }

    /** Competencia pedida: sin {@code id} es nueva; con {@code id} edita la existente. Las que faltan se quitan. */
    public record CompetenciaSolicitud(String id, String nombre, String nivelEsperado) {
    }

    public record CursoSolicitud(String nombre, int horasLectivas, List<CompetenciaSolicitud> competencias) {
    }

    /** Un curso del año anterior que se puede copiar; {@code yaExiste} si el año destino ya tiene un curso igual. */
    public record CursoCopiable(String id, String nombre, int horasLectivas, List<CompetenciaDto> competencias,
                                boolean yaExiste) {
    }

    /** Configuración del año anterior (FA-1, paso 2). {@code anioOrigen} es nulo si ningún año anterior tiene cursos. */
    public record CopiaAnteriorDto(Integer anioOrigen, List<CursoCopiable> cursos) {
    }

    public record ResultadoCopiaDto(int anioOrigen, List<CursoDto> copiados, List<String> omitidos) {
    }
}
