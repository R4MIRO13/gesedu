package pe.edu.gesedu.seguridad.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.compartido.web.Etags;
import pe.edu.gesedu.seguridad.aplicacion.DatosPersonalesService;
import pe.edu.gesedu.seguridad.aplicacion.DatosPersonalesService.CambiosAlumno;
import pe.edu.gesedu.seguridad.aplicacion.DatosPersonalesService.CambiosApoderado;
import pe.edu.gesedu.seguridad.aplicacion.DatosPersonalesService.FichaAlumno;
import pe.edu.gesedu.seguridad.aplicacion.DatosPersonalesService.FichaApoderado;

/**
 * Datos personales de estudiantes y apoderados (RF-02). Solo el administrador (`USUARIO_GESTIONAR`). La ficha devuelve
 * `ETag` con la versión de la cuenta y la edición exige `If-Match`.
 */
@Tag(name = "Seguridad")
@RestController
@RequestMapping("/api/v1")
class DatosPersonalesController {

    // Formato amplio: un documento antiguo (por ejemplo, de un extranjero) se puede conservar al editar otros datos;
    // un documento nuevo debe ser un DNI de 8 dígitos y lo exige el servicio (S-50)
    private static final String DOCUMENTO_INVALIDO = "El documento de identidad solo admite letras, números y guiones";
    private static final String FORMATO_DOCUMENTO = "^[A-Za-z0-9-]{4,20}$";

    record AlumnoRequest(@Schema(example = "Valeria") @NotBlank @Size(max = 100) String nombres,
                         @Schema(example = "Torres Gil") @NotBlank @Size(max = 100) String apellidos,
                         @Schema(example = "77100004") @NotBlank @Pattern(regexp = FORMATO_DOCUMENTO, message = DOCUMENTO_INVALIDO) String documento,
                         @Schema(description = "Opcional; la cuenta de un estudiante puede no tener correo") @Email @Size(max = 254) String correo,
                         @Schema(example = "2012-09-09") @NotNull @Past LocalDate fechaNacimiento,
                         @Schema(example = "PADRE") @NotBlank @Size(max = 30) String parentesco) {
    }

    record ApoderadoRequest(@Schema(example = "Marcos") @NotBlank @Size(max = 100) String nombres,
                            @Schema(example = "Torres Lima") @NotBlank @Size(max = 100) String apellidos,
                            @Schema(example = "40100004") @NotBlank @Pattern(regexp = FORMATO_DOCUMENTO, message = DOCUMENTO_INVALIDO) String documento,
                            @Schema(example = "familia.torres@demo.gesedu.pe") @Email @Size(max = 254) String correo,
                            @Schema(example = "977100004") @Size(max = 20) String telefono) {
    }

    private final DatosPersonalesService datos;

    DatosPersonalesController(DatosPersonalesService datos) {
        this.datos = datos;
    }

    @Operation(summary = "Ficha del estudiante para editarla. Devuelve ETag con la versión de la cuenta")
    @GetMapping("/alumnos/{id}")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    ResponseEntity<FichaAlumno> alumno(@PathVariable String id) {
        FichaAlumno ficha = datos.obtenerAlumno(id);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etags.de(ficha.version())).body(ficha);
    }

    @Operation(summary = "Edita los datos personales del estudiante (RF-02). Exige If-Match; 409 DOCUMENTO_DUPLICADO, "
            + "CORREO_DUPLICADO o CONFLICTO_CONCURRENCIA. Queda auditado con el valor anterior y el nuevo")
    @PutMapping("/alumnos/{id}")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    ResponseEntity<FichaAlumno> actualizarAlumno(@PathVariable String id,
                                                 @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                 @Valid @RequestBody AlumnoRequest s) {
        FichaAlumno ficha = datos.actualizarAlumno(id, new CambiosAlumno(s.nombres(), s.apellidos(), s.documento(),
                s.correo(), s.fechaNacimiento(), s.parentesco()), ifMatch);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etags.de(ficha.version())).body(ficha);
    }

    @Operation(summary = "Ficha del apoderado para editarla. Devuelve ETag con la versión de la cuenta")
    @GetMapping("/apoderados/{id}")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    ResponseEntity<FichaApoderado> apoderado(@PathVariable String id) {
        FichaApoderado ficha = datos.obtenerApoderado(id);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etags.de(ficha.version())).body(ficha);
    }

    @Operation(summary = "Edita los datos personales del apoderado (RF-02). Exige If-Match; 409 DOCUMENTO_DUPLICADO, "
            + "CORREO_DUPLICADO o CONFLICTO_CONCURRENCIA. Queda auditado con el valor anterior y el nuevo")
    @PutMapping("/apoderados/{id}")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    ResponseEntity<FichaApoderado> actualizarApoderado(@PathVariable String id,
                                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                       @Valid @RequestBody ApoderadoRequest s) {
        FichaApoderado ficha = datos.actualizarApoderado(id, new CambiosApoderado(s.nombres(), s.apellidos(),
                s.documento(), s.correo(), s.telefono()), ifMatch);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etags.de(ficha.version())).body(ficha);
    }
}
