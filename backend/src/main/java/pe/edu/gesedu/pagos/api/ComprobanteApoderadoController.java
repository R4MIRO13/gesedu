package pe.edu.gesedu.pagos.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ComprobanteResumen;
import pe.edu.gesedu.pagos.aplicacion.PresentacionComprobanteService;
import pe.edu.gesedu.pagos.aplicacion.PresentacionComprobanteService.ArchivoSubida;
import pe.edu.gesedu.pagos.aplicacion.PresentacionComprobanteService.DatosComprobante;

/** El apoderado presenta el comprobante de pago de una obligación de su hijo (UC-04, RF-08). */
@Tag(name = "Pagos")
@RestController
@RequestMapping("/api/v1/obligaciones/{obligacionId}/comprobantes")
class ComprobanteApoderadoController {

    record ArchivoRequest(@Schema(example = "voucher_septiembre.jpg") @NotBlank @Size(max = 200) String nombreArchivo,
                          @Schema(example = "image/jpeg") @NotBlank @Size(max = 100) String contentType,
                          @Schema(example = "1258291") @Min(1) long tamanoBytes) {
    }

    record ComprobanteRequest(@Schema(example = "00458712") @NotBlank @Size(max = 40)
                              @Pattern(regexp = "^[A-Za-z0-9-]*$", message = "El número de operación admite letras, números y guiones") String numeroOperacion,
                              @Schema(example = "2027-09-28") @NotNull LocalDate fechaPago,
                              @Schema(description = "Monto pagado en soles como texto decimal", example = "350.00")
                              @NotNull @Pattern(regexp = "^\\d{1,10}(\\.\\d{1,2})?$",
                                      message = "El monto debe ser un decimal con hasta 2 decimales") String montoPagado,
                              @Schema(example = "Transferencia bancaria - BCP") @NotBlank @Size(max = 30) String medioPago,
                              @Schema(description = "Clave devuelta al pedir la URL de subida") @NotBlank @Size(max = 255) String claveObjeto,
                              @Schema(example = "image/jpeg") @NotBlank @Size(max = 100) String contentType,
                              @Min(1) long tamanoBytes) {
    }

    private final PresentacionComprobanteService comprobantes;

    ComprobanteApoderadoController(PresentacionComprobanteService comprobantes) {
        this.comprobantes = comprobantes;
    }

    @Operation(summary = "Pide una URL prefirmada de 5 minutos para subir el voucher (PDF, JPG o PNG de hasta 5 MB)")
    @PostMapping("/archivo")
    @PreAuthorize("hasAuthority('COMPROBANTE_PRESENTAR')")
    ArchivoSubida pedirSubida(@PathVariable String obligacionId, @Valid @RequestBody ArchivoRequest solicitud) {
        return comprobantes.solicitarSubida(obligacionId, solicitud.nombreArchivo(), solicitud.contentType(),
                solicitud.tamanoBytes());
    }

    @Operation(summary = "Presenta el comprobante: queda PENDIENTE de revisión y la obligación no cambia. 422 si la "
            + "obligación ya está pagada; 409 COMPROBANTE_PENDIENTE o NUMERO_OPERACION_DUPLICADO; 403 si la "
            + "obligación no es de un hijo del apoderado")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMPROBANTE_PRESENTAR')")
    ComprobanteResumen presentar(@PathVariable String obligacionId, @Valid @RequestBody ComprobanteRequest solicitud) {
        return comprobantes.presentar(obligacionId, new DatosComprobante(solicitud.numeroOperacion().trim(),
                solicitud.fechaPago(), Dinero.de(solicitud.montoPagado()), solicitud.medioPago(),
                solicitud.claveObjeto(), solicitud.contentType(), solicitud.tamanoBytes()));
    }
}
