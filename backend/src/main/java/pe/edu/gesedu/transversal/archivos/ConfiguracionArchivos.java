package pe.edu.gesedu.transversal.archivos;

import java.net.URI;
import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Activa {@link PropiedadesArchivos} y, solo con {@code tipo=s3}, el cliente y el firmador de S3. */
@Configuration
@EnableConfigurationProperties(PropiedadesArchivos.class)
class ConfiguracionArchivos {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "gesedu.archivos.tipo", havingValue = "s3")
    S3Client clienteS3(PropiedadesArchivos propiedades) {
        var constructor = S3Client.builder()
                .region(Region.of(propiedades.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build());
        if (hayEndpoint(propiedades)) {
            // LocalStack: el bucket va en la ruta, no en el subdominio
            constructor.endpointOverride(URI.create(propiedades.endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return constructor.build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "gesedu.archivos.tipo", havingValue = "s3")
    S3Presigner firmadorS3(PropiedadesArchivos propiedades) {
        var constructor = S3Presigner.builder()
                .region(Region.of(propiedades.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build());
        if (hayEndpoint(propiedades)) {
            constructor.endpointOverride(URI.create(propiedades.endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return constructor.build();
    }

    @Bean
    @ConditionalOnProperty(name = "gesedu.archivos.tipo", havingValue = "s3")
    GestorArchivos gestorArchivosS3(S3Presigner firmador, S3Client cliente, PropiedadesArchivos propiedades,
                                    Clock reloj) {
        return new GestorArchivosS3(firmador, cliente, propiedades.bucket(), reloj);
    }

    private static boolean hayEndpoint(PropiedadesArchivos propiedades) {
        return propiedades.endpoint() != null && !propiedades.endpoint().isBlank();
    }
}
