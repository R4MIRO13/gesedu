package pe.edu.gesedu.matricula.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.matricula.aplicacion.CambioSeccionService;
import pe.edu.gesedu.matricula.aplicacion.CambioSeccionService.MatriculaGestionable;
import pe.edu.gesedu.matricula.aplicacion.CambioSeccionService.ResultadoCambioSeccion;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService.HijoMatricula;

/** Matrícula propia de los hijos del apoderado o del alumno (UC-03). */
@Tag(name = "Matrícula")
@RestController
@RequestMapping("/api/v1/matriculas")
class MatriculaController {

    record CambioSeccionRequest(@Schema(description = "Sección de destino, del mismo año; puede ser de otro grado")
                                @NotBlank String seccionId) {
    }

    private final ConsultaMatriculaService consulta;
    private final CambioSeccionService cambios;

    MatriculaController(ConsultaMatriculaService consulta, CambioSeccionService cambios) {
        this.consulta = consulta;
        this.cambios = cambios;
    }

    @Operation(summary = "Estudiantes a cargo del apoderado (o el propio alumno) con su matrícula vigente: año, grado, "
            + "sección y estado. Sin matrícula vigente, `matricula` es nulo (FA-2). Con `alumnoId` solo ese "
            + "estudiante; 403 si no está en el alcance")
    @GetMapping("/mias")
    @PreAuthorize("hasAuthority('MATRICULA_CONSULTAR_PROPIA')")
    List<HijoMatricula> mias(@RequestParam(required = false) String alumnoId) {
        return consulta.mias(alumnoId);
    }

    @Operation(summary = "Matrícula vigente de un estudiante con las secciones de su año y su cupo, para trasladarlo "
            + "(RF-05). 404 si no tiene una matrícula vigente")
    @GetMapping("/alumno/{alumnoId}")
    @PreAuthorize("hasAuthority('MATRICULA_REGISTRAR')")
    MatriculaGestionable matriculaDeAlumno(@PathVariable String alumnoId) {
        return cambios.deAlumno(alumnoId);
    }

    @Operation(summary = "Cambia el grado o la sección de una matrícula confirmada de un año vigente (RF-05). En una sola "
            + "transacción descuenta la vacante del destino y devuelve la del origen. 409 SIN_VACANTE si el destino está "
            + "lleno (no se libera nada); 422 MATRICULA_NO_VIGENTE si está anulada o es de un año cerrado; misma sección "
            + "= sin cambios. Queda auditado")
    @PatchMapping("/{matriculaId}/seccion")
    @PreAuthorize("hasAuthority('MATRICULA_REGISTRAR')")
    ResultadoCambioSeccion cambiarSeccion(@PathVariable String matriculaId, @Valid @RequestBody CambioSeccionRequest solicitud) {
        return cambios.cambiar(matriculaId, solicitud.seccionId());
    }
}
