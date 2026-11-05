package pe.edu.gesedu.evaluacion.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.evaluacion.aplicacion.ReglaConsolidacionDtos.ConfiguracionDto;
import pe.edu.gesedu.evaluacion.aplicacion.ReglaConsolidacionDtos.PesoSolicitud;
import pe.edu.gesedu.evaluacion.aplicacion.ReglaConsolidacionDtos.SolicitudConfiguracion;
import pe.edu.gesedu.evaluacion.aplicacion.ReglaConsolidacionService;

/** Regla de consolidación y pesos por bimestre de un curso (UC-16, RF-26, RF-38). Solo el docente de la asignación. */
@Tag(name = "Evaluación")
@RestController
@RequestMapping("/api/v1/asignaciones-docentes")
class ReglaConsolidacionController {

    record PesoRequest(Integer bimestre, String peso) {
    }

    record ConfiguracionRequest(@NotNull String regla, List<@Valid PesoRequest> pesos) {
    }

    private final ReglaConsolidacionService reglas;

    ReglaConsolidacionController(ReglaConsolidacionService reglas) {
        this.reglas = reglas;
    }

    @Operation(summary = "La regla de consolidación vigente, las tres opciones con su ejemplo y los pesos por bimestre",
            description = "Sin haber elegido nunca, la regla es PROMEDIO_PONDERADO (FA-2). Los pesos son texto decimal (`1.00`); "
                    + "`personalizado` indica que el docente lo ajustó. 403 FUERA_DE_ALCANCE si la asignación no es mía.")
    @GetMapping("/{id}/configuracion")
    @PreAuthorize("hasAuthority('REGLA_DEFINIR')")
    ConfiguracionDto consultar(@PathVariable String id) {
        return reglas.consultar(id);
    }

    @Operation(summary = "Define o cambia la regla de consolidación y, si se envían, los pesos de cada bimestre",
            description = "`regla`: PROMEDIO_PONDERADO, MODA o ULTIMO_REGISTRO. `pesos` es el conjunto completo (los bimestres omitidos "
                    + "pesan 1; sin `pesos` no se tocan). Regla desconocida o peso inválido (cero, negativo, más de dos decimales, "
                    + "mayor que 100, bimestre inexistente o repetido): 400 con `errores[]` y no se guarda nada. Año cerrado: 422 ANIO_CERRADO.")
    @PutMapping("/{id}/configuracion")
    @PreAuthorize("hasAuthority('REGLA_DEFINIR')")
    ConfiguracionDto guardar(@PathVariable String id, @Valid @RequestBody ConfiguracionRequest solicitud) {
        return reglas.guardar(id, new SolicitudConfiguracion(solicitud.regla(), solicitud.pesos() == null ? null
                : solicitud.pesos().stream().map(p -> new PesoSolicitud(p.bimestre(), p.peso())).toList()));
    }
}
