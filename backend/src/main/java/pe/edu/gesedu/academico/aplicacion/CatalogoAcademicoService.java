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
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
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

    /** Sección con su cupo, para elegirla al matricular (RF-04). */
    public record SeccionConVacantes(String id, String gradoId, String nombre, int capacidad, int vacantes) {
    }

    /** Grado de un año escolar. */
    public record GradoResumen(String id, String anioEscolarId, int numeroNivel) {
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

    @Transactional(readOnly = true)
    public GradoResumen grado(String gradoId) {
        Grado grado = grados.findById(gradoId).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el grado"));
        return new GradoResumen(grado.getId(), grado.getAnioEscolarId(), grado.getNumeroNivel());
    }

    /** Una sección con su grado (para mostrar «3° A»). */
    @Transactional(readOnly = true)
    public SeccionResumen seccion(String seccionId) {
        Seccion seccion = secciones.findById(seccionId).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe la sección"));
        Grado grado = grados.findById(seccion.getGradoId()).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el grado"));
        return aResumen(seccion, grado);
    }

    /** Grados del año ordenados por nivel (las solicitudes de matrícula eligen uno). */
    @Transactional(readOnly = true)
    public List<GradoResumen> gradosDelAnio(String anioEscolarId) {
        return grados.findByAnioEscolarId(anioEscolarId).stream()
                .map(g -> new GradoResumen(g.getId(), g.getAnioEscolarId(), g.getNumeroNivel()))
                .sorted(Comparator.comparingInt(GradoResumen::numeroNivel))
                .toList();
    }

    /** Secciones del grado con sus vacantes, ordenadas por letra. */
    @Transactional(readOnly = true)
    public List<SeccionConVacantes> seccionesDelGrado(String gradoId) {
        Grado grado = grados.findById(gradoId).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el grado"));
        return secciones.findByGradoIdOrderByLetra(gradoId).stream()
                .map(s -> new SeccionConVacantes(s.getId(), s.getGradoId(),
                        grado.getNumeroNivel() + "° " + s.getLetra(), s.getCapacidad(), s.getVacantesDisponibles()))
                .toList();
    }

    /**
     * Descuenta una vacante de la sección con un UPDATE condicional (RNF-09). Corre en la transacción del llamador:
     * si la matrícula se revierte, la vacante vuelve.
     *
     * @throws ExcepcionNegocio SIN_VACANTE si ya no quedaban
     */
    @Transactional
    public void reservarVacante(String seccionId) {
        if (secciones.reservarVacante(seccionId) == 0) {
            throw new ExcepcionNegocio(CodigoError.SIN_VACANTE);
        }
    }

    /** Vacantes actuales de la sección, tras la última escritura. */
    @Transactional(readOnly = true)
    public int vacantes(String seccionId) {
        return secciones.vacantesDe(seccionId).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe la sección"));
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
