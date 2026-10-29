package pe.edu.gesedu.auditoria.aplicacion;

/** Un campo que cambió en la operación auditada (RF-49): valor anterior y nuevo, como texto. */
public record CambioAuditoriaDto(String campo, String valorAnterior, String valorNuevo) {
}
