package pe.edu.gesedu.seguridad.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Tokens opacos: 32 bytes aleatorios en base64url y hash SHA-256 en hexadecimal. */
class TokensOpacosTest {

    @Test
    void generaValoresUnicosDe43CaracteresUrlSafe() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = TokensOpacos.generar();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
            vistos.add(token);
        }
        assertThat(vistos).hasSize(1000);
    }

    @Test
    void elHashEsSha256EnHexadecimalYDeterminista() {
        // SHA-256("abc")
        assertThat(TokensOpacos.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(TokensOpacos.hash("abc")).isEqualTo(TokensOpacos.hash("abc")).hasSize(64);
        assertThat(TokensOpacos.hash("abd")).isNotEqualTo(TokensOpacos.hash("abc"));
    }
}
