package pe.edu.gesedu.transversal.notificaciones;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/** Activa {@link PropiedadesNotificaciones}, el envío asíncrono y, solo con {@code tipo=ses}, el cliente de SES. */
@Configuration
@EnableAsync
@EnableConfigurationProperties(PropiedadesNotificaciones.class)
class ConfiguracionNotificaciones {

    @Bean
    @ConditionalOnProperty(name = "gesedu.notificaciones.tipo", havingValue = "smtp", matchIfMissing = true)
    Notificador notificadorSmtp(JavaMailSender correo, PropiedadesNotificaciones propiedades) {
        return new NotificadorSmtp(correo, propiedades.remitente());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "gesedu.notificaciones.tipo", havingValue = "ses")
    SesV2Client clienteSes(PropiedadesNotificaciones propiedades) {
        return SesV2Client.builder()
                .region(Region.of(propiedades.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "gesedu.notificaciones.tipo", havingValue = "ses")
    Notificador notificadorSes(SesV2Client ses, PropiedadesNotificaciones propiedades) {
        return new NotificadorSes(ses, propiedades.remitente());
    }
}
