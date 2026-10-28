package pe.edu.gesedu.auditoria.aplicacion;

import java.util.List;

/** Valores disponibles para filtrar la auditoría (RF-51). */
public record CatalogosAuditoriaDto(List<ModuloAuditoriaDto> modulos, List<UsuarioAuditoriaDto> usuarios) {
}
