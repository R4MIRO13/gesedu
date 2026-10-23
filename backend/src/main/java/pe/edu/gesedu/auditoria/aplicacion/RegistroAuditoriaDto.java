package pe.edu.gesedu.auditoria.aplicacion;

import java.time.OffsetDateTime;

/**
 * Registro de auditoría para la consulta. {@code usuarioId} y {@code usuario} son {@code null} cuando el
 * actor fue «Sistema» (tareas programadas y acciones automáticas); {@code usuario} trae «Nombres Apellidos».
 */
public record RegistroAuditoriaDto(
        String id,
        OffsetDateTime fechaHora,
        String usuarioId,
        String usuario,
        String accion,
        String modulo,
        String tipoEntidad,
        String idEntidadAfectada,
        String descripcion) {
}
