package pe.edu.gesedu.horarios.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.horarios.aplicacion.DisponibilidadDocenteService;
import pe.edu.gesedu.horarios.aplicacion.DisponibilidadDtos.DisponibilidadDto;
import pe.edu.gesedu.horarios.aplicacion.DisponibilidadDtos.ValidacionDisponibilidadDto;

/** Disponibilidad horaria del docente autenticado (UC-20, RF-46). Es siempre la propia: no recibe el id del docente. */
@Tag(name = "Horarios")
@RestController
@RequestMapping("/api/v1/anios-escolares/{anio}/disponibilidad/mia")
class DisponibilidadDocenteController {

    record BloquesRequest(@Schema(description = "Ids de los bloques en que el docente puede dictar") @NotNull List<String> bloqueIds) {
    }

    record ConfirmacionRequest(@Schema(description = "Ids de los bloques en que el docente puede dictar") @NotNull List<String> bloqueIds,
                               @Size(max = 500) String observaciones) {
    }

    private final DisponibilidadDocenteService disponibilidad;

    DisponibilidadDocenteController(DisponibilidadDocenteService disponibilidad) {
        this.disponibilidad = disponibilidad;
    }

    @Operation(summary = "Mi disponibilidad del año: la matriz de bloques, lo último que confirmé y el estado del periodo",
            description = "422 VINCULO_ACADEMICO_INEXISTENTE no aplica aquí: si el docente no está habilitado, `vinculado` es falso.")
    @GetMapping
    @PreAuthorize("hasAuthority('DISPONIBILIDAD_REGISTRAR')")
    DisponibilidadDto consultar(@PathVariable int anio) {
        return disponibilidad.consultar(anio);
    }

    @Operation(summary = "Compara los bloques marcados con mi carga horaria, sin guardar (advertencia de insuficiencia, FA-2)")
    @PostMapping("/validacion")
    @PreAuthorize("hasAuthority('DISPONIBILIDAD_REGISTRAR')")
    ValidacionDisponibilidadDto validar(@PathVariable int anio, @Valid @RequestBody BloquesRequest solicitud) {
        return disponibilidad.validar(anio, solicitud.bloqueIds());
    }

    @Operation(summary = "Confirma mi disponibilidad: queda como la última versión confirmada (FA-1)",
            description = "422 PERIODO_DISPONIBILIDAD_CERRADO si el periodo no está abierto (FE-1: se conserva la anterior). "
                    + "La advertencia de insuficiencia no impide confirmar. Queda auditado.")
    @PutMapping
    @PreAuthorize("hasAuthority('DISPONIBILIDAD_REGISTRAR')")
    DisponibilidadDto confirmar(@PathVariable int anio, @Valid @RequestBody ConfirmacionRequest solicitud) {
        return disponibilidad.confirmar(anio, solicitud.bloqueIds(), solicitud.observaciones());
    }
}
