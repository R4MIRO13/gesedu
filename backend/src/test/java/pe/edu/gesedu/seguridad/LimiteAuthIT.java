package pe.edu.gesedu.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import pe.edu.gesedu.apoyo.BaseIT;

/** Límite de peticiones por IP a {@code /auth/*} (RNF-06): la 4.ª petición del minuto responde 429. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "gesedu.seguridad.limite-auth-por-minuto=3")
class LimiteAuthIT extends BaseIT {

    @Test
    void despuesDelLimiteElInicioDeSesionResponde429ProblemJson() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(iniciarSesion("nadie@test.gesedu.pe", "x").estado()).isEqualTo(401);
        }

        Respuesta r = iniciarSesion("nadie@test.gesedu.pe", "x");

        assertThat(r.estado()).isEqualTo(429);
        assertThat(r.tipoContenido()).startsWith("application/problem+json");
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("LIMITE_PETICIONES_EXCEDIDO");
        assertThat(r.cabeceras().firstValue("Retry-After")).contains("60");
        // El refresco comparte el contador de la IP
        assertThat(postConCookie("/api/v1/auth/refresco", null, null).estado()).isEqualTo(429);
    }

    @Test
    void lasPeticionesQueNoSonDeAutenticacionNoSeLimitan() throws Exception {
        for (int i = 0; i < 6; i++) {
            assertThat(get("/actuator/health", null).estado()).isEqualTo(200);
        }
    }
}
