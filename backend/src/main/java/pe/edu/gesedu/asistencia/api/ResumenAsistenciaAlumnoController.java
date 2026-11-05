package pe.edu.gesedu.asistencia.api;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.asistencia.aplicacion.ReporteAsistenciaDtos.ResumenAsistenciaAlumnoDto;
import pe.edu.gesedu.asistencia.aplicacion.ReporteAsistenciaService;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;

/** Resumen de asistencia del propio alumno para su inicio (RF-16 bajo demanda). */
@Tag(name = "Asistencia")
@RestController
@RequestMapping("/api/v1/alumnos/{id}/asistencia")
class ResumenAsistenciaAlumnoController {

    private final ReporteAsistenciaService reportes;

    ResumenAsistenciaAlumnoController(ReporteAsistenciaService reportes) {
        this.reportes = reportes;
    }

    @Operation(summary = "Resumen de asistencia del alumno en un mes: conteos, días no registrados y porcentaje",
            description = "Solo el propio alumno (403 FUERA_DE_ALCANCE con otro id). `mes` es AAAA-MM; por omisión, el mes actual del "
                    + "servidor. Se calcula al pedirlo con las reglas de UC-11: `porcentaje` nulo = «No disponible» y los días sin registro "
                    + "no son faltas. 422 PERIODO_INVALIDO con un mes futuro; 422 CALENDARIO_NO_DISPONIBLE sin año escolar activo.")
    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('ASISTENCIA_CONSULTAR_PROPIA')")
    ResumenAsistenciaAlumnoDto resumen(@PathVariable String id, @RequestParam(required = false) String mes) {
        YearMonth periodo = null;
        if (mes != null && !mes.isBlank()) {
            try {
                periodo = YearMonth.parse(mes.trim());
            } catch (DateTimeParseException e) {
                throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "El mes debe tener la forma AAAA-MM")
                        .con("errores", List.of(Map.of("campo", "mes", "mensaje", "Usa el formato AAAA-MM, por ejemplo 2027-03")));
            }
        }
        return reportes.resumenDeAlumno(id, periodo);
    }
}
