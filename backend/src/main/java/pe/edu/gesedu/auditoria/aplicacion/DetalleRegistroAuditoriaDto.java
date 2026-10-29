package pe.edu.gesedu.auditoria.aplicacion;

import java.util.List;

/** Un registro de auditoría con sus cambios de campo. */
public record DetalleRegistroAuditoriaDto(RegistroAuditoriaDto registro, List<CambioAuditoriaDto> cambios) {
}
