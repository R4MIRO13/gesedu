package pe.edu.gesedu.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import pe.edu.gesedu.apoyo.BaseIT;

/** JWT y {@code @PreAuthorize}: 401 y 403 salen como RFC 9457 con {@code codigo} (RF-50, RNF-07). */
class JwtYPermisosIT extends BaseIT {

    @Autowired
    private JwtEncoder codificadorJwt;

    @Test
    void sinTokenResponde401NoAutenticado() throws Exception {
        Respuesta r = get("/api/v1/prueba/horario", null);

        assertProblema(r, 401, "NO_AUTENTICADO");
        assertThat(r.cuerpo().get("instance").asString()).isEqualTo("/api/v1/prueba/horario");
    }

    @Test
    void unTokenManipuladoResponde401() throws Exception {
        String token = tokenDe(crearUsuario("ADMINISTRADOR", true));
        String manipulado = token.substring(0, token.length() - 4) + "AAAA";

        assertProblema(get("/api/v1/prueba/horario", manipulado), 401, "NO_AUTENTICADO");
    }

    @Test
    void unTokenVencidoResponde401SesionExpirada() throws Exception {
        String id = idDe(crearUsuario("ADMINISTRADOR", true));
        Instant emitido = Instant.now().minus(Duration.ofHours(1));
        String vencido = codificadorJwt.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("gesedu").subject(id).issuedAt(emitido)
                        .expiresAt(emitido.plus(Duration.ofMinutes(15))).build())).getTokenValue();

        assertProblema(get("/api/v1/prueba/horario", vencido), 401, "SESION_EXPIRADA");
    }

    @Test
    void conElPermisoPasaYSinElPermisoResponde403() throws Exception {
        String administrador = tokenDe(crearUsuario("ADMINISTRADOR", true));
        String alumno = tokenDe(crearUsuario("ALUMNO", true));

        assertThat(get("/api/v1/prueba/auditoria", administrador).estado()).isEqualTo(200);
        assertThat(get("/api/v1/prueba/horario", alumno).estado()).isEqualTo(200);
        assertProblema(get("/api/v1/prueba/auditoria", alumno), 403, "ACCESO_DENEGADO");
    }

    @Test
    void unaCuentaDesactivadaPierdeElAccesoAunqueSuTokenSigaVigente() throws Exception {
        String correo = crearUsuario("ADMINISTRADOR", true);
        String token = tokenDe(correo);
        assertThat(get("/api/v1/prueba/horario", token).estado()).isEqualTo(200);

        jdbc.update("UPDATE seguridad.usuario SET activo = FALSE WHERE correo = ?", correo);
        invalidarCuentas(); // lo que hará el servicio de usuarios al desactivar una cuenta

        assertProblema(get("/api/v1/prueba/horario", token), 401, "CUENTA_INACTIVA");
    }

    @Test
    void unaCuentaBloqueadaPierdeElAccesoAunqueSuTokenSigaVigente() throws Exception {
        String correo = crearUsuario("ADMINISTRADOR", true);
        String token = tokenDe(correo);

        jdbc.update("UPDATE seguridad.usuario SET bloqueado_hasta = now() + interval '10 minutes' WHERE correo = ?",
                correo);
        invalidarCuentas();

        assertProblema(get("/api/v1/prueba/horario", token), 401, "CUENTA_BLOQUEADA");
    }

    @Test
    void unEndpointInexistenteSinTokenNoRevelaSiExiste() throws Exception {
        assertProblema(get("/api/v1/no-existe", null), 401, "NO_AUTENTICADO");
    }

    @Test
    void unEndpointInexistenteConTokenResponde404ConCodigo() throws Exception {
        Respuesta r = get("/api/v1/no-existe", tokenDe(crearUsuario("ADMINISTRADOR", true)));

        assertProblema(r, 404, "RECURSO_NO_ENCONTRADO");
    }

    @Test
    void yoDevuelveLosDatosYPermisosDelUsuarioAutenticado() throws Exception {
        Respuesta r = get("/api/v1/auth/yo", tokenDe(crearUsuario("ALUMNO", true)));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("rol").asString()).isEqualTo("ALUMNO");
        assertThat(r.cuerpo().get("permisos").valueStream().map(p -> p.asString()).toList())
                .contains("HORARIO_CONSULTAR").doesNotContain("AUDITORIA_CONSULTAR");
        assertProblema(get("/api/v1/auth/yo", null), 401, "NO_AUTENTICADO");
    }

    @Test
    void laDocumentacionDeLaApiEsPublicaYDeclaraElEsquemaBearer() throws Exception {
        Respuesta api = get("/v3/api-docs", null);

        assertThat(api.estado()).isEqualTo(200);
        assertThat(api.cuerpo().get("components").get("securitySchemes").get("bearer").get("scheme").asString())
                .isEqualTo("bearer");
        assertThat(get("/v3/api-docs/3-auditoria", null).cuerpo().get("paths").has("/api/v1/auditoria/registros"))
                .isTrue();
        assertThat(get("/swagger-ui/index.html", null).estado()).isEqualTo(200);
    }

    @Test
    void laSaludDelServicioEsPublica() throws Exception {
        assertThat(get("/actuator/health", null).estado()).isEqualTo(200);
    }

    private static void assertProblema(Respuesta r, int estado, String codigo) {
        assertThat(r.estado()).isEqualTo(estado);
        assertThat(r.tipoContenido()).startsWith("application/problem+json");
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo(codigo);
        assertThat(r.cuerpo().get("status").asInt()).isEqualTo(estado);
        assertThat(r.cuerpo().get("type").asString()).isEqualTo("https://gesedu.pe/problemas/" + codigo);
    }
}
