package pe.edu.gesedu.pagos.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.pagos.aplicacion.ConfiguracionCobrosService;
import pe.edu.gesedu.pagos.aplicacion.ConfiguracionCobrosService.ConfiguracionCobros;
import pe.edu.gesedu.pagos.aplicacion.ConfiguracionCobrosService.LineaCobro;
import pe.edu.gesedu.pagos.dominio.ConceptoPago;

/** Costos y política de penalidad del año escolar (RF-06). */
@Tag(name = "Pagos")
@RestController
@RequestMapping("/api/v1/anios-escolares/{anio}/cobros")
class ConfiguracionCobrosController {

    record LineaRequest(@Schema(example = "PENSION") @NotNull ConceptoPago concepto,
                        @Schema(description = "El año (matrícula y cuota de ingreso) o AAAA-MM (pensión)",
                                example = "2027-03") @NotNull @Size(max = 20) String periodo,
                        @Schema(description = "Monto en soles como texto decimal", example = "350.00")
                        @NotNull @Pattern(regexp = "^\\d{1,10}(\\.\\d{1,2})?$",
                                message = "El monto debe ser un decimal con hasta 2 decimales") String monto,
                        @Schema(example = "2027-03-31") @NotNull LocalDate fechaVencimiento) {
    }

    record CobrosRequest(@Schema(example = "5.00") @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal porcentajePenalidad,
                         @NotEmpty List<@Valid LineaRequest> lineas) {
    }

    private final ConfiguracionCobrosService cobros;

    ConfiguracionCobrosController(ConfiguracionCobrosService cobros) {
        this.cobros = cobros;
    }

    @Operation(summary = "Cronograma de cobros y porcentaje de penalidad del año")
    @GetMapping
    @PreAuthorize("hasAuthority('COBRO_CONFIGURAR')")
    ConfiguracionCobros obtener(@PathVariable int anio) {
        return cobros.obtener(anio);
    }

    @Operation(summary = "Guarda el cronograma completo (reemplaza al anterior) y la penalidad. Queda auditado")
    @PutMapping
    @PreAuthorize("hasAuthority('COBRO_CONFIGURAR')")
    ConfiguracionCobros guardar(@PathVariable int anio, @Valid @RequestBody CobrosRequest solicitud) {
        return cobros.guardar(anio, solicitud.porcentajePenalidad(), solicitud.lineas().stream()
                .map(l -> new LineaCobro(l.concepto(), l.periodo(), Dinero.de(l.monto()), l.fechaVencimiento()))
                .toList());
    }
}
