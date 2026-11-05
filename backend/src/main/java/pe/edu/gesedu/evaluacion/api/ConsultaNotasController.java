package pe.edu.gesedu.evaluacion.api;

import java.util.List;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.AlumnoNotasDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.ArchivoNotas;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.CursoNotasDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.CursosAlumnoDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.LibretaDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.LibretaResumenDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasService;

/** Notas y libreta del alumno y de sus hijos (UC-19, RF-30, RF-32 a RF-37). El alcance es «yo» o «mis hijos». */
@Tag(name = "Evaluación")
@RestController
@RequestMapping("/api/v1")
class ConsultaNotasController {

    private final ConsultaNotasService notas;

    ConsultaNotasController(ConsultaNotasService notas) {
        this.notas = notas;
    }

    @Operation(summary = "Los alumnos que puedo consultar: yo mismo (alumno) o mis hijos (apoderado)",
            description = "Con más de un hijo el apoderado elige uno (FA-1). Trae la sección vigente de cada uno, si la tiene.")
    @GetMapping("/apoderados/yo/alumnos")
    @PreAuthorize("hasAuthority('NOTA_CONSULTAR_PROPIA')")
    List<AlumnoNotasDto> misAlumnos() {
        return notas.misAlumnos();
    }

    @Operation(summary = "Los cursos de la sección del alumno con sus competencias y las notas consolidadas por bimestre",
            description = "`anio` nulo = el de la matrícula vigente o el de la última libreta; `bimestre` nulo = el último consolidado. "
                    + "Solo trae notas ya consolidadas. 403 FUERA_DE_ALCANCE si el alumno no es el propio ni un hijo.")
    @GetMapping("/alumnos/{alumnoId}/cursos")
    @PreAuthorize("hasAuthority('NOTA_CONSULTAR_PROPIA')")
    CursosAlumnoDto cursos(@PathVariable String alumnoId, @RequestParam(required = false) Integer anio,
                           @RequestParam(required = false) Integer bimestre) {
        return notas.cursos(alumnoId, anio, bimestre);
    }

    @Operation(summary = "El detalle de un curso: competencias con su nivel esperado y las notas de cada bimestre")
    @GetMapping("/alumnos/{alumnoId}/cursos/{cursoId}/notas")
    @PreAuthorize("hasAuthority('NOTA_CONSULTAR_PROPIA')")
    CursoNotasDto curso(@PathVariable String alumnoId, @PathVariable String cursoId, @RequestParam(required = false) Integer anio) {
        return notas.curso(alumnoId, cursoId, anio);
    }

    @Operation(summary = "Las libretas del alumno por año escolar (PARCIAL o FINAL)")
    @GetMapping("/alumnos/{alumnoId}/libretas")
    @PreAuthorize("hasAuthority('NOTA_CONSULTAR_PROPIA')")
    List<LibretaResumenDto> libretas(@PathVariable String alumnoId) {
        return notas.libretas(alumnoId);
    }

    @Operation(summary = "La libreta de notas del año o el informe de progreso de un bimestre, en JSON o PDF",
            description = "Sin `bimestre` es la libreta completa (con la nota final si ya existe, FA-2); con `bimestre`, el informe de progreso "
                    + "hasta ese periodo. `formato` json (por omisión) o pdf. Sin notas consolidadas: 422 SIN_NOTAS_CONSOLIDADAS.")
    @ApiResponse(responseCode = "200", description = "JSON con `formato=json`; PDF con `formato=pdf`", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = LibretaDto.class)),
            @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))})
    @GetMapping("/alumnos/{alumnoId}/libreta")
    @PreAuthorize("hasAuthority('NOTA_CONSULTAR_PROPIA')")
    ResponseEntity<?> libreta(@PathVariable String alumnoId, @RequestParam(required = false) Integer anio,
                              @RequestParam(required = false) Integer bimestre, @RequestParam(defaultValue = "json") String formato) {
        String f = formato.trim().toLowerCase();
        if (f.equals("json")) {
            return ResponseEntity.ok(notas.libreta(alumnoId, anio, bimestre));
        }
        if (!f.equals("pdf")) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "El formato debe ser json o pdf");
        }
        ArchivoNotas archivo = notas.libretaPdf(alumnoId, anio, bimestre);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .contentType(MediaType.parseMediaType(archivo.tipoContenido()))
                .body(archivo.contenido());
    }
}
