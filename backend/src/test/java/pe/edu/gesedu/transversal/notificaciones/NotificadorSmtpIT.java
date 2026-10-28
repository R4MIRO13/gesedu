package pe.edu.gesedu.transversal.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** El correo llega de verdad a Mailpit (el mismo que se usa en desarrollo local) por SMTP. */
class NotificadorSmtpIT {

    private static GenericContainer<?> mailpit;

    @BeforeAll
    static void iniciar() {
        mailpit = new GenericContainer<>("axllent/mailpit:v1.27")
                .withExposedPorts(1025, 8025)
                .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025).withStartupTimeout(Duration.ofSeconds(60)));
        mailpit.start();
    }

    @AfterAll
    static void detener() {
        if (mailpit != null) {
            mailpit.stop();
        }
    }

    @Test
    void elCorreoHtmlLlegaConAsuntoRemitenteYDestinatario() throws Exception {
        JavaMailSenderImpl correo = new JavaMailSenderImpl();
        correo.setHost(mailpit.getHost());
        correo.setPort(mailpit.getMappedPort(1025));
        correo.setDefaultEncoding("UTF-8");

        new NotificadorSmtp(correo, "no-reply@gesedu.pe")
                .enviar("apoderado@demo.gesedu.pe", "Matrícula confirmada", "<p>Tu cronograma: <b>11 pagos</b></p>");

        HttpResponse<String> respuesta = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025)
                        + "/api/v1/messages")).GET().build(), HttpResponse.BodyHandlers.ofString());
        JsonNode mensajes = JsonMapper.builder().build().readTree(respuesta.body());

        assertThat(mensajes.get("total").asInt()).isEqualTo(1);
        JsonNode mensaje = mensajes.get("messages").get(0);
        assertThat(mensaje.get("Subject").asString()).isEqualTo("Matrícula confirmada");
        assertThat(mensaje.get("From").get("Address").asString()).isEqualTo("no-reply@gesedu.pe");
        assertThat(mensaje.get("To").get(0).get("Address").asString()).isEqualTo("apoderado@demo.gesedu.pe");
    }
}
