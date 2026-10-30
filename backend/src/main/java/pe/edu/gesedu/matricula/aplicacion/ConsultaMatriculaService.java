package pe.edu.gesedu.matricula.aplicacion;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.SeccionResumen;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.matricula.dominio.EstadoMatricula;
import pe.edu.gesedu.matricula.dominio.Matricula;
import pe.edu.gesedu.matricula.infraestructura.MatriculaRepository;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias.AlumnoResumen;

/**
 * Consulta de la matrícula propia (UC-03, pasos 1 y 2): el apoderado ve a sus hijos y la matrícula vigente de cada
 * uno; el alumno ve la suya. Solo lectura; el alcance es «mis hijos» o «yo».
 */
@Service
public class ConsultaMatriculaService {

    /** Matrícula confirmada de un año no cerrado. */
    public record MatriculaVigente(String matriculaId, int anio, String grado, String seccion, String estado,
                                   LocalDate fechaMatricula) {
    }

    /** Un estudiante del alcance y su matrícula vigente; {@code matricula} es nulo si no tiene una (FA-2). */
    public record HijoMatricula(String alumnoId, String codigoAlumno, String alumno, MatriculaVigente matricula) {
    }

    /** La sección en que está matriculado un estudiante del alcance (para el horario, UC-23). */
    public record SeccionDeAlumno(String alumnoId, String alumno, String seccionId, String seccion) {
    }

    private final MatriculaRepository matriculas;
    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final DirectorioFamilias familias;
    private final ContextoUsuario contexto;

    ConsultaMatriculaService(MatriculaRepository matriculas, AnioEscolarService anios, CatalogoAcademicoService catalogo,
                             DirectorioFamilias familias, ContextoUsuario contexto) {
        this.matriculas = matriculas;
        this.anios = anios;
        this.catalogo = catalogo;
        this.familias = familias;
        this.contexto = contexto;
    }

    /**
     * Estudiantes del alcance con su matrícula vigente (la confirmada del año más reciente que no está cerrado).
     *
     * @param alumnoId si se indica, solo ese estudiante; uno fuera del alcance responde 403
     */
    @Transactional(readOnly = true)
    public List<HijoMatricula> mias(String alumnoId) {
        String usuarioId = contexto.idUsuarioActual().orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.NO_AUTENTICADO));
        List<AlumnoResumen> alcance = familias.apoderadoDeUsuario(usuarioId)
                .map(ap -> familias.hijosDe(ap.id()))
                .or(() -> familias.alumnoDeUsuario(usuarioId).map(List::of))
                .orElse(List.of());
        if (alumnoId != null && alcance.stream().noneMatch(a -> a.id().equals(alumnoId))) {
            throw new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "El estudiante no está a tu cargo");
        }
        List<AlumnoResumen> elegidos = alcance.stream().filter(a -> alumnoId == null || a.id().equals(alumnoId)).toList();

        Set<String> vigentes = Set.copyOf(anios.idsNoCerrados());
        Map<String, Matricula> porAlumno = matriculas
                .findByAlumnoIdInAndEstado(elegidos.stream().map(AlumnoResumen::id).toList(), EstadoMatricula.CONFIRMADA)
                .stream().filter(m -> vigentes.contains(m.getAnioEscolarId()))
                .collect(Collectors.toMap(Matricula::getAlumnoId, m -> m, (a, b) -> anio(a) >= anio(b) ? a : b));
        return elegidos.stream()
                .map(a -> new HijoMatricula(a.id(), a.codigo(), a.nombreCompleto(),
                        Optional.ofNullable(porAlumno.get(a.id())).map(this::aVigente).orElse(null)))
                .sorted(Comparator.comparing(HijoMatricula::alumno))
                .toList();
    }

    private int anio(Matricula m) {
        return anios.obtenerPorId(m.getAnioEscolarId()).anio();
    }

    /**
     * Las secciones de los estudiantes del alcance («mis hijos» o «yo»), según su matrícula vigente. Es lo que decide qué
     * horario puede ver un apoderado o un alumno (UC-23). Un estudiante sin matrícula vigente no aporta ninguna.
     */
    @Transactional(readOnly = true)
    public List<SeccionDeAlumno> seccionesDeMiAlcance() {
        String usuarioId = contexto.idUsuarioActual().orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO));
        List<AlumnoResumen> alcance = familias.apoderadoDeUsuario(usuarioId)
                .map(ap -> familias.hijosDe(ap.id()))
                .or(() -> familias.alumnoDeUsuario(usuarioId).map(List::of))
                .orElse(List.of());
        Set<String> vigentes = Set.copyOf(anios.idsNoCerrados());
        Map<String, Matricula> porAlumno = matriculas
                .findByAlumnoIdInAndEstado(alcance.stream().map(AlumnoResumen::id).toList(), EstadoMatricula.CONFIRMADA)
                .stream().filter(m -> vigentes.contains(m.getAnioEscolarId()))
                .collect(Collectors.toMap(Matricula::getAlumnoId, m -> m, (a, b) -> anio(a) >= anio(b) ? a : b));
        return alcance.stream().filter(a -> porAlumno.containsKey(a.id()))
                .map(a -> new SeccionDeAlumno(a.id(), a.nombreCompleto(), porAlumno.get(a.id()).getSeccionId(),
                        catalogo.seccion(porAlumno.get(a.id()).getSeccionId()).nombre()))
                .sorted(Comparator.comparing(SeccionDeAlumno::alumno)).toList();
    }

    private MatriculaVigente aVigente(Matricula m) {
        SeccionResumen seccion = catalogo.seccion(m.getSeccionId());
        return new MatriculaVigente(m.getId(), anio(m), seccion.numeroGrado() + "° de secundaria", seccion.nombre(),
                "Confirmada", m.getFechaMatricula());
    }
}
