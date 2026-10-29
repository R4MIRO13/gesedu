package pe.edu.gesedu.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;

/** Lista de usuarios con filtros y desbloqueo por el administrador (UC-24, RF-01). */
class UsuariosIT extends BaseIT {

    private String administrador() throws Exception {
        return tokenDe(crearUsuario("ADMINISTRADOR", true));
    }

    private void bloquear(String correo) throws Exception {
        for (int i = 0; i < 5; i++) {
            iniciarSesion(correo, "incorrecta");
        }
    }

    private List<String> correos(Respuesta r) {
        return r.cuerpo().get("contenido").valueStream().map(u -> u.get("correo").asString()).toList();
    }

    @Test
    void laListaMuestraElEstadoDeCadaCuentaYFiltra() throws Exception {
        String token = administrador();
        String activa = crearUsuario("DOCENTE", true);
        String inactiva = crearUsuario("DOCENTE", false);
        String bloqueada = crearUsuario("DOCENTE", true);
        bloquear(bloqueada);

        // Cada cuenta se busca por su correo (las demás pruebas dejan muchas cuentas parecidas)
        assertThat(estadoDe(token, activa)).isEqualTo("ACTIVO");
        assertThat(estadoDe(token, inactiva)).isEqualTo("INACTIVO");
        assertThat(estadoDe(token, bloqueada)).isEqualTo("BLOQUEADO");
        Respuesta fila = get("/api/v1/usuarios?q=" + bloqueada, token);
        assertThat(fila.cuerpo().get("contenido").get(0).get("bloqueadoHasta").asString()).endsWith("-05:00");
        assertThat(fila.cuerpo().get("contenido").get(0).get("rol").asString()).isEqualTo("DOCENTE");

        // Los filtros por estado y rol se combinan con la búsqueda
        assertThat(correos(get("/api/v1/usuarios?estado=BLOQUEADO&q=" + bloqueada, token))).containsExactly(bloqueada);
        assertThat(correos(get("/api/v1/usuarios?estado=ACTIVO&q=" + bloqueada, token))).isEmpty();
        assertThat(correos(get("/api/v1/usuarios?estado=ACTIVO&rol=DOCENTE&q=" + activa, token))).containsExactly(activa);
        assertThat(correos(get("/api/v1/usuarios?estado=INACTIVO&q=" + activa, token))).isEmpty();
        assertThat(correos(get("/api/v1/usuarios?estado=INACTIVO&q=" + inactiva, token))).containsExactly(inactiva);
        assertThat(correos(get("/api/v1/usuarios?rol=ALUMNO&q=" + activa, token))).isEmpty();
        assertThat(correos(get("/api/v1/usuarios?estado=BLOQUEADO&size=100", token))).contains(bloqueada);
    }

    private String estadoDe(String token, String correo) throws Exception {
        return get("/api/v1/usuarios?q=" + correo, token).cuerpo().get("contenido").get(0).get("estado").asString();
    }

    @Test
    void laListaPaginaYNoMuestraContrasenas() throws Exception {
        String token = administrador();
        for (int i = 0; i < 3; i++) {
            crearUsuario("ALUMNO", true);
        }

        Respuesta pagina = get("/api/v1/usuarios?rol=ALUMNO&size=2&page=0", token);

        assertThat(pagina.cuerpo().get("contenido")).hasSize(2);
        assertThat(pagina.cuerpo().get("totalElementos").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(pagina.cuerpo().toString()).doesNotContain("hash").doesNotContain("contrasena");
        assertThat(get("/api/v1/usuarios?size=1000", token).estado()).isEqualTo(400);
    }

    @Test
    void desbloquearLevantaElBloqueoPermiteEntrarYQuedaAuditado() throws Exception {
        String token = administrador();
        String correo = crearUsuario("DOCENTE", true);
        bloquear(correo);
        assertThat(iniciarSesion(correo, CONTRASENA).cuerpo().get("codigo").asString()).isEqualTo("CUENTA_BLOQUEADA");

        Respuesta r = enviar("PATCH", "/api/v1/usuarios/" + idDe(correo) + "/desbloqueo", null, token, null);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("ACTIVO");
        assertThat(iniciarSesion(correo, CONTRASENA).estado()).isEqualTo(200);
        Map<String, Object> registro = jdbc.queryForMap("SELECT * FROM auditoria.registro_auditoria WHERE accion = "
                + "'DESBLOQUEAR_CUENTA' AND id_entidad_afectada = ?", idDe(correo));
        assertThat(registro.get("modulo")).isEqualTo("SEGURIDAD");
        assertThat(registro.get("usuario_id")).as("lo hizo el administrador").isNotNull();
        assertThat(jdbc.queryForList("SELECT campo FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ?",
                String.class, registro.get("id"))).containsExactly("bloqueadoHasta");
    }

    @Test
    void unaCuentaQueNoEstaBloqueadaNoSeDesbloquea() throws Exception {
        String token = administrador();
        String correo = crearUsuario("DOCENTE", true);

        Respuesta r = enviar("PATCH", "/api/v1/usuarios/" + idDe(correo) + "/desbloqueo", null, token, null);

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("SOLICITUD_INVALIDA");
        assertThat(enviar("PATCH", "/api/v1/usuarios/no-existe/desbloqueo", null, token, null).estado()).isEqualTo(404);
    }

    @Test
    void soloElAdministradorConsultaYDesbloquea() throws Exception {
        String correo = crearUsuario("DOCENTE", true);
        bloquear(correo);
        assertThat(get("/api/v1/usuarios", null).estado()).isEqualTo(401);
        for (String rol : new String[] {"ALUMNO", "APODERADO", "DOCENTE"}) {
            String token = tokenDe(crearUsuario(rol, true));
            assertThat(get("/api/v1/usuarios", token).estado()).as(rol).isEqualTo(403);
            assertThat(enviar("PATCH", "/api/v1/usuarios/" + idDe(correo) + "/desbloqueo", null, token, null).estado())
                    .as(rol).isEqualTo(403);
        }
        assertThat(jdbc.queryForObject("SELECT bloqueado_hasta IS NOT NULL FROM seguridad.usuario WHERE correo = ?",
                Boolean.class, correo)).isTrue();
    }
}
