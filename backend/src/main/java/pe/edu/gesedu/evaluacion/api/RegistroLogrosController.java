package pe.edu.gesedu.evaluacion.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.AsignacionCursoDto;
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.ItemLogro;
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.RejillaDto;
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosService;

/** Registro de logros por competencia (UC-15, RF-22, RF-31). Solo el docente de la asignación. */
@Tag(name = "Evaluación")
@RestController
@RequestMapping("/api/v1/asignaciones-docentes")
class RegistroLogrosController {

    record ItemLogroRequest(String alumnoId, String competenciaId, String nivel) {
    }

    record GuardarLogrosRequest(@NotEmpty List<@Valid ItemLogroRequest> logros) {
    }

    private final RegistroLogrosService logros;

    RegistroLogrosController(RegistroLogrosService logros) {
        this.logros = logros;
    }

    @Operation(summary = "Mis cursos y secciones del año (lista consolidada de lo que imparto)",
            description = "Las asignaciones del docente en el año ACTIVO, con su regla de consolidación (RF-31).")
    @GetMapping("/mias")
    @PreAuthorize("hasAuthority('NOTA_REGISTRAR')")
    List<AsignacionCursoDto> mias() {
        return logros.misAsignaciones();
    }

    @Operation(summary = "La rejilla alumnos x competencias de un bimestre",
            description = "`bimestre` es el número (1 a 4); por omisión, el vigente. `editable` es verdadero solo con el bimestre "
                    + "vigente. `logros` va por id de competencia y solo trae las celdas ya calificadas. "
                    + "403 FUERA_DE_ALCANCE si la asignación no es mía.")
    @GetMapping("/{id}/registros")
    @PreAuthorize("hasAuthority('NOTA_REGISTRAR')")
    RejillaDto rejilla(@PathVariable String id, @RequestParam(required = false) Integer bimestre) {
        return logros.consultar(id, bimestre);
    }

    @Operation(summary = "Guarda las celdas indicadas de la rejilla (upsert, atómico)",
            description = "Cada `nivel` debe ser AD, A, B o C: si no, 422 NIVEL_LOGRO_INVALIDO con `errores[]` y no se guarda nada (FA-1). "
                    + "Alumno o competencia ajenos, o celda repetida: 400. Solo el bimestre vigente: 422 BIMESTRE_NO_VIGENTE. "
                    + "Si el registro queda incompleto se guarda igual y la respuesta trae `aviso` e `incompletos` (FA-2). "
                    + "Editar un bimestre ya consolidado marca la consolidación como DESACTUALIZADA.")
    @PutMapping("/{id}/registros")
    @PreAuthorize("hasAuthority('NOTA_REGISTRAR')")
    RejillaDto guardar(@PathVariable String id, @RequestParam(required = false) Integer bimestre,
                       @Valid @RequestBody GuardarLogrosRequest solicitud) {
        return logros.guardar(id, bimestre, solicitud.logros().stream()
                .map(i -> new ItemLogro(i.alumnoId(), i.competenciaId(), i.nivel())).toList());
    }
}
