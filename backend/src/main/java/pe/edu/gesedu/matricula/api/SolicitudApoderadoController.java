package pe.edu.gesedu.matricula.api;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.ArchivoSubida;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.OpcionesSolicitud;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.SolicitudPropia;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.SolicitudRegistrada;
import pe.edu.gesedu.matricula.aplicacion.SolicitudApoderadoService;
import pe.edu.gesedu.matricula.aplicacion.SolicitudApoderadoService.DocumentoPresentado;
import pe.edu.gesedu.matricula.aplicacion.SolicitudApoderadoService.NuevaSolicitud;
import pe.edu.gesedu.matricula.dominio.TipoEstudiante;
import pe.edu.gesedu.seguridad.aplicacion.RegistroFamilias.DatosAlumno;

/** Solicitud de matrícula del apoderado (UC-01, RF-02, RF-08) y el seguimiento de sus solicitudes. */
@Tag(name = "Matrícula")
@RestController
@RequestMapping("/api/v1/solicitudes-matricula")
class SolicitudApoderadoController {

    record ArchivoRequest(@Schema(example = "certificado-estudios.pdf") @NotBlank @Size(max = 200) String nombreArchivo,
                          @Schema(example = "application/pdf") @NotBlank @Size(max = 100) String contentType,
                          @Schema(example = "184320") @Min(1) long tamanoBytes) {
    }

    record AlumnoRequest(@Schema(example = "Valeria") @NotBlank @Size(max = 100) String nombres,
                         @Schema(example = "Torres Gil") @NotBlank @Size(max = 100) String apellidos,
                         @Schema(example = "77100004") @NotBlank
                         @Pattern(regexp = "^\\d{8}$", message = "El documento de identidad debe tener 8 dígitos") String documento,
                         @Schema(example = "2012-09-09") @NotNull @Past LocalDate fechaNacimiento,
                         @Schema(example = "PADRE") @NotBlank @Size(max = 30) String parentesco) {
    }

    record DocumentoRequest(@Schema(example = "CERTIFICADO_ESTUDIOS") @NotBlank @Size(max = 30) String tipo,
                            @NotBlank @Size(max = 255) String claveObjeto,
                            @NotBlank @Size(max = 200) String nombreArchivo,
                            @NotBlank @Size(max = 100) String contentType,
                            @Min(1) long tamanoBytes) {
    }

    record SolicitudRequest(@Schema(example = "2027") @Min(2000) @Max(2999) int anio,
                            @Schema(example = "NUEVO_INGRESO") @NotNull TipoEstudiante tipoEstudiante,
                            @NotBlank String gradoId,
                            @NotNull @Valid AlumnoRequest alumno,
                            @Schema(example = "977100004") @Size(max = 20) String telefonoApoderado,
                            @NotNull List<@Valid DocumentoRequest> documentos) {
    }

    private final SolicitudApoderadoService solicitudes;

    SolicitudApoderadoController(SolicitudApoderadoService solicitudes) {
        this.solicitudes = solicitudes;
    }

    @Operation(summary = "Grados del año y documentos que se exigen según el tipo de estudiante (paso 1)")
    @GetMapping("/opciones")
    @PreAuthorize("hasAuthority('MATRICULA_SOLICITAR')")
    OpcionesSolicitud opciones(@RequestParam int anio) {
        return solicitudes.opciones(anio);
    }

    @Operation(summary = "Pide una URL prefirmada de 5 minutos para subir un documento (PDF, JPG o PNG de hasta 5 MB). "
            + "El navegador sube el archivo directo y luego declara la clave en la solicitud")
    @PostMapping("/archivos")
    @PreAuthorize("hasAuthority('MATRICULA_SOLICITAR')")
    ArchivoSubida pedirSubida(@Valid @RequestBody ArchivoRequest solicitud) {
        return solicitudes.solicitarSubida(solicitud.nombreArchivo(), solicitud.contentType(), solicitud.tamanoBytes());
    }

    @Operation(summary = "Registra la solicitud (pasos 4 a 7): queda «Pendiente de revisión» con un código de "
            + "seguimiento. 400 si faltan campos o documentos; 409 SOLICITUD_EN_TRAMITE o DOCUMENTO_DUPLICADO")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MATRICULA_SOLICITAR')")
    SolicitudRegistrada registrar(@Valid @RequestBody SolicitudRequest solicitud) {
        AlumnoRequest a = solicitud.alumno();
        return solicitudes.registrar(new NuevaSolicitud(solicitud.anio(), solicitud.tipoEstudiante(), solicitud.gradoId(),
                new DatosAlumno(a.nombres(), a.apellidos(), a.documento(), a.fechaNacimiento(), a.parentesco()),
                solicitud.telefonoApoderado(), solicitud.documentos().stream()
                .map(d -> new DocumentoPresentado(d.tipo(), d.claveObjeto(), d.nombreArchivo(), d.contentType(),
                        d.tamanoBytes()))
                .toList()));
    }

    @Operation(summary = "Mis solicitudes: estado de cada trámite del apoderado, de la más reciente a la más antigua")
    @GetMapping("/mias")
    @PreAuthorize("hasAuthority('MATRICULA_SOLICITAR')")
    List<SolicitudPropia> mias() {
        return solicitudes.mias();
    }
}
