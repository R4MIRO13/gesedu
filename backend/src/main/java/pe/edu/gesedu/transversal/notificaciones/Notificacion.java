package pe.edu.gesedu.transversal.notificaciones;

import java.util.Map;

/**
 * Correo por enviar: la plantilla es el nombre de un archivo de {@code plantillas/correo/} (sin extensión) y
 * {@code datos} reemplaza sus marcas {@code {{clave}}}.
 */
public record Notificacion(String destinatario, String asunto, String plantilla, Map<String, Object> datos) {
}
