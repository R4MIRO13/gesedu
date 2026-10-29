package pe.edu.gesedu.pagos.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.pagos.aplicacion.ConsultaObligacionesService;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.ObligacionesAlumno;

/** Obligaciones de pago de los hijos del apoderado (UC-03, RF-07, RF-09, RF-11). */
@Tag(name = "Pagos")
@RestController
@RequestMapping("/api/v1/alumnos/{alumnoId}/obligaciones")
class ObligacionController {

    private final ConsultaObligacionesService consulta;

    ObligacionController(ConsultaObligacionesService consulta) {
        this.consulta = consulta;
    }

    @Operation(summary = "Obligaciones de un hijo del apoderado en los años no cerrados: concepto, periodo, importe "
            + "base, penalidad, total, vencimiento y estado, con su boleta y el estado del último comprobante. "
            + "403 si el estudiante no está a cargo del apoderado")
    @GetMapping
    @PreAuthorize("hasAuthority('OBLIGACION_CONSULTAR_PROPIA')")
    ObligacionesAlumno obligaciones(@PathVariable String alumnoId) {
        return consulta.deMiHijo(alumnoId);
    }
}
