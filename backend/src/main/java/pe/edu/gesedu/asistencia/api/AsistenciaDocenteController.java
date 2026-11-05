package pe.edu.gesedu.asistencia.api;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.asistencia.aplicacion.AsistenciaDocenteDtos.EstadoHoyDto;
import pe.edu.gesedu.asistencia.aplicacion.AsistenciaDocenteDtos.RegistroDto;
import pe.edu.gesedu.asistencia.aplicacion.AsistenciaDocenteService;

/** Marcación de asistencia del docente autenticado (UC-07, RF-18 a RF-20). Es siempre la propia: no recibe ids ni horas. */
@Tag(name = "Asistencia")
@RestController
@RequestMapping("/api/v1/asistencia-docente")
class AsistenciaDocenteController {

    private final AsistenciaDocenteService asistencia;

    AsistenciaDocenteController(AsistenciaDocenteService asistencia) {
        this.asistencia = asistencia;
    }

    @Operation(summary = "Mi asistencia de hoy: hora del servidor, primera clase, límite puntual y si puedo marcar",
            description = "La hora es la del servidor en la zona institucional; la del dispositivo no se usa nunca. "
                    + "`motivo` trae el código que daría marcar ahora (DIA_NO_LECTIVO, JORNADA_CERRADA, "
                    + "SIN_CLASES_PROGRAMADAS o ASISTENCIA_YA_REGISTRADA) y es nulo si puedo marcar.")
    @GetMapping("/hoy")
    @PreAuthorize("hasAuthority('ASISTENCIA_DOCENTE_MARCAR')")
    EstadoHoyDto hoy() {
        return asistencia.hoy();
    }

    @Operation(summary = "Marca mi llegada: PUNTUAL si es a más tardar el inicio de mi primera clase más la tolerancia, si no TARDANZA",
            description = "No recibe cuerpo: el instante, la fecha y el estado los decide el servidor. "
                    + "409 ASISTENCIA_YA_REGISTRADA con el registro existente en `registro` (no se sobrescribe, FA-1); "
                    + "422 SIN_CLASES_PROGRAMADAS (sin clases hoy o sin horario publicado, FA-2); "
                    + "422 DIA_NO_LECTIVO o JORNADA_CERRADA (FA-3).")
    @PostMapping("/marcaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ASISTENCIA_DOCENTE_MARCAR')")
    RegistroDto marcar() {
        return asistencia.marcar();
    }

    @Operation(summary = "Mi historial de asistencia, del más reciente al más antiguo (por omisión, los últimos 30 días)")
    @GetMapping("/mias")
    @PreAuthorize("hasAuthority('ASISTENCIA_DOCENTE_MARCAR')")
    List<RegistroDto> mias(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return asistencia.mias(desde, hasta);
    }
}
