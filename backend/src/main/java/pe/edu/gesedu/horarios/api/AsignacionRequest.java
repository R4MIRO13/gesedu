package pe.edu.gesedu.horarios.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Una clase del borrador: bloque, sección, curso y docente. Al validar una edición se manda también el id de la
 * clase que se edita para que no choque consigo misma; al guardar una edición, la sección se toma de la clase.
 */
record AsignacionRequest(
        @Schema(description = "Bloque horario (martes de 9:15 a 10:00 en la demo)", example = "0192f1c2-7b1e-7c5a-9d3e-4a6f8b2c1d00")
        @NotBlank String bloqueId,
        @Schema(description = "Sección (3° A)", example = "0192f1c2-7b1e-7c5a-9d3e-4a6f8b2c1d01")
        @NotBlank String seccionId,
        @Schema(description = "Curso (Matemática)", example = "0192f1c2-7b1e-7c5a-9d3e-4a6f8b2c1d02")
        @NotBlank String cursoId,
        @Schema(description = "Docente asociado al curso y disponible en el bloque", example = "0192f1c2-7b1e-7c5a-9d3e-4a6f8b2c1d03")
        @NotBlank String docenteId,
        @Schema(description = "Solo al validar una edición: la clase que se está editando", nullable = true)
        String asignacionId) {
}
