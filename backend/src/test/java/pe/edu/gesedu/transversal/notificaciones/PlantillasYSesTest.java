package pe.edu.gesedu.transversal.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/** Plantillas HTML de correo y el envío por SES. */
class PlantillasYSesTest {

    private final Plantillas plantillas = new Plantillas();

    @Test
    void reemplazaLasMarcasYEscapaElHtml() {
        String html = plantillas.renderizar("prueba", Map.of("nombre", "Ana & <b>Luis</b>", "codigo", 42));

        assertThat(html).isEqualTo("<p>Hola Ana &amp; &lt;b&gt;Luis&lt;/b&gt;, tu código es 42.</p>\n");
    }

    @Test
    void unaMarcaSinDatoOUnaPlantillaInexistenteSonErrores() {
        assertThatThrownBy(() -> plantillas.renderizar("prueba", Map.of("nombre", "Ana")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("codigo");
        assertThatThrownBy(() -> plantillas.renderizar("no-existe", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plantillas.renderizar("../application", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sesEnviaUnCorreoHtmlUtf8() {
        SesV2Client ses = mock(SesV2Client.class);
        new NotificadorSes(ses, "no-reply@gesedu.pe").enviar("apoderado@demo.gesedu.pe", "Matrícula", "<p>Hola</p>");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).sendEmail(captor.capture());
        SendEmailRequest pedido = captor.getValue();
        assertThat(pedido.fromEmailAddress()).isEqualTo("no-reply@gesedu.pe");
        assertThat(pedido.destination().toAddresses()).containsExactly("apoderado@demo.gesedu.pe");
        assertThat(pedido.content().simple().subject().data()).isEqualTo("Matrícula");
        assertThat(pedido.content().simple().body().html().data()).isEqualTo("<p>Hola</p>");
        assertThat(pedido.content().simple().body().html().charset()).isEqualTo("UTF-8");
    }
}
