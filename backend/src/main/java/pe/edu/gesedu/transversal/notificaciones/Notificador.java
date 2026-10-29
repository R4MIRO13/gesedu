package pe.edu.gesedu.transversal.notificaciones;

/**
 * Transporte de correo (DA-06 de la arquitectura, Tabla 7): SMTP hacia Mailpit en local ({@code NotificadorSmtp})
 * y SES en AWS ({@code NotificadorSes}). Envía de inmediato; los módulos no lo usan directamente sino a través de
 * {@link Notificaciones}, que lo invoca después del commit y con reintentos.
 */
public interface Notificador {

    /** Envía un correo HTML. Lanza una excepción si el transporte falla. */
    void enviar(String destinatario, String asunto, String cuerpoHtml);
}
