package pe.edu.gesedu.seguridad.infraestructura;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** BCrypt (costo 12, RNF-01) y JWT HS256 con Nimbus (DA-04). */
@Configuration
@EnableConfigurationProperties(PropiedadesSeguridad.class)
class ConfiguracionCriptografia {

    private static final int COSTO_BCRYPT = 12;

    @Bean
    PasswordEncoder codificadorContrasenas() {
        return new BCryptPasswordEncoder(COSTO_BCRYPT);
    }

    @Bean
    JwtEncoder codificadorJwt(PropiedadesSeguridad propiedades) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave(propiedades)));
    }

    @Bean
    JwtDecoder decodificadorJwt(PropiedadesSeguridad propiedades) {
        return NimbusJwtDecoder.withSecretKey(clave(propiedades)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static SecretKey clave(PropiedadesSeguridad propiedades) {
        return new SecretKeySpec(propiedades.jwtSecreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
