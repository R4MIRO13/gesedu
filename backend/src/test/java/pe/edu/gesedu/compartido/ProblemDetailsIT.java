package pe.edu.gesedu.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;

/** Formato RFC 9457 y propiedad {@code codigo} en los errores del framework (docs/PLAN.md § 5.1). */
class ProblemDetailsIT extends BaseIT {

    @Test
    void cuerpoInvalidoResponde400ConErroresPorCampo() throws Exception {
        Respuesta r = post("/api/v1/auth/inicio-sesion", "{}", null);

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.tipoContenido()).contains("application/problem+json");
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
        assertThat(r.cuerpo().get("type").asString()).isEqualTo("https://gesedu.pe/problemas/VALIDACION_INVALIDA");
        assertThat(r.cuerpo().get("errores").size()).isPositive();
    }

    @Test
    void rutaInexistenteResponde404ConCodigo() throws Exception {
        String token = tokenDe(crearUsuario("ADMINISTRADOR", true));
        Respuesta r = get("/api/v1/no-existe", token);

        assertThat(r.estado()).isEqualTo(404);
        assertThat(r.tipoContenido()).contains("application/problem+json");
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("RECURSO_NO_ENCONTRADO");
    }

    @Test
    void metodoNoPermitidoResponde4xxConCodigo() throws Exception {
        String token = tokenDe(crearUsuario("ADMINISTRADOR", true));
        Respuesta r = post("/api/v1/auth/yo", "{}", token);

        assertThat(r.estado()).isBetween(400, 499);
        assertThat(r.cuerpo().get("codigo").asString()).isNotBlank();
    }
}
