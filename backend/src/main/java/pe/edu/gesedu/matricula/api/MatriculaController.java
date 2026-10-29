package pe.edu.gesedu.matricula.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService.HijoMatricula;

/** Matrícula propia de los hijos del apoderado o del alumno (UC-03). */
@Tag(name = "Matrícula")
@RestController
@RequestMapping("/api/v1/matriculas")
class MatriculaController {

    private final ConsultaMatriculaService consulta;

    MatriculaController(ConsultaMatriculaService consulta) {
        this.consulta = consulta;
    }

    @Operation(summary = "Estudiantes a cargo del apoderado (o el propio alumno) con su matrícula vigente: año, grado, "
            + "sección y estado. Sin matrícula vigente, `matricula` es nulo (FA-2). Con `alumnoId` solo ese "
            + "estudiante; 403 si no está en el alcance")
    @GetMapping("/mias")
    @PreAuthorize("hasAuthority('MATRICULA_CONSULTAR_PROPIA')")
    List<HijoMatricula> mias(@RequestParam(required = false) String alumnoId) {
        return consulta.mias(alumnoId);
    }
}
