package pe.edu.gesedu.matricula.api;

import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.compartido.paginacion.Pagina;
import pe.edu.gesedu.compartido.web.Etags;
import pe.edu.gesedu.compartido.web.ParametrosPaginacion;
import pe.edu.gesedu.matricula.aplicacion.EvaluacionMatriculaService;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.DetalleSolicitud;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.ResultadoConfirmacion;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.ResultadoRechazo;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.SolicitudResumen;
import pe.edu.gesedu.matricula.dominio.EstadoAdmision;
import pe.edu.gesedu.matricula.dominio.EstadoSolicitud;
import pe.edu.gesedu.matricula.dominio.MotivoRechazo;
import pe.edu.gesedu.matricula.dominio.TipoEstudiante;
import pe.edu.gesedu.transversal.archivos.GestorArchivos.UrlPrefirmada;

/**
 * Evaluación de solicitudes de matrícula (UC-02, RF-04, RF-05, RF-07, RF-12). El detalle devuelve {@code ETag} con
 * la versión de la solicitud; las operaciones que la modifican exigen {@code If-Match}.
 */
@Tag(name = "Matrícula")
@RestController
@RequestMapping("/api/v1/solicitudes-matricula")
class SolicitudMatriculaController {

    private static final Set<String> ORDENABLES = Set.of("fechaSolicitud", "codigoSeguimiento");

    record EvaluacionRequest(@Schema(example = "NUEVO_INGRESO") @NotNull TipoEstudiante tipoEstudiante,
                             @Schema(example = "ADMITIDO") @NotNull EstadoAdmision estadoAdmision) {
    }

    record ConfirmacionRequest(@Schema(description = "Sección elegida, del mismo grado que la solicitud")
                               @NotBlank String seccionId,
                               @Schema(description = "El apoderado aceptó el resumen de costos (debe ser true)",
                                       example = "true") @NotNull Boolean costosAceptados) {
    }

    record RechazoRequest(@Schema(example = "VOLUNTAD_APODERADO") @NotNull MotivoRechazo motivo,
                          @Schema(example = "El apoderado desiste de la matrícula") @Size(max = 500) String observacion) {
    }

    private final EvaluacionMatriculaService evaluacion;

    SolicitudMatriculaController(EvaluacionMatriculaService evaluacion) {
        this.evaluacion = evaluacion;
    }

    @Operation(summary = "Bandeja de solicitudes (paso 1), paginada y filtrable por estado y texto")
    @GetMapping
    @PreAuthorize("hasAuthority('MATRICULA_EVALUAR')")
    Pagina<SolicitudResumen> listar(@RequestParam(required = false) EstadoSolicitud estado,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size,
                                    @RequestParam(required = false) String sort) {
        return evaluacion.listar(estado, q, ParametrosPaginacion.de(page, size, sort, ORDENABLES,
                Sort.by(Sort.Direction.ASC, "fechaSolicitud", "codigoSeguimiento")));
    }

    @Operation(summary = "Detalle para evaluar: datos, validaciones, vacantes por sección y resumen de costos. "
            + "Devuelve ETag con la versión")
    @GetMapping("/{codigo}")
    @PreAuthorize("hasAuthority('MATRICULA_EVALUAR')")
    ResponseEntity<DetalleSolicitud> detalle(@PathVariable String codigo) {
        DetalleSolicitud detalle = evaluacion.obtener(codigo);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etags.de(detalle.version())).body(detalle);
    }

    @Operation(summary = "URL prefirmada de 5 minutos para ver un documento de la solicitud")
    @GetMapping("/{codigo}/documentos/{documentoId}/url")
    @PreAuthorize("hasAuthority('MATRICULA_EVALUAR')")
    UrlPrefirmada urlDocumento(@PathVariable String codigo, @PathVariable String documentoId) {
        return evaluacion.urlDocumento(codigo, documentoId);
    }

    @Operation(summary = "Registra el tipo de estudiante y el resultado de la admisión (C-37). Exige If-Match; "
            + "devuelve el detalle actualizado")
    @PutMapping("/{codigo}/evaluacion")
    @PreAuthorize("hasAuthority('MATRICULA_REGISTRAR')")
    ResponseEntity<DetalleSolicitud> registrarEvaluacion(@PathVariable String codigo,
                                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                         @Valid @RequestBody EvaluacionRequest solicitud) {
        evaluacion.registrarEvaluacion(codigo, solicitud.tipoEstudiante(), solicitud.estadoAdmision(), ifMatch);
        return detalle(codigo);
    }

    @Operation(summary = "Confirma la matrícula en una sola transacción: vacante, matrícula, compromisos de pago y "
            + "correo al apoderado. Exige If-Match. 422 si una validación falla; 409 SIN_VACANTE o CONFLICTO_CONCURRENCIA")
    @PostMapping("/{codigo}/confirmacion")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MATRICULA_REGISTRAR')")
    ResultadoConfirmacion confirmar(@PathVariable String codigo,
                                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                    @Valid @RequestBody ConfirmacionRequest solicitud) {
        return evaluacion.confirmar(codigo, solicitud.seccionId(), solicitud.costosAceptados(), ifMatch);
    }

    @Operation(summary = "Rechaza la solicitud (FA-1 a FA-5). El motivo debe coincidir con las validaciones, salvo "
            + "VOLUNTAD_APODERADO. Exige If-Match; avisa al apoderado por correo")
    @PostMapping("/{codigo}/rechazo")
    @PreAuthorize("hasAuthority('MATRICULA_REGISTRAR')")
    ResultadoRechazo rechazar(@PathVariable String codigo,
                              @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                              @Valid @RequestBody RechazoRequest solicitud) {
        return evaluacion.rechazar(codigo, solicitud.motivo(), solicitud.observacion(), ifMatch);
    }
}
