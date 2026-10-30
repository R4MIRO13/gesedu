package pe.edu.gesedu.horarios.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.Perspectiva;
import pe.edu.gesedu.horarios.aplicacion.ConsultaHorarioService;
import pe.edu.gesedu.horarios.aplicacion.ConsultaHorarioService.HorarioVigenteDto;

/** Consulta del horario publicado (UC-23, RF-39, RF-45). El alcance depende del rol; el borrador nunca se expone aquí. */
@Tag(name = "Horarios")
@RestController
@RequestMapping("/api/v1/horarios/{anio}")
class ConsultaHorarioController {

    private final ConsultaHorarioService consulta;

    ConsultaHorarioController(ConsultaHorarioService consulta) {
        this.consulta = consulta;
    }

    @Operation(summary = "La última versión publicada del horario, por sección o por docente, según el alcance del usuario",
            description = "Administrador: cualquier sección o docente. Docente: sus clases y las secciones donde dicta o es tutor. "
                    + "Alumno y apoderado: la sección de su alcance. 404 HORARIO_NO_PUBLICADO si el año no tiene versión vigente "
                    + "(FA-1); 403 FUERA_DE_ALCANCE si pide una sección o un docente fuera de su alcance. Sin parámetros devuelve "
                    + "la perspectiva principal del rol y el primer elemento permitido.")
    @GetMapping("/vigente")
    @PreAuthorize("hasAuthority('HORARIO_CONSULTAR')")
    HorarioVigenteDto vigente(@PathVariable int anio, @RequestParam(required = false) Perspectiva perspectiva,
                              @RequestParam(required = false) String seccionId, @RequestParam(required = false) String docenteId) {
        return consulta.consultar(anio, perspectiva, seccionId, docenteId);
    }
}
