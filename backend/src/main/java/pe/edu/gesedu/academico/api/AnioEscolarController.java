package pe.edu.gesedu.academico.api;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.academico.aplicacion.ConfiguracionAcademicaDtos.AnioDetalleDto;
import pe.edu.gesedu.academico.aplicacion.ConfiguracionAcademicaDtos.AnioResumenDto;
import pe.edu.gesedu.academico.aplicacion.ConfiguracionAcademicaDtos.RangoBimestreSolicitud;
import pe.edu.gesedu.academico.aplicacion.ConfiguracionAcademicaDtos.ValidacionAnioDto;
import pe.edu.gesedu.academico.aplicacion.ConfiguracionAnioService;

/** Año escolar y sus bimestres (UC-13, RF-03, RF-25). */
@Tag(name = "Académico")
@RestController
@RequestMapping("/api/v1/anios-escolares")
class AnioEscolarController {

    record BimestreRequest(@Schema(example = "1") @NotNull @Min(1) @Max(4) Integer numero,
                           @Schema(example = "2027-02-15") @NotNull LocalDate fechaInicio,
                           @Schema(example = "2027-04-30") @NotNull LocalDate fechaFin) {

        RangoBimestreSolicitud aSolicitud() {
            return new RangoBimestreSolicitud(numero, fechaInicio, fechaFin);
        }
    }

    record AnioRequest(@Schema(example = "2028-02-14") @NotNull LocalDate fechaInicio,
                       @Schema(example = "2028-12-20") @NotNull LocalDate fechaFin,
                       @NotNull @Size(min = 4, max = 4, message = "Se necesitan los cuatro bimestres")
                       List<@Valid @NotNull BimestreRequest> bimestres) {
    }

    record ValidacionRequest(@NotNull LocalDate fechaInicio, @NotNull LocalDate fechaFin,
                             @Size(max = 4) List<@Valid @NotNull BimestreRequest> bimestres) {
    }

    private final ConfiguracionAnioService anios;

    AnioEscolarController(ConfiguracionAnioService anios) {
        this.anios = anios;
    }

    @Operation(summary = "Años escolares, del más reciente al más antiguo")
    @GetMapping
    @PreAuthorize("hasAuthority('ACADEMICO_CONSULTAR')")
    List<AnioResumenDto> listar() {
        return anios.listar();
    }

    @Operation(summary = "Un año escolar con sus cuatro bimestres")
    @GetMapping("/{anio}")
    @PreAuthorize("hasAuthority('ACADEMICO_CONSULTAR')")
    AnioDetalleDto obtener(@PathVariable int anio) {
        return anios.obtener(anio);
    }

    @Operation(summary = "Valida las fechas del año (y de los bimestres, si se envían) sin guardar nada",
            description = "Devuelve la duración y la lista de problemas (año fuera de 10–12 meses, bimestres fuera del año "
                    + "o cruzados) para mostrarlos antes de crear el año.")
    @PostMapping("/validaciones")
    @PreAuthorize("hasAuthority('ACADEMICO_CONFIGURAR')")
    ValidacionAnioDto validar(@Valid @RequestBody ValidacionRequest solicitud) {
        return anios.validar(solicitud.fechaInicio(), solicitud.fechaFin(), solicitud.bimestres() == null ? List.of()
                : solicitud.bimestres().stream().map(BimestreRequest::aSolicitud).toList());
    }

    @Operation(summary = "Crea el año escolar PLANIFICADO con sus cuatro bimestres y sus cinco grados",
            description = "422 RANGO_ANIO_INVALIDO si no dura entre 10 y 12 meses; 422 BIMESTRES_SUPERPUESTOS si un "
                    + "bimestre sale del año o se cruza con otro (con `problemas[]`); 409 si el año ya existe. Queda auditado.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ACADEMICO_CONFIGURAR')")
    AnioDetalleDto crear(@Valid @RequestBody AnioRequest solicitud) {
        return anios.crear(solicitud.fechaInicio(), solicitud.fechaFin(),
                solicitud.bimestres().stream().map(BimestreRequest::aSolicitud).toList());
    }
}
