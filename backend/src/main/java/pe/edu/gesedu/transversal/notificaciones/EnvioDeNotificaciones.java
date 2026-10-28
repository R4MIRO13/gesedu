package pe.edu.gesedu.transversal.notificaciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía los correos solicitados con {@link Notificaciones}: solo tras el commit (o enseguida, si no hay
 * transacción), en un hilo aparte y con hasta {@value #INTENTOS} intentos (S-16).
 */
@Component
class EnvioDeNotificaciones {

    static final int INTENTOS = 3;
    private static final Logger LOG = LoggerFactory.getLogger(EnvioDeNotificaciones.class);

    private final Notificador notificador;
    private final Plantillas plantillas;
    private final PropiedadesNotificaciones propiedades;

    EnvioDeNotificaciones(Notificador notificador, Plantillas plantillas, PropiedadesNotificaciones propiedades) {
        this.notificador = notificador;
        this.plantillas = plantillas;
        this.propiedades = propiedades;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void alSolicitar(Notificaciones.NotificacionSolicitada evento) {
        Notificacion n = evento.notificacion();
        String cuerpo;
        try {
            cuerpo = plantillas.renderizar(n.plantilla(), n.datos());
        } catch (RuntimeException e) {
            LOG.error("No se pudo preparar el correo '{}' para {}", n.plantilla(), n.destinatario(), e);
            return;
        }
        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                notificador.enviar(n.destinatario(), n.asunto(), cuerpo);
                return;
            } catch (RuntimeException e) {
                LOG.warn("Falló el intento {}/{} de enviar el correo '{}' a {}", intento, INTENTOS, n.plantilla(),
                        n.destinatario(), e);
                if (intento < INTENTOS && !esperar()) {
                    return;
                }
            }
        }
        LOG.error("No se pudo enviar el correo '{}' a {} tras {} intentos", n.plantilla(), n.destinatario(), INTENTOS);
    }

    /** @return {@code false} si el hilo fue interrumpido y hay que abandonar los reintentos */
    private boolean esperar() {
        try {
            Thread.sleep(propiedades.reintentoMs());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
