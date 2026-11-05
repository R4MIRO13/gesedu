package pe.edu.gesedu.evaluacion.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.evaluacion.aplicacion.ConsolidacionBimestralDtos.AvanceDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsolidacionBimestralService;

/** Consolidación de las notas de un bimestre (UC-18, RF-27, RF-28, RF-35, RF-37). Solo con el permiso NOTA_CONSOLIDAR. */
@Tag(name = "Evaluación")
@RestController
@RequestMapping("/api/v1/anios-escolares/{anio}/bimestres/{numero}")
class ConsolidacionBimestralController {

    private final ConsolidacionBimestralService consolidacion;

    ConsolidacionBimestralController(ConsolidacionBimestralService consolidacion) {
        this.consolidacion = consolidacion;
    }

    @Operation(summary = "El avance de las notas del bimestre por curso y sección, y si ya se puede consolidar",
            description = "`cursos` trae, por curso en una sección, las celdas alumno x competencia con logro sobre el total y su porcentaje; "
                    + "los incompletos van primero. `puedeConsolidar` es verdadero cuando todos están al 100 % (y, en el último bimestre, "
                    + "los anteriores están consolidados y al día); si no, `motivo` y `docentesPendientes` lo explican.")
    @GetMapping("/avance")
    @PreAuthorize("hasAuthority('NOTA_CONSOLIDAR')")
    AvanceDto avance(@PathVariable int anio, @PathVariable int numero) {
        return consolidacion.avance(anio, numero);
    }

    @Operation(summary = "Consolida el bimestre: pasa las notas bimestrales a la libreta de cada alumno",
            description = "Con cursos incompletos: 422 BIMESTRE_INCOMPLETO con `pendientes[]` y `docentes[]` (FA-2). En el último bimestre calcula "
                    + "además las notas anuales de todos los cursos y deja la libreta FINAL (FA-1); exige los anteriores consolidados y al día "
                    + "(422 BIMESTRES_ANTERIORES_PENDIENTES). Reconsolidar reemplaza las notas del bimestre. No exige que el bimestre siga "
                    + "vigente. 422 ANIO_CERRADO.")
    @PostMapping("/consolidacion")
    @PreAuthorize("hasAuthority('NOTA_CONSOLIDAR')")
    AvanceDto consolidar(@PathVariable int anio, @PathVariable int numero) {
        return consolidacion.consolidar(anio, numero);
    }
}
