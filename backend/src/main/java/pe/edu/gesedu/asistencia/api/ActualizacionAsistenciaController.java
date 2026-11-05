package pe.edu.gesedu.asistencia.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.asistencia.aplicacion.ActualizacionAsistenciaService;
import pe.edu.gesedu.asistencia.aplicacion.AsistenciaSeccionDtos.RegistroAlumnoDto;
import pe.edu.gesedu.asistencia.dominio.EstadoAsistencia;

/** Actualización del estado de asistencia de un alumno por su tutor (UC-09, RF-15). */
@Tag(name = "Asistencia")
@RestController
@RequestMapping("/api/v1/asistencia-alumnos")
class ActualizacionAsistenciaController {

    record CambioEstadoRequest(@NotNull EstadoAsistencia estado) {
    }

    private final ActualizacionAsistenciaService actualizacion;

    ActualizacionAsistenciaController(ActualizacionAsistenciaService actualizacion) {
        this.actualizacion = actualizacion;
    }

    @Operation(summary = "Cambia de FALTA a TARDANZA la asistencia de hoy de un alumno que se incorporó tarde",
            description = "Solo `estado: TARDANZA`. Se modifica el mismo registro (sin historial en el módulo; el cambio queda en la "
                    + "auditoría). 200 con `modificado: false` si ya estaba en TARDANZA (FA-2); 400 si el registro no es de hoy o ya "
                    + "es PUNTUAL (FA-2); 403 FUERA_DE_ALCANCE si el alumno no es de la sección del tutor (FA-3); "
                    + "422 PLAZO_TARDANZA_EXCEDIDO si la hora del servidor pasó el límite o la jornada cerró (FA-1).")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ASISTENCIA_ALUMNO_REGISTRAR')")
    RegistroAlumnoDto cambiarEstado(@PathVariable String id, @Valid @RequestBody CambioEstadoRequest solicitud) {
        return actualizacion.cambiarATardanza(id, solicitud.estado());
    }
}
