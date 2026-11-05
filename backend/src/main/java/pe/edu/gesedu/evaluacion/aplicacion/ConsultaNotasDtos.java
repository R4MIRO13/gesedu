package pe.edu.gesedu.evaluacion.aplicacion;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import pe.edu.gesedu.compartido.dominio.NivelLogro;

/** DTO de UC-19 (consulta de notas y libreta del alumno y del apoderado). */
public final class ConsultaNotasDtos {

    private ConsultaNotasDtos() {
    }

    /** Un alumno dentro del alcance del usuario: él mismo, o cada hijo del apoderado (FA-1). */
    public record AlumnoNotasDto(String alumnoId, String codigo, String nombre, String seccion, Integer anio) {
    }

    /** Las notas de una competencia: el nivel esperado, el de cada bimestre consolidado (por número) y el anual. */
    public record NotaCompetenciaDto(String competenciaId, String codigo, String nombre, String nivelEsperado,
                                     Map<Integer, NivelLogro> bimestres, NivelLogro anual) {
    }

    public record CursoNotasDto(String cursoId, String curso, String docente, List<NotaCompetenciaDto> competencias) {
    }

    public record BimestreConsolidadoDto(int numero, boolean consolidado) {
    }

    /**
     * Los cursos de la sección del alumno en un año con sus notas consolidadas (RF-30, RF-32, RF-33, RF-34).
     * {@code bimestre} es el que la pantalla muestra (por omisión, el último consolidado); {@code libreta} es PARCIAL, FINAL
     * o nulo si aún no hay; {@code anios} son los años con libreta o matrícula, para ver los anteriores.
     */
    public record CursosAlumnoDto(String alumnoId, String alumno, String codigo, Integer anio, String seccion, List<Integer> anios,
                                  List<BimestreConsolidadoDto> bimestres, int bimestre, String libreta, List<CursoNotasDto> cursos) {
    }

    public record LibretaResumenDto(int anio, String estado, OffsetDateTime fechaActualizacion) {
    }

    /** La libreta o el informe de progreso: las notas consolidadas hasta {@code hastaBimestre} (nulo = libreta completa). */
    public record LibretaDto(String alumnoId, String alumno, String codigo, String seccion, int anio, String estado,
                             OffsetDateTime fechaActualizacion, Integer hastaBimestre, List<Integer> bimestres,
                             List<CursoNotasDto> cursos) {
    }

    /** Un archivo para descargar. */
    public record ArchivoNotas(byte[] contenido, String tipoContenido, String nombre) {
    }
}
