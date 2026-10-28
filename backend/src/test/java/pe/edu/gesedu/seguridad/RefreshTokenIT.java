package pe.edu.gesedu.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;

/** Refresh token rotativo con detección de reutilización y cierre de sesión revocable (DA-04, RF-52, UC-24 FA-4). */
class RefreshTokenIT extends BaseIT {

    private static final String COOKIE = "gesedu_refresh";

    private static String cabeceraCookie(String valor) {
        return COOKIE + "=" + valor;
    }

    @Test
    void elInicioDeSesionEntregaElRefreshSoloEnUnaCookieSegura() throws Exception {
        Respuesta r = iniciarSesion(crearUsuario("DOCENTE", true), CONTRASENA);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().has("tokenAcceso")).isTrue();
        assertThat(r.cuerpo().toString()).doesNotContain("refresh");
        String cookie = r.cookieCompleta(COOKIE);
        assertThat(cookie).contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .contains("Path=/api/v1/auth").contains("Max-Age=604800");
        // En la base solo queda el hash SHA-256 (64 hex), nunca el valor de la cookie
        String valor = r.valorCookie(COOKIE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seguridad.refresh_token WHERE token_hash = ?",
                Integer.class, valor)).isZero();
    }

    @Test
    void refrescarEntregaUnTokenDeAccesoNuevoYRotaElRefresh() throws Exception {
        Respuesta inicio = iniciarSesion(crearUsuario("ADMINISTRADOR", true), CONTRASENA);
        String primero = inicio.valorCookie(COOKIE);

        Respuesta r = postConCookie("/api/v1/auth/refresco", null, cabeceraCookie(primero));

        assertThat(r.estado()).isEqualTo(200);
        String nuevoAcceso = r.cuerpo().get("tokenAcceso").asString();
        assertThat(r.cuerpo().get("usuario").get("rol").asString()).isEqualTo("ADMINISTRADOR");
        String segundo = r.valorCookie(COOKIE);
        assertThat(segundo).isNotBlank().isNotEqualTo(primero);
        assertThat(get("/api/v1/auth/yo", nuevoAcceso).estado()).isEqualTo(200);
        // Misma familia, el primero queda revocado y apunta al segundo
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT familia) FROM seguridad.refresh_token "
                + "WHERE usuario_id = (SELECT usuario_id FROM seguridad.refresh_token ORDER BY created_at DESC LIMIT 1)",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seguridad.refresh_token "
                + "WHERE revocado_en IS NOT NULL AND reemplazado_por IS NOT NULL", Integer.class)).isPositive();
    }

    @Test
    void reutilizarUnRefreshYaRotadoRevocaLaFamiliaEntera() throws Exception {
        String primero = iniciarSesion(crearUsuario("DOCENTE", true), CONTRASENA).valorCookie(COOKIE);
        Respuesta rotado = postConCookie("/api/v1/auth/refresco", null, cabeceraCookie(primero));
        String segundo = rotado.valorCookie(COOKIE);

        // Alguien presenta el primero otra vez: se asume robo
        Respuesta reutilizado = postConCookie("/api/v1/auth/refresco", null, cabeceraCookie(primero));
        assertThat(reutilizado.estado()).isEqualTo(401);
        assertThat(reutilizado.cuerpo().get("codigo").asString()).isEqualTo("SESION_EXPIRADA");
        assertThat(reutilizado.cookieCompleta(COOKIE)).contains("Max-Age=0");

        // ...y el segundo, que era legítimo, ya no sirve
        assertThat(postConCookie("/api/v1/auth/refresco", null, cabeceraCookie(segundo)).estado()).isEqualTo(401);
    }

    @Test
    void sinCookieOConUnValorDesconocidoResponde401() throws Exception {
        assertThat(postConCookie("/api/v1/auth/refresco", null, null).cuerpo().get("codigo").asString())
                .isEqualTo("SESION_EXPIRADA");
        assertThat(postConCookie("/api/v1/auth/refresco", null, cabeceraCookie("inventado")).estado()).isEqualTo(401);
    }

    @Test
    void cerrarSesionRevocaElRefreshYElSiguienteRefrescoDa401() throws Exception {
        Respuesta inicio = iniciarSesion(crearUsuario("APODERADO", true), CONTRASENA);
        String acceso = inicio.cuerpo().get("tokenAcceso").asString();
        String refresco = inicio.valorCookie(COOKIE);

        Respuesta cierre = postConCookie("/api/v1/auth/cierre-sesion", acceso, cabeceraCookie(refresco));

        assertThat(cierre.estado()).isEqualTo(204);
        assertThat(cierre.cookieCompleta(COOKIE)).contains("Max-Age=0");
        Respuesta siguiente = postConCookie("/api/v1/auth/refresco", null, cabeceraCookie(refresco));
        assertThat(siguiente.estado()).isEqualTo(401);
        assertThat(siguiente.cuerpo().get("codigo").asString()).isEqualTo("SESION_EXPIRADA");
    }

    @Test
    void cerrarSesionExigeEstarAutenticado() throws Exception {
        assertThat(postConCookie("/api/v1/auth/cierre-sesion", null, null).estado()).isEqualTo(401);
    }

    @Test
    void unaCuentaDesactivadaNoPuedeRefrescarYPierdeSusSesiones() throws Exception {
        String correo = crearUsuario("DOCENTE", true);
        String refresco = iniciarSesion(correo, CONTRASENA).valorCookie(COOKIE);

        jdbc.update("UPDATE seguridad.usuario SET activo = FALSE WHERE correo = ?", correo);

        Respuesta r = postConCookie("/api/v1/auth/refresco", null, cabeceraCookie(refresco));
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("CUENTA_INACTIVA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seguridad.refresh_token t JOIN seguridad.usuario u "
                + "ON u.id = t.usuario_id WHERE u.correo = ? AND t.revocado_en IS NULL", Integer.class, correo)).isZero();
    }

    @Test
    void unRefreshVencidoNoSirve() throws Exception {
        String correo = crearUsuario("DOCENTE", true);
        String refresco = iniciarSesion(correo, CONTRASENA).valorCookie(COOKIE);

        jdbc.update("UPDATE seguridad.refresh_token SET expira_en = now() - interval '1 minute' "
                + "WHERE usuario_id = (SELECT id FROM seguridad.usuario WHERE correo = ?)", correo);

        assertThat(postConCookie("/api/v1/auth/refresco", null, cabeceraCookie(refresco)).estado()).isEqualTo(401);
    }
}
