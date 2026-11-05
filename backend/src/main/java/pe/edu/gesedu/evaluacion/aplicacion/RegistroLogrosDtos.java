package pe.edu.gesedu.evaluacion.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import pe.edu.gesedu.compartido.dominio.NivelLogro;

/** DTO de UC-15 (rejilla de logros por competencia). */
public final class RegistroLogrosDtos {

    private RegistroLogrosDtos() {
    }

    /**
     * Un curso en una sección que el docente tiene a cargo (RF-31). {@code bimestreVigente} es el número del bimestre en
     * curso hoy, o nulo si no hay ninguno.
     */
    public record AsignacionDto(String id, String cursoId, String curso, String cursoSlug, String seccionId, String seccion,
                                String seccionCodigo, int anio, String regla, int alumnos, int competencias,
                                Integer bimestreVigente) {
    }

    public record BimestreDto(int numero, LocalDate fechaInicio, LocalDate fechaFin, boolean vigente) {
    }

    public record CompetenciaDto(String id, String codigo, String nombre, String nivelEsperado) {
    }

    /** Fila de la rejilla: {@code logros} va por id de competencia y solo trae las celdas ya calificadas. */
    public record FilaAlumnoDto(String alumnoId, String codigo, String apellidos, String nombres,
                                Map<String, NivelLogro> logros) {
    }

    /** Alumno con competencias sin calificar (FA-2): sus códigos, por ejemplo {@code ["C3"]}. */
    public record IncompletoDto(String alumnoId, String alumno, List<String> competencias) {
    }

    /**
     * La rejilla alumnos x competencias de un bimestre. {@code editable} es verdadero solo con el bimestre vigente;
     * {@code consolidacion} es nulo (sin consolidar), COMPLETADA o DESACTUALIZADA; {@code cambios} cuenta las celdas
     * que modificó el guardado que la produjo.
     */
    public record RejillaDto(String asignacionId, String curso, String seccion, int anio, BimestreDto bimestre,
                             List<BimestreDto> bimestres, boolean editable, String mensaje, String consolidacion,
                             String avisoConsolidacion, List<CompetenciaDto> competencias, List<FilaAlumnoDto> alumnos,
                             int celdasTotales, int celdasCompletas, List<IncompletoDto> incompletos, String aviso,
                             int cambios) {
    }

    /** Una celda a guardar; {@code nivel} llega como texto para validarlo contra la escala AD, A, B, C. */
    public record ItemLogro(String alumnoId, String competenciaId, String nivel) {
    }
}
