package pe.edu.gesedu.academico.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.academico.aplicacion.SeccionService;
import pe.edu.gesedu.academico.aplicacion.SeccionesDtos.GradosAnioDto;
import pe.edu.gesedu.academico.aplicacion.SeccionesDtos.SeccionConfiguracionDto;
import pe.edu.gesedu.compartido.web.Etags;

/** Grados y secciones del año con su capacidad, sus vacantes y su tutor (RF-03, RF-04). */
@Tag(name = "Académico")
@RestController
@RequestMapping("/api/v1")
class SeccionController {

    record NuevaSeccionRequest(@Schema(example = "C") @NotBlank @Pattern(regexp = "^[A-Za-z]$", message = "La letra es una sola letra de la A a la Z")
                               String letra,
                               @Schema(example = "30") @NotNull @Min(1) @Max(60) Integer capacidad,
                               @Schema(description = "Docente tutor; opcional") String tutorDocenteId) {
    }

    record SeccionRequest(@Schema(example = "30") @NotNull @Min(1) @Max(60) Integer capacidad,
                          @Schema(description = "Docente tutor; nulo la deja sin tutor") String tutorDocenteId) {
    }

    private final SeccionService secciones;

    SeccionController(SeccionService secciones) {
        this.secciones = secciones;
    }

    @Operation(summary = "Grados del año con sus secciones (capacidad, matriculados, vacantes y tutor) y los docentes "
            + "que pueden ser tutores")
    @GetMapping("/anios-escolares/{anio}/grados")
    @PreAuthorize("hasAuthority('ACADEMICO_CONSULTAR')")
    GradosAnioDto listar(@PathVariable int anio) {
        return secciones.listar(anio);
    }

    @Operation(summary = "Agrega una sección a un grado, con todas sus vacantes libres. 409 si la letra ya existe o el "
            + "docente ya es tutor de otra sección del año. Queda auditado")
    @PostMapping("/grados/{gradoId}/secciones")
    @PreAuthorize("hasAuthority('ACADEMICO_CONFIGURAR')")
    ResponseEntity<SeccionConfiguracionDto> crear(@PathVariable String gradoId, @Valid @RequestBody NuevaSeccionRequest solicitud) {
        SeccionConfiguracionDto creada = secciones.crear(gradoId, solicitud.letra(), solicitud.capacidad(), vacioANulo(solicitud.tutorDocenteId()));
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.ETAG, Etags.de(creada.version())).body(creada);
    }

    @Operation(summary = "Una sección con su versión en el ETag")
    @GetMapping("/secciones/{id}")
    @PreAuthorize("hasAuthority('ACADEMICO_CONSULTAR')")
    ResponseEntity<SeccionConfiguracionDto> obtener(@PathVariable String id) {
        SeccionConfiguracionDto dto = secciones.obtener(id);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etags.de(dto.version())).body(dto);
    }

    @Operation(summary = "Cambia la capacidad y el tutor de la sección. Exige If-Match",
            description = "Las vacantes se ajustan con la capacidad. 422 CAPACIDAD_INSUFICIENTE si queda por debajo de los "
                    + "matriculados; 409 CONFLICTO_CONCURRENCIA si la sección cambió; 409 TUTOR_YA_ASIGNADO.")
    @PutMapping("/secciones/{id}")
    @PreAuthorize("hasAuthority('ACADEMICO_CONFIGURAR')")
    ResponseEntity<SeccionConfiguracionDto> modificar(@PathVariable String id, @Valid @RequestBody SeccionRequest solicitud,
                                         @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        SeccionConfiguracionDto dto = secciones.modificar(id, solicitud.capacidad(), vacioANulo(solicitud.tutorDocenteId()), ifMatch);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etags.de(dto.version())).body(dto);
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }
}
