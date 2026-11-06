package pe.edu.gesedu.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;

/** Cabeceras de seguridad de las respuestas (tarea 6.5, ISO/IEC 27002:2022 8.9). */
class CabecerasSeguridadIT extends BaseIT {

    private HttpResponse<String> pedir(String ruta, String protocolo) throws Exception {
        HttpRequest.Builder p = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET();
        if (protocolo != null) {
            p.header("X-Forwarded-Proto", protocolo);
        }
        return HttpClient.newHttpClient().send(p.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void lasRespuestasLlevanLasCabecerasDeSeguridad() throws Exception {
        // 401 de un endpoint protegido: las cabeceras también deben salir en los errores
        for (String ruta : new String[] {"/api/v1/auth/yo", "/actuator/health", "/v3/api-docs"}) {
            HttpResponse<String> r = pedir(ruta, null);
            var h = r.headers();
            assertThat(h.firstValue("X-Content-Type-Options")).as(ruta).contains("nosniff");
            assertThat(h.firstValue("X-Frame-Options")).as(ruta).contains("DENY");
            assertThat(h.firstValue("Content-Security-Policy").orElse("")).as(ruta).contains("frame-ancestors 'none'");
            assertThat(h.firstValue("Referrer-Policy")).as(ruta).contains("no-referrer");
            assertThat(h.firstValue("Permissions-Policy").orElse("")).as(ruta).contains("camera=()");
        }
        assertThat(pedir("/api/v1/auth/yo", null).headers().firstValue("Cache-Control").orElse(""))
                .contains("no-store");
    }

    @Test
    void hstsSoloSeEnviaEnPeticionesHttps() throws Exception {
        assertThat(pedir("/actuator/health", null).headers().firstValue("Strict-Transport-Security")).isEmpty();
        assertThat(pedir("/actuator/health", "https").headers().firstValue("Strict-Transport-Security").orElse(""))
                .contains("max-age=31536000").contains("includeSubDomains");
    }

    @Test
    void laSwaggerUiSigueFuncionandoConLaPolitica() throws Exception {
        HttpResponse<String> r = pedir("/swagger-ui/index.html", null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Security-Policy").orElse("")).doesNotContain("default-src");
    }
}
