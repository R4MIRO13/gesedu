package pe.edu.gesedu.asistencia.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.asistencia.aplicacion.ReporteAsistenciaDtos.Ambito;
import pe.edu.gesedu.asistencia.aplicacion.ReporteAsistenciaDtos.ArchivoReporte;
import pe.edu.gesedu.asistencia.aplicacion.ReporteAsistenciaDtos.Formato;
import pe.edu.gesedu.asistencia.aplicacion.ReporteAsistenciaDtos.ReporteAsistenciaDto;
import pe.edu.gesedu.asistencia.aplicacion.ReporteAsistenciaService;

/** Reportes mensuales de asistencia calculados al pedirlos (UC-10, UC-11; RF-16, RF-17, RF-21). */
@Tag(name = "Asistencia")
@RestController
@RequestMapping("/api/v1/reportes/asistencia")
class ReporteAsistenciaController {

    private final ReporteAsistenciaService reportes;

    ReporteAsistenciaController(ReporteAsistenciaService reportes) {
        this.reportes = reportes;
    }

    @Operation(summary = "Reporte mensual de asistencia de alumnos o de docentes, en JSON, Excel o PDF",
            description = "Se calcula al pedirlo a partir de los registros diarios; no se guarda ningún consolidado. "
                    + "`mes` y `anio` van juntos; sin ninguno de los dos es el mes actual del servidor. "
                    + "`ambito` ALUMNOS (el tutor, solo su sección; el administrador, cualquiera o todas) o DOCENTES (solo el administrador). "
                    + "% = 100 × (puntual + tardanza) / días registrados, con 2 decimales; `porcentaje` nulo = «No disponible». "
                    + "Los días esperados sin registro van en `noRegistrados` y no son faltas. El periodo en curso es parcial "
                    + "(`parcial`, `fechaCorte`): el día actual solo entra con la jornada cerrada. "
                    + "403 FUERA_DE_ALCANCE (sección ajena o ámbito no permitido, FA-3); 422 PERIODO_INVALIDO (periodo futuro, FA-3); "
                    + "422 SIN_REGISTROS_ASISTENCIA (sin asistencias en el alcance y el periodo: no se genera el archivo, FA-2); "
                    + "422 CALENDARIO_NO_DISPONIBLE (sin año activo o, en docentes, sin horario publicado: no se inventan faltas, FA-4).")
    @ApiResponse(responseCode = "200", description = "El reporte: JSON con `formato=json`; archivo con `xlsx` o `pdf`", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ReporteAsistenciaDto.class)),
            @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))})
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ASISTENCIA_REPORTE_SECCION', 'ASISTENCIA_REPORTE_GENERAL')")
    ResponseEntity<?> reporte(@RequestParam Ambito ambito, @RequestParam(required = false) Integer mes, @RequestParam(required = false) Integer anio,
                              @RequestParam(required = false) String seccion,
                              @RequestParam(defaultValue = "json") String formato) {
        Formato f = formato(formato);
        if (f == Formato.JSON) {
            return ResponseEntity.ok(reportes.generar(ambito, anio, mes, seccion));
        }
        ArchivoReporte archivo = reportes.archivo(ambito, anio, mes, seccion, f);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .contentType(MediaType.parseMediaType(archivo.tipoContenido()))
                .body(archivo.contenido());
    }

    private static Formato formato(String texto) {
        try {
            return Formato.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new pe.edu.gesedu.compartido.error.ExcepcionNegocio(pe.edu.gesedu.compartido.error.CodigoError.VALIDACION_INVALIDA,
                    "El formato debe ser json, xlsx o pdf");
        }
    }
}
