package pe.edu.gesedu.transversal.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.gesedu.apoyo.BaseIT;

/** Los correos salen después del commit, en otro hilo y con hasta 3 intentos (S-16, RF-12). */
class NotificacionesIT extends BaseIT {

    @MockitoBean
    private Notificador notificador;

    @Autowired
    private Notificaciones notificaciones;

    @Autowired
    private PlatformTransactionManager gestorTransacciones;

    private static Notificacion correo() {
        return new Notificacion("apoderado@demo.gesedu.pe", "Matrícula confirmada", "prueba",
                Map.of("nombre", "María <Rojas>", "codigo", "MAT-2027-00482"));
    }

    @BeforeEach
    void limpiar() {
        reset(notificador);
    }

    @Test
    void siNoHayTransaccionElCorreoSaleEnSegundoPlano() {
        notificaciones.notificar(correo());

        verify(notificador, timeout(3000)).enviar(eq("apoderado@demo.gesedu.pe"), eq("Matrícula confirmada"),
                contains("MAT-2027-00482"));
    }

    @Test
    void laPlantillaEscapaElHtmlDeLosDatos() {
        notificaciones.notificar(correo());

        verify(notificador, timeout(3000)).enviar(anyString(), anyString(),
                eq("<p>Hola María &lt;Rojas&gt;, tu código es MAT-2027-00482.</p>\n"));
    }

    @Test
    void conTransaccionSaleSoloDespuesDelCommit() throws Exception {
        new TransactionTemplate(gestorTransacciones).executeWithoutResult(estado -> {
            notificaciones.notificar(correo());
            // Antes del commit no ha salido nada
            verify(notificador, never()).enviar(anyString(), anyString(), anyString());
        });

        verify(notificador, timeout(3000)).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void siLaTransaccionSeRevierteElCorreoNoSale() throws Exception {
        new TransactionTemplate(gestorTransacciones).executeWithoutResult(estado -> {
            notificaciones.notificar(correo());
            estado.setRollbackOnly();
        });

        // Se espera lo suficiente para que, de salir, ya hubiera salido
        Thread.sleep(500);
        verify(notificador, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void reintentaHastaTresVecesYSeDetieneAlTenerExito() {
        doThrow(new IllegalStateException("SMTP caído")).doThrow(new IllegalStateException("SMTP caído"))
                .doNothing().when(notificador).enviar(anyString(), anyString(), anyString());

        notificaciones.notificar(correo());

        verify(notificador, timeout(3000).times(3)).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void siFallanLosTresIntentosNoPropagaElErrorNiReintentaMas() throws Exception {
        doThrow(new IllegalStateException("SMTP caído")).when(notificador).enviar(anyString(), anyString(), anyString());

        notificaciones.notificar(correo());

        verify(notificador, timeout(3000).times(EnvioDeNotificaciones.INTENTOS)).enviar(anyString(), anyString(), anyString());
        Thread.sleep(300);
        verify(notificador, times(EnvioDeNotificaciones.INTENTOS)).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void unaPlantillaInexistenteNoEnviaNada() throws Exception {
        doNothing().when(notificador).enviar(anyString(), anyString(), anyString());

        notificaciones.notificar(new Notificacion("a@b.pe", "Asunto", "no-existe", Map.of()));

        Thread.sleep(300);
        assertThat(true).isTrue();
        verify(notificador, never()).enviar(anyString(), anyString(), anyString());
    }
}
