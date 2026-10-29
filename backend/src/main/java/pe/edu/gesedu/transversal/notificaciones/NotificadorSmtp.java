package pe.edu.gesedu.transversal.notificaciones;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Correo por SMTP; en desarrollo local lo recibe Mailpit (http://localhost:8025). Lo crea
 * {@link ConfiguracionNotificaciones} con {@code gesedu.notificaciones.tipo=smtp}.
 */
public class NotificadorSmtp implements Notificador {

    private final JavaMailSender correo;
    private final String remitente;

    public NotificadorSmtp(JavaMailSender correo, String remitente) {
        this.correo = correo;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String destinatario, String asunto, String cuerpoHtml) {
        try {
            MimeMessage mensaje = correo.createMimeMessage();
            MimeMessageHelper ayuda = new MimeMessageHelper(mensaje, false, "UTF-8");
            ayuda.setFrom(remitente);
            ayuda.setTo(destinatario);
            ayuda.setSubject(asunto);
            ayuda.setText(cuerpoHtml, true);
            correo.send(mensaje);
        } catch (MessagingException | MailException e) {
            throw new IllegalStateException("No se pudo enviar el correo por SMTP", e);
        }
    }
}
