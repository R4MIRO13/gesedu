package pe.edu.gesedu.evaluacion.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.evaluacion.aplicacion.NotasFinalesDtos.NotasFinalesDto;
import pe.edu.gesedu.evaluacion.aplicacion.NotasFinalesService;

/** Notas finales de un curso en una sección (UC-17, RF-28, RF-29). Solo el docente de la asignación. */
@Tag(name = "Evaluación")
@RestController
@RequestMapping("/api/v1/asignaciones-docentes")
class NotasFinalesController {

    private final NotasFinalesService notasFinales;

    NotasFinalesController(NotasFinalesService notasFinales) {
        this.notasFinales = notasFinales;
    }

    @Operation(summary = "Los alumnos con el logro de cada bimestre y la nota anual de cada competencia",
            description = "`desactualizada` es verdadero si lo registrado después del último cálculo (logros, regla o pesos) daría otras "
                    + "notas. 403 FUERA_DE_ALCANCE si la asignación no es mía.")
    @GetMapping("/{id}/notas-finales")
    @PreAuthorize("hasAuthority('NOTA_CALCULAR_FINAL')")
    NotasFinalesDto consultar(@PathVariable String id) {
        return notasFinales.consultar(id);
    }

    @Operation(summary = "Calcula la nota anual de cada competencia con la regla del curso (idempotente)",
            description = "Repetirlo no duplica ni cambia nada si los datos son iguales; si cambiaron, reemplaza la nota anterior. "
                    + "`calculo` cuenta las notas creadas, actualizadas, sin cambios y las celdas sin ningún logro. "
                    + "422 SIN_LOGROS_REGISTRADOS si el curso no tiene ningún logro; 422 ANIO_CERRADO.")
    @PostMapping("/{id}/notas-finales/calculo")
    @PreAuthorize("hasAuthority('NOTA_CALCULAR_FINAL')")
    NotasFinalesDto calcular(@PathVariable String id) {
        return notasFinales.calcular(id);
    }
}
