package pe.edu.gesedu.transversal.notificaciones;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada de los módulos para enviar correos. {@link #notificar} no envía en el acto: el correo sale
 * después del commit de la transacción en curso (si se revierte, no sale) y fuera del hilo de la petición, con
 * hasta 3 intentos (S-16). Un fallo de correo nunca revierte la operación de negocio.
 */
@Component
public class Notificaciones {

    /** Evento interno; lo atiende {@link EnvioDeNotificaciones}. */
    record NotificacionSolicitada(Notificacion notificacion) {
    }

    private final ApplicationEventPublisher publicador;

    Notificaciones(ApplicationEventPublisher publicador) {
        this.publicador = publicador;
    }

    public void notificar(Notificacion notificacion) {
        publicador.publishEvent(new NotificacionSolicitada(notificacion));
    }
}
