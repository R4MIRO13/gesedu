package pe.edu.gesedu.auditoria.aplicacion;

import java.util.List;

/** Un módulo y las acciones que tiene registradas, para el filtro de la consulta. */
public record ModuloAuditoriaDto(String modulo, List<String> acciones) {
}
