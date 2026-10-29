package pe.edu.gesedu.pagos.api;

import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.compartido.paginacion.Pagina;
import pe.edu.gesedu.compartido.web.ParametrosPaginacion;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ComprobanteBandeja;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.DetalleComprobante;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ResultadoRevision;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ResultadoValidacion;
import pe.edu.gesedu.pagos.aplicacion.ValidacionPagoService;
import pe.edu.gesedu.pagos.dominio.EstadoRevision;

/** Revisión de comprobantes por el administrador (UC-05, RF-08 a RF-10, RNF-10, RNF-11). */
@Tag(name = "Pagos")
@RestController
@RequestMapping("/api/v1/comprobantes")
class ComprobanteController {

    private static final Set<String> ORDENABLES = Set.of("createdAt", "numeroOperacion", "montoPresentado");

    record RevisionRequest(@Schema(example = "El monto no cubre la penalidad: faltan S/ 17.50") @NotBlank
                           @Size(max = 500) String observacion) {
    }

    private final ValidacionPagoService pagos;

    ComprobanteController(ValidacionPagoService pagos) {
        this.pagos = pagos;
    }

    @Operation(summary = "Bandeja de comprobantes (paso 1), paginada; por defecto los PENDIENTES, del más antiguo al más reciente")
    @GetMapping
    @PreAuthorize("hasAuthority('COMPROBANTE_VALIDAR')")
    Pagina<ComprobanteBandeja> listar(@RequestParam(required = false, defaultValue = "PENDIENTE") EstadoRevision estado,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size,
                                      @RequestParam(required = false) String sort) {
        return pagos.listar(estado, ParametrosPaginacion.de(page, size, sort, ORDENABLES,
                Sort.by(Sort.Direction.ASC, "createdAt")));
    }

    @Operation(summary = "Detalle para revisar: obligación con importe base, penalidad y total, lo declarado por el "
            + "apoderado, las comprobaciones del sistema y la URL prefirmada del voucher (5 minutos)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPROBANTE_VALIDAR')")
    DetalleComprobante detalle(@PathVariable String id) {
        return pagos.obtener(id);
    }

    @Operation(summary = "Valida el pago (pasos 5 a 9): emite la boleta B001-NNNNN, aprueba el comprobante, deja la "
            + "obligación PAGADA y avisa al apoderado, todo en una transacción con la obligación bloqueada. "
            + "422 OBLIGACION_YA_PAGADA o MONTO_INSUFICIENTE; 409 si el comprobante ya fue revisado")
    @PostMapping("/{id}/validacion")
    @PreAuthorize("hasAuthority('COMPROBANTE_VALIDAR')")
    ResultadoValidacion validar(@PathVariable String id) {
        return pagos.validar(id);
    }

    @Operation(summary = "Observa el comprobante (FA-2): la obligación no cambia y el apoderado recibe el importe total "
            + "pendiente para presentar uno nuevo")
    @PostMapping("/{id}/observacion")
    @PreAuthorize("hasAuthority('COMPROBANTE_VALIDAR')")
    ResultadoRevision observar(@PathVariable String id, @Valid @RequestBody RevisionRequest solicitud) {
        return pagos.observar(id, solicitud.observacion());
    }

    @Operation(summary = "Rechaza el comprobante (FA-1): no corresponde al estudiante o a la obligación; la "
            + "obligación no cambia y el apoderado debe presentar la información correcta")
    @PostMapping("/{id}/rechazo")
    @PreAuthorize("hasAuthority('COMPROBANTE_VALIDAR')")
    ResultadoRevision rechazar(@PathVariable String id, @Valid @RequestBody RevisionRequest solicitud) {
        return pagos.rechazar(id, solicitud.observacion());
    }
}
