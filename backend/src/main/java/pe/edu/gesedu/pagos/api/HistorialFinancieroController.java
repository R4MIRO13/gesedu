package pe.edu.gesedu.pagos.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.pagos.aplicacion.HistorialFinancieroService;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.AlumnoBusqueda;
import pe.edu.gesedu.pagos.aplicacion.PagosDtos.HistorialFinanciero;

/** Historial financiero de un estudiante para el administrador (UC-06, RF-11). Solo consulta. */
@Tag(name = "Pagos")
@RestController
@RequestMapping("/api/v1/alumnos")
class HistorialFinancieroController {

    private final HistorialFinancieroService historial;

    HistorialFinancieroController(HistorialFinancieroService historial) {
        this.historial = historial;
    }

    @Operation(summary = "Busca estudiantes por nombre, DNI o código (hasta 15) para elegir el historial que se consulta")
    @GetMapping("/busqueda")
    @PreAuthorize("hasAuthority('PAGO_HISTORIAL_CONSULTAR')")
    List<AlumnoBusqueda> buscar(@RequestParam String q) {
        return historial.buscar(q);
    }

    @Operation(summary = "Historial financiero del estudiante (todos los años): resumen, obligaciones con su boleta, "
            + "pagos registrados, deudas vencidas y penalidades. No modifica nada. `sinHistorial` (FA-1) y `sinPagos` (FA-2)")
    @GetMapping("/{codigoAlumno}/historial-financiero")
    @PreAuthorize("hasAuthority('PAGO_HISTORIAL_CONSULTAR')")
    HistorialFinanciero historial(@PathVariable String codigoAlumno) {
        return historial.deAlumno(codigoAlumno);
    }
}
