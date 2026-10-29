package pe.edu.gesedu.seguridad.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import pe.edu.gesedu.seguridad.infraestructura.PropiedadesSeguridad;

/** Emisión y validación del JWT de acceso HS256 (DA-04): vigencia de 15 minutos y rechazo de tokens ajenos. */
class JwtServiceTest {

    private static final String SECRETO = "clave-de-pruebas-de-al-menos-32-caracteres-0123456789";
    private static final Instant AHORA = Instant.parse("2027-03-01T13:00:00Z");

    private static SecretKey clave(String secreto) {
        return new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static EmisorToken emisor(String secreto, Instant instante) {
        PropiedadesSeguridad propiedades = new PropiedadesSeguridad(secreto, 15, 5, 15, 7, true, 30);
        return new EmisorToken(new NimbusJwtEncoder(new ImmutableSecret<>(clave(secreto))), propiedades,
                Clock.fixed(instante, ZoneId.of("America/Lima")));
    }

    private static JwtDecoder decodificador() {
        return NimbusJwtDecoder.withSecretKey(clave(SECRETO)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Test
    void elTokenIdentificaAlUsuarioYVenceALosQuinceMinutos() {
        EmisorToken.TokenEmitido emitido = emisor(SECRETO, AHORA.minus(Duration.ofMinutes(1))).emitir("usuario-1");

        Jwt jwt = decodificador().decode(emitido.valor());

        assertThat(jwt.getSubject()).isEqualTo("usuario-1");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("gesedu");
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(emitido.expiraEn()).isEqualTo(jwt.getExpiresAt());
        // El token de acceso no lleva permisos: se leen de la cuenta en cada petición
        assertThat(jwt.getClaims()).doesNotContainKeys("permisos", "roles", "scope");
    }

    @Test
    void unTokenVencidoSeRechaza() {
        String vencido = emisor(SECRETO, Instant.now().minus(Duration.ofHours(1))).emitir("usuario-1").valor();

        assertThatThrownBy(() -> decodificador().decode(vencido)).isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        String ajeno = emisor("otra-clave-distinta-de-al-menos-32-caracteres-xyz", Instant.now()).emitir("u").valor();

        assertThatThrownBy(() -> decodificador().decode(ajeno)).isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenAlteradoSeRechaza() {
        String valido = emisor(SECRETO, Instant.now()).emitir("usuario-1").valor();
        String[] partes = valido.split("\\.");
        // Se cambia el contenido (sub) conservando la firma original
        String carga = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"administrador\",\"iss\":\"gesedu\"}".getBytes(StandardCharsets.UTF_8));
        String alterado = partes[0] + "." + carga + "." + partes[2];

        assertThatThrownBy(() -> decodificador().decode(alterado)).isInstanceOf(JwtException.class);
    }

    @Test
    void laZonaDelRelojNoCambiaElInstanteDeVencimiento() {
        Instant ahora = Instant.now();
        EmisorToken.TokenEmitido lima = emisor(SECRETO, ahora).emitir("u");
        EmisorToken.TokenEmitido utc = new EmisorToken(new NimbusJwtEncoder(new ImmutableSecret<>(clave(SECRETO))),
                new PropiedadesSeguridad(SECRETO, 15, 5, 15, 7, true, 30), Clock.fixed(ahora, ZoneOffset.UTC)).emitir("u");

        assertThat(lima.expiraEn()).isEqualTo(utc.expiraEn());
    }
}
