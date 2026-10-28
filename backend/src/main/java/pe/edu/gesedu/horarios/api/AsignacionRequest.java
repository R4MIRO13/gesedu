package pe.edu.gesedu.horarios.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Una clase del borrador: bloque, sección, curso y docente. Al validar una edición se manda también el id de la
 * clase que se edita para que no choque consigo misma; al guardar una edición, la sección se toma de la clase.
 */
record AsignacionRequest(
        @NotBlank String bloqueId,
        @NotBlank String seccionId,
        @NotBlank String cursoId,
        @NotBlank String docenteId,
        String asignacionId) {
}
