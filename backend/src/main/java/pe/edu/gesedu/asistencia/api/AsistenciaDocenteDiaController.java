package pe.edu.gesedu.asistencia.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.asistencia.aplicacion.ConsultaAsistenciaDocenteService;
import pe.edu.gesedu.asistencia.aplicacion.ConsultaAsistenciaDocenteService.AsistenciaDocenteDiaDto;

/** Asistencia de todos los docentes en un día, para el administrador (UC-12, consulta). */
@Tag(name = "Asistencia")
@RestController
@RequestMapping("/api/v1/asistencia-docente")
class AsistenciaDocenteDiaController {

    private final ConsultaAsistenciaDocenteService consulta;

    AsistenciaDocenteDiaController(ConsultaAsistenciaDocenteService consulta) {
        this.consulta = consulta;
    }

    @Operation(summary = "Asistencia de los docentes en un día: quién tenía clases, cómo marcó y las faltas del cierre",
            description = "`fecha` (AAAA-MM-DD) es opcional: por omisión, hoy según el servidor; no admite fechas futuras (400). "
                    + "Un docente programado sin registro figura con `estado` nulo (no es falta). `generadaAlCierre` marca las "
                    + "FALTA que creó el cierre diario, sin hora de marcación. `cierre` dice si el cierre procesó la fecha "
                    + "(PROCESADO), falló y se reintenta (FALLIDO) o aún no corrió (PENDIENTE).")
    @GetMapping
    @PreAuthorize("hasAuthority('ASISTENCIA_DOCENTE_CONSULTAR')")
    AsistenciaDocenteDiaDto delDia(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return consulta.delDia(fecha);
    }
}
