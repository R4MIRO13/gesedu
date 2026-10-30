package pe.edu.gesedu.academico.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.academico.aplicacion.CursoService;
import pe.edu.gesedu.academico.aplicacion.CursosDtos.CompetenciaSolicitud;
import pe.edu.gesedu.academico.aplicacion.CursosDtos.CopiaAnteriorDto;
import pe.edu.gesedu.academico.aplicacion.CursosDtos.CursoDto;
import pe.edu.gesedu.academico.aplicacion.CursosDtos.CursoSolicitud;
import pe.edu.gesedu.academico.aplicacion.CursosDtos.CursosAnioDto;
import pe.edu.gesedu.academico.aplicacion.CursosDtos.ResultadoCopiaDto;

/** Cursos y competencias del año escolar (UC-14, RF-23, RF-24). Los cursos son los mismos para todos los grados. */
@Tag(name = "Académico")
@RestController
@RequestMapping("/api/v1")
class CursoController {

    record CompetenciaRequest(@Schema(description = "Sin id es una competencia nueva; con id edita la existente. Las que no vienen se quitan")
                              String id,
                              @Schema(example = "Resuelve problemas de cantidad") @NotBlank @Size(max = 160) String nombre,
                              @Size(max = 500) String nivelEsperado) {
    }

    record CursoRequest(@Schema(example = "Matemática") @NotBlank @Size(max = 80) String nombre,
                        @Schema(example = "5") @NotNull @Min(1) @Max(40) Integer horasLectivas,
                        @NotNull @Size(min = 1, max = 12, message = "El curso necesita entre 1 y 12 competencias")
                        List<@Valid @NotNull CompetenciaRequest> competencias) {

        CursoSolicitud aSolicitud() {
            return new CursoSolicitud(nombre, horasLectivas, competencias.stream()
                    .map(c -> new CompetenciaSolicitud(c.id(), c.nombre(), c.nivelEsperado())).toList());
        }
    }

    record CopiaRequest(@Schema(description = "Cursos del año anterior que se copian; vacío o ausente = todos")
                        List<String> cursoIds) {
    }

    private final CursoService cursos;

    CursoController(CursoService cursos) {
        this.cursos = cursos;
    }

    @Operation(summary = "Cursos del año con sus competencias y el total de horas semanales")
    @GetMapping("/anios-escolares/{anio}/cursos")
    @PreAuthorize("hasAuthority('ACADEMICO_CONSULTAR')")
    CursosAnioDto listar(@PathVariable int anio) {
        return cursos.listar(anio);
    }

    @Operation(summary = "Crea un curso con sus competencias (códigos C1, C2…). 409 si el nombre ya existe en el año; "
            + "422 si el año está cerrado. Queda auditado")
    @PostMapping("/anios-escolares/{anio}/cursos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ACADEMICO_CONFIGURAR')")
    CursoDto crear(@PathVariable int anio, @Valid @RequestBody CursoRequest solicitud) {
        return cursos.crear(anio, solicitud.aSolicitud());
    }

    @Operation(summary = "Configuración de cursos y competencias del año anterior más reciente que tenga cursos (FA-1)",
            description = "Cada curso trae `yaExiste` si el año ya tiene uno con el mismo nombre.")
    @GetMapping("/anios-escolares/{anio}/cursos/copia-anterior")
    @PreAuthorize("hasAuthority('ACADEMICO_CONFIGURAR')")
    CopiaAnteriorDto copiaAnterior(@PathVariable int anio) {
        return cursos.copiaAnterior(anio);
    }

    @Operation(summary = "Copia al año los cursos elegidos del año anterior, con sus competencias (FA-1)",
            description = "Los cursos que el año ya tiene se omiten: repetir la copia no duplica nada.")
    @PostMapping("/anios-escolares/{anio}/cursos/copia-anterior")
    @PreAuthorize("hasAuthority('ACADEMICO_CONFIGURAR')")
    ResultadoCopiaDto copiar(@PathVariable int anio, @RequestBody(required = false) CopiaRequest solicitud) {
        return cursos.copiar(anio, solicitud == null ? null : solicitud.cursoIds());
    }

    @Operation(summary = "Un curso con sus competencias")
    @GetMapping("/cursos/{id}")
    @PreAuthorize("hasAuthority('ACADEMICO_CONSULTAR')")
    CursoDto obtener(@PathVariable String id) {
        return cursos.obtener(id);
    }

    @Operation(summary = "Edita el curso y su lista de competencias (agrega, edita y quita; RF-24)")
    @PutMapping("/cursos/{id}")
    @PreAuthorize("hasAuthority('ACADEMICO_CONFIGURAR')")
    CursoDto modificar(@PathVariable String id, @Valid @RequestBody CursoRequest solicitud) {
        return cursos.modificar(id, solicitud.aSolicitud());
    }

    @Operation(summary = "Elimina un curso que aún no se usa. 409 CURSO_EN_USO si tiene docentes, cargas, clases o logros")
    @DeleteMapping("/cursos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ACADEMICO_CONFIGURAR')")
    void eliminar(@PathVariable String id) {
        cursos.eliminar(id);
    }
}
