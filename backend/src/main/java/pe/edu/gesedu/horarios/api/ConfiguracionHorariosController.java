package pe.edu.gesedu.horarios.api;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.horarios.aplicacion.CargaHorariaService;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.CargaSolicitud;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.CargasHorariasDto;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.PeriodoDto;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.VinculacionDto;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.VinculacionesAnioDto;
import pe.edu.gesedu.horarios.aplicacion.PeriodoDisponibilidadService;
import pe.edu.gesedu.horarios.aplicacion.VinculacionDocenteService;

/**
 * Configuración de horarios del año (RF-46, RF-47, RF-43): periodo de registro de disponibilidad, docentes vinculados
 * con sus cursos y bloques semanales de cada curso por grado. Los bloques horarios tienen su propio controlador.
 */
@Tag(name = "Horarios")
@RestController
@RequestMapping("/api/v1/anios-escolares/{anio}")
class ConfiguracionHorariosController {

    record PeriodoRequest(@Schema(example = "2027-01-04T08:00:00-05:00") @NotNull OffsetDateTime fechaHoraInicio,
                          @Schema(example = "2027-01-15T23:59:00-05:00") @NotNull OffsetDateTime fechaHoraFin,
                          @Schema(description = "false cierra el periodo aunque las fechas sigan vigentes") @NotNull Boolean habilitado) {
    }

    record VinculacionRequest(@NotNull Boolean habilitado,
                              @Schema(description = "Bloques semanales que se espera que dicte (referencia para la advertencia de UC-20)", example = "20")
                              @NotNull @Min(0) @Max(60) Integer cargaSemanalReferencia,
                              @Schema(description = "Cursos que puede dictar; reemplaza a los anteriores") List<String> cursoIds) {
    }

    record CargaRequest(@NotNull String gradoId, @NotNull String cursoId, @NotNull @Min(1) @Max(20) Integer bloques) {
    }

    record CargasRequest(@NotNull List<@Valid @NotNull CargaRequest> cargas) {
    }

    private final PeriodoDisponibilidadService periodo;
    private final VinculacionDocenteService vinculaciones;
    private final CargaHorariaService cargas;

    ConfiguracionHorariosController(PeriodoDisponibilidadService periodo, VinculacionDocenteService vinculaciones,
                                    CargaHorariaService cargas) {
        this.periodo = periodo;
        this.vinculaciones = vinculaciones;
        this.cargas = cargas;
    }

    @Operation(summary = "Periodo en que los docentes registran su disponibilidad (`abierto` lo decide el servidor)")
    @GetMapping("/periodo-disponibilidad")
    @PreAuthorize("hasAuthority('HORARIO_CONFIGURAR')")
    PeriodoDto obtenerPeriodo(@PathVariable int anio) {
        return periodo.obtener(anio);
    }

    @Operation(summary = "Define el periodo de registro de disponibilidad. `habilitado=false` lo cierra aunque no haya vencido. Queda auditado")
    @PutMapping("/periodo-disponibilidad")
    @PreAuthorize("hasAuthority('HORARIO_CONFIGURAR')")
    PeriodoDto guardarPeriodo(@PathVariable int anio, @Valid @RequestBody PeriodoRequest solicitud) {
        return periodo.guardar(anio, solicitud.fechaHoraInicio(), solicitud.fechaHoraFin(), solicitud.habilitado());
    }

    @Operation(summary = "Docentes frente al año (vinculados o no), con sus cursos asociados y el estado de su disponibilidad")
    @GetMapping("/vinculaciones-docentes")
    @PreAuthorize("hasAuthority('HORARIO_CONFIGURAR')")
    VinculacionesAnioDto listarVinculaciones(@PathVariable int anio) {
        return vinculaciones.listar(anio);
    }

    @Operation(summary = "Vincula o actualiza al docente con el año y reemplaza sus cursos asociados (RF-47)",
            description = "409 DOCENTE_CON_CLASES si se intenta deshabilitarlo o quitarle un curso que ya tiene clases en el borrador.")
    @PutMapping("/vinculaciones-docentes/{docenteId}")
    @PreAuthorize("hasAuthority('HORARIO_CONFIGURAR')")
    VinculacionDto guardarVinculacion(@PathVariable int anio, @PathVariable String docenteId,
                                      @Valid @RequestBody VinculacionRequest solicitud) {
        return vinculaciones.guardar(anio, docenteId, solicitud.habilitado(), solicitud.cargaSemanalReferencia(),
                solicitud.cursoIds());
    }

    @Operation(summary = "Bloques semanales de cada curso por grado, con el total por grado frente a los bloques de la semana")
    @GetMapping("/cargas-horarias")
    @PreAuthorize("hasAuthority('HORARIO_CONFIGURAR')")
    CargasHorariasDto obtenerCargas(@PathVariable int anio) {
        return cargas.obtener(anio);
    }

    @Operation(summary = "Guarda las cargas indicadas (las demás celdas no cambian). Queda auditado")
    @PutMapping("/cargas-horarias")
    @PreAuthorize("hasAuthority('HORARIO_CONFIGURAR')")
    CargasHorariasDto guardarCargas(@PathVariable int anio, @Valid @RequestBody CargasRequest solicitud) {
        return cargas.guardar(anio, solicitud.cargas().stream()
                .map(c -> new CargaSolicitud(c.gradoId(), c.cursoId(), c.bloques())).toList());
    }
}
