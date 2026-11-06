package pe.edu.gesedu.matricula.aplicacion;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.matricula.dominio.EstadoMatricula;
import pe.edu.gesedu.matricula.infraestructura.MatriculaRepository;
import pe.edu.gesedu.seguridad.aplicacion.VerificadorVinculoAcademico;

/** Implementa el puerto de {@code seguridad}: el alumno se habilita solo con una matrícula activa (UC-24 FA-2). */
@Component
class VerificadorVinculoAcademicoAdaptador implements VerificadorVinculoAcademico {

    private final MatriculaRepository matriculas;
    private final AnioEscolarService anios;

    VerificadorVinculoAcademicoAdaptador(MatriculaRepository matriculas, AnioEscolarService anios) {
        this.matriculas = matriculas;
        this.anios = anios;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean alumnoConMatriculaActiva(String alumnoId) {
        Set<String> vigentes = Set.copyOf(anios.idsNoCerrados());
        return matriculas.findByAlumnoIdInAndEstado(List.of(alumnoId), EstadoMatricula.CONFIRMADA).stream()
                .anyMatch(m -> vigentes.contains(m.getAnioEscolarId()));
    }
}
