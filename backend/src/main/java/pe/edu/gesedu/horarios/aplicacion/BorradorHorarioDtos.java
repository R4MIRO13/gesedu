package pe.edu.gesedu.horarios.aplicacion;

import java.util.List;

/** Respuestas de la elaboración del horario (UC-21). Son {@code record}: las entidades no salen de la capa. */
public final class BorradorHorarioDtos {

    private BorradorHorarioDtos() {
    }

    /** Qué mira la matriz: las clases de una sección o las de un docente (UC-21, FA-3: mismo borrador). */
    public enum Perspectiva { SECCION, DOCENTE }

    public record SeccionDto(String id, String nombre) {
    }

    public record DocenteDto(String id, String nombre) {
    }

    /** Una clase del borrador, con los nombres ya resueltos para mostrarla. */
    public record AsignacionDto(String id, String bloqueId, String seccionId, String seccion, String cursoId,
                                String curso, String docenteId, String docente) {
    }

    /** Un curso de la sección con las horas (bloques) asignadas y las requeridas (RF-43). */
    public record CargaCursoDto(String cursoId, String curso, int horasAsignadas, int horasRequeridas) {
    }

    /** Panel de carga horaria de la sección: «19 de 22 horas asignadas» (informativo). */
    public record CargaSeccionDto(List<CargaCursoDto> cursos, int totalAsignadas, int totalRequeridas) {
    }

    /**
     * El borrador del año para una perspectiva. {@code version} es la del borrador completo: las ediciones deben
     * enviarla en {@code If-Match}. {@code carga} solo viene en la perspectiva Sección.
     */
    public record BorradorDto(String id, int anio, long version, Perspectiva perspectiva, String seccionId,
                              String docenteId, List<BloqueHorarioDto> bloques, List<SeccionDto> secciones,
                              List<DocenteDto> docentes, List<AsignacionDto> asignaciones, CargaSeccionDto carga) {
    }

    public record OpcionDocenteDto(String docenteId, String nombre, boolean disponible, String ocupadoEn,
                                   String detalleOcupado) {
    }

    /** Cursos de la sección y, si se eligió un curso, los docentes asociados con su disponibilidad (RF-47). */
    public record OpcionesDto(List<CargaCursoDto> cursos, List<OpcionDocenteDto> docentes) {
    }

    /** Qué regla se rompe y con qué (para mostrarla en vivo en el panel de asignación). */
    public record ConflictoDto(String tipo, String mensaje, String asignacionId, String seccionId, String docenteId) {
    }

    public record ValidacionDto(boolean valido, List<ConflictoDto> conflictos) {
    }

    /** La clase pedida: el bloque, la sección, el curso y el docente. {@code asignacionId} solo al validar una edición. */
    public record SolicitudAsignacion(String bloqueId, String seccionId, String cursoId, String docenteId,
                                      String asignacionId) {
    }

    /** Resultado de un alta o un cambio, con la nueva versión del borrador. */
    public record ResultadoAsignacion(AsignacionDto asignacion, long version) {
    }
}
