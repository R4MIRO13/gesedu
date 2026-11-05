package pe.edu.gesedu.asistencia.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.asistencia.aplicacion.AsistenciaSeccionDtos.AsistenciaSeccionDto;
import pe.edu.gesedu.asistencia.aplicacion.AsistenciaSeccionDtos.ItemAsistencia;
import pe.edu.gesedu.asistencia.aplicacion.AsistenciaSeccionDtos.MiSeccionDto;
import pe.edu.gesedu.asistencia.aplicacion.AsistenciaSeccionService;
import pe.edu.gesedu.asistencia.dominio.EstadoAsistencia;

/** Asistencia de los alumnos de la sección del tutor (UC-08, RF-14). Solo el tutor de la sección la ve y la registra. */
@Tag(name = "Asistencia")
@RestController
@RequestMapping("/api/v1")
class AsistenciaSeccionController {

    record ItemAsistenciaRequest(@NotBlank String alumnoId, @NotNull EstadoAsistencia estado) {
    }

    record RegistroSeccionRequest(@NotEmpty List<@Valid ItemAsistenciaRequest> asistencias) {
    }

    private final AsistenciaSeccionService asistencia;

    AsistenciaSeccionController(AsistenciaSeccionService asistencia) {
        this.asistencia = asistencia;
    }

    @Operation(summary = "La sección que tengo a cargo como tutor (código, nombre y alumnos)",
            description = "403 FUERA_DE_ALCANCE si no soy tutor de ninguna sección (UC-08 FA-3); 422 DIA_NO_LECTIVO si hoy no es lectivo.")
    @GetMapping("/asistencia-alumnos/mi-seccion")
    @PreAuthorize("hasAuthority('ASISTENCIA_ALUMNO_REGISTRAR')")
    MiSeccionDto miSeccion() {
        return asistencia.miSeccion();
    }

    @Operation(summary = "Los alumnos de la sección con su asistencia de hoy, en orden alfabético y con los totales",
            description = "`{codigo}` es el grado y la letra, por ejemplo `3A`. `estado` nulo = sin registro (no es falta). "
                    + "Con la jornada cerrada `registroHabilitado` es falso y la pantalla queda en consulta. "
                    + "403 FUERA_DE_ALCANCE si no soy el tutor de la sección; 422 DIA_NO_LECTIVO.")
    @GetMapping("/secciones/{codigo}/asistencia")
    @PreAuthorize("hasAuthority('ASISTENCIA_ALUMNO_REGISTRAR')")
    AsistenciaSeccionDto consultar(@PathVariable String codigo) {
        return asistencia.consultar(codigo);
    }

    @Operation(summary = "Pasa lista: guarda el estado inicial de todos los alumnos pendientes en un solo lote atómico",
            description = "Cada estado es PUNTUAL, TARDANZA o FALTA (lo decide el tutor, sin umbral automático). "
                    + "400 con `errores[]` si faltan alumnos o alguno no es de la sección (no se guarda nada, FA-1); "
                    + "409 ASISTENCIA_YA_REGISTRADA con los registros vigentes en `registro` si algún alumno ya tenía asistencia "
                    + "(no se sobrescribe ni se guarda nada del lote, FA-2 y FA-5); 422 JORNADA_CERRADA o DIA_NO_LECTIVO (FA-4); "
                    + "422 SECCION_SIN_ALUMNOS (FA-3). La fecha y la hora son las del servidor.")
    @PostMapping("/secciones/{codigo}/asistencia")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ASISTENCIA_ALUMNO_REGISTRAR')")
    AsistenciaSeccionDto registrar(@PathVariable String codigo, @Valid @RequestBody RegistroSeccionRequest solicitud) {
        return asistencia.registrar(codigo, solicitud.asistencias().stream()
                .map(i -> new ItemAsistencia(i.alumnoId(), i.estado())).toList());
    }
}
