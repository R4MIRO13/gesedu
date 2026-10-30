package pe.edu.gesedu.academico.aplicacion;

import java.util.List;

/** Grados y secciones de un año escolar con su cupo y su tutor (RF-03, RF-04). */
public final class SeccionesDtos {

    private SeccionesDtos() {
    }

    /**
     * Una sección con su cupo: {@code matriculados = capacidad − vacantes}. {@code version} viaja también como
     * {@code ETag} y se devuelve en {@code If-Match} al editar.
     */
    public record SeccionDto(String id, String gradoId, String letra, String nombre, int capacidad, int vacantes,
                             int matriculados, String tutorDocenteId, String tutor, long version) {
    }

    public record GradoSeccionesDto(String id, int numeroNivel, List<SeccionDto> secciones) {
    }

    /** Docente que puede ser tutor (para el selector de la pantalla). */
    public record DocenteTutorDto(String id, String nombre) {
    }

    public record GradosAnioDto(int anio, String estado, List<GradoSeccionesDto> grados, List<DocenteTutorDto> docentes) {
    }
}
