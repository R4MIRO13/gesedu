package pe.edu.gesedu.matricula.aplicacion;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.SeccionResumen;
import pe.edu.gesedu.matricula.dominio.EstadoMatricula;
import pe.edu.gesedu.matricula.dominio.Matricula;
import pe.edu.gesedu.matricula.infraestructura.MatriculaRepository;
import pe.edu.gesedu.pagos.aplicacion.MatriculasDeAlumnos;

/** Implementa el puerto que {@code pagos} usa para mostrar la matrícula en el historial financiero (UC-06). */
@Component
class MatriculasDeAlumnosAdaptador implements MatriculasDeAlumnos {

    private final MatriculaRepository matriculas;
    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;

    MatriculasDeAlumnosAdaptador(MatriculaRepository matriculas, AnioEscolarService anios, CatalogoAcademicoService catalogo) {
        this.matriculas = matriculas;
        this.anios = anios;
        this.catalogo = catalogo;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MatriculaDeAlumno> ultimaDe(String alumnoId) {
        List<Matricula> confirmadas = matriculas.findByAlumnoIdInAndEstado(List.of(alumnoId), EstadoMatricula.CONFIRMADA);
        return confirmadas.stream()
                .max(Comparator.comparingInt(m -> anios.obtenerPorId(m.getAnioEscolarId()).anio()))
                .map(m -> {
                    SeccionResumen seccion = catalogo.seccion(m.getSeccionId());
                    return new MatriculaDeAlumno(anios.obtenerPorId(m.getAnioEscolarId()).anio(),
                            seccion.numeroGrado() + "° de secundaria", seccion.nombre(), "Confirmada");
                });
    }
}
