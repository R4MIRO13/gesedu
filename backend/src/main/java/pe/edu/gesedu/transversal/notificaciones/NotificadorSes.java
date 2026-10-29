package pe.edu.gesedu.transversal.notificaciones;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/** Correo por Amazon SES (perfil aws). Lo crea {@link ConfiguracionNotificaciones} solo con {@code tipo=ses}. */
public class NotificadorSes implements Notificador {

    private final SesV2Client ses;
    private final String remitente;

    public NotificadorSes(SesV2Client ses, String remitente) {
        this.ses = ses;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String destinatario, String asunto, String cuerpoHtml) {
        ses.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(remitente)
                .destination(Destination.builder().toAddresses(destinatario).build())
                .content(EmailContent.builder().simple(Message.builder()
                        .subject(Content.builder().data(asunto).charset("UTF-8").build())
                        .body(Body.builder().html(Content.builder().data(cuerpoHtml).charset("UTF-8").build()).build())
                        .build()).build())
                .build());
    }
}
