package pe.edu.gesedu.academico.aplicacion;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.dominio.Curso;
import pe.edu.gesedu.academico.dominio.Grado;
import pe.edu.gesedu.academico.dominio.Seccion;
import pe.edu.gesedu.academico.infraestructura.CursoRepository;
import pe.edu.gesedu.academico.infraestructura.GradoRepository;
import pe.edu.gesedu.academico.infraestructura.SeccionRepository;

/**
 * Secciones y cursos de un año escolar para los demás módulos (horarios, matrícula, evaluación y asistencia).
 * Devuelve {@code record}: las entidades no salen del módulo.
 */
@Service
public class CatalogoAcademicoService {

    /** Sección con su grado; {@code nombre} es «3° A», como se muestra en pantalla. */
    public record SeccionResumen(String id, String gradoId, int numeroGrado, String letra, String nombre,
                                 String tutorDocenteId) {
    }

    /** Curso con sus horas lectivas semanales de referencia (RF-23, C-10). */
    public record CursoResumen(String id, String nombre, int horasLectivas) {
    }

    private final SeccionRepository secciones;
    private final GradoRepository grados;
    private final CursoRepository cursos;

    CatalogoAcademicoService(SeccionRepository secciones, GradoRepository grados, CursoRepository cursos) {
        this.secciones = secciones;
        this.grados = grados;
        this.cursos = cursos;
    }

    /** Secciones del año ordenadas por grado y letra. */
    @Transactional(readOnly = true)
    public List<SeccionResumen> secciones(String anioEscolarId) {
        Map<String, Grado> gradosPorId = grados.findByAnioEscolarId(anioEscolarId).stream()
                .collect(Collectors.toMap(Grado::getId, Function.identity()));
        return secciones.findByAnioEscolarId(anioEscolarId).stream()
                .map(s -> aResumen(s, gradosPorId.get(s.getGradoId())))
                .sorted(Comparator.comparingInt(SeccionResumen::numeroGrado).thenComparing(SeccionResumen::letra))
                .toList();
    }

    /** Cursos del año ordenados por nombre. */
    @Transactional(readOnly = true)
    public List<CursoResumen> cursos(String anioEscolarId) {
        return cursos.findByAnioEscolarIdOrderByNombre(anioEscolarId).stream()
                .map(CatalogoAcademicoService::aResumen)
                .toList();
    }

    private static SeccionResumen aResumen(Seccion seccion, Grado grado) {
        int nivel = grado.getNumeroNivel();
        return new SeccionResumen(seccion.getId(), seccion.getGradoId(), nivel, seccion.getLetra(),
                nivel + "° " + seccion.getLetra(), seccion.getTutorDocenteId());
    }

    private static CursoResumen aResumen(Curso curso) {
        return new CursoResumen(curso.getId(), curso.getNombre(), curso.getHorasLectivas());
    }
}
