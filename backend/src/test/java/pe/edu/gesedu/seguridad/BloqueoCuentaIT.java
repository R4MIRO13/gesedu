package pe.edu.gesedu.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;

/** Bloqueo temporal tras 5 intentos fallidos (RNF-06, S-03) y reglas del inicio de sesión (RF-52). */
class BloqueoCuentaIT extends BaseIT {

    @Test
    void cincoFallosBloqueanLaCuentaYLaContrasenaCorrectaYaNoSirve() throws Exception {
        String correo = crearUsuario("DOCENTE", true);

        for (int i = 1; i <= 4; i++) {
            Respuesta r = iniciarSesion(correo, "incorrecta");
            assertThat(r.estado()).isEqualTo(401);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("CREDENCIALES_INVALIDAS");
        }
        Respuesta quinto = iniciarSesion(correo, "incorrecta");
        assertThat(quinto.estado()).isEqualTo(401);
        assertThat(quinto.cuerpo().get("codigo").asString()).isEqualTo("CUENTA_BLOQUEADA");
        assertThat(quinto.cuerpo().get("bloqueadoHasta").asString()).endsWith("-05:00");

        // Con la contraseña correcta, mientras dura el bloqueo, tampoco entra
        Respuesta correcta = iniciarSesion(correo, CONTRASENA);
        assertThat(correcta.estado()).isEqualTo(401);
        assertThat(correcta.cuerpo().get("codigo").asString()).isEqualTo("CUENTA_BLOQUEADA");

        Instant hasta = jdbc.queryForObject("SELECT bloqueado_hasta FROM seguridad.usuario WHERE correo = ?",
                java.sql.Timestamp.class, correo).toInstant();
        assertThat(Duration.between(Instant.now(), hasta)).isBetween(Duration.ofMinutes(14), Duration.ofMinutes(15));
    }

    @Test
    void elBloqueoSeAuditaUnaSolaVezComoSistema() throws Exception {
        String correo = crearUsuario("DOCENTE", true);
        String id = idDe(correo);

        for (int i = 0; i < 7; i++) {
            iniciarSesion(correo, "incorrecta");
        }

        List<Map<String, Object>> registros = jdbc.queryForList(
                "SELECT * FROM auditoria.registro_auditoria WHERE id_entidad_afectada = ?", id);
        assertThat(registros).hasSize(1);
        Map<String, Object> registro = registros.getFirst();
        assertThat(registro.get("accion")).isEqualTo("BLOQUEAR_CUENTA");
        assertThat(registro.get("modulo")).isEqualTo("SEGURIDAD");
        assertThat(registro.get("tipo_entidad")).isEqualTo("Usuario");
        assertThat(registro.get("usuario_id")).as("actor «Sistema»").isNull();
        Integer cambios = jdbc.queryForObject(
                "SELECT count(*) FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ? AND campo = 'bloqueado_hasta'",
                Integer.class, registro.get("id"));
        assertThat(cambios).isEqualTo(1);
    }

    @Test
    void unAccesoExitosoReiniciaElContador() throws Exception {
        String correo = crearUsuario("DOCENTE", true);

        for (int i = 0; i < 3; i++) {
            iniciarSesion(correo, "incorrecta");
        }
        assertThat(iniciarSesion(correo, CONTRASENA).estado()).isEqualTo(200);
        assertThat(intentos(correo)).isZero();

        // Si no se hubiera reiniciado, el cuarto fallo de esta tanda ya habría bloqueado la cuenta
        for (int i = 0; i < 4; i++) {
            Respuesta r = iniciarSesion(correo, "incorrecta");
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("CREDENCIALES_INVALIDAS");
        }
        assertThat(intentos(correo)).isEqualTo(4);
    }

    @Test
    void unBloqueoVencidoYaNoImpideEntrar() throws Exception {
        String correo = crearUsuario("DOCENTE", true);
        jdbc.update("UPDATE seguridad.usuario SET bloqueado_hasta = now() - interval '1 minute' WHERE correo = ?",
                correo);

        assertThat(iniciarSesion(correo, CONTRASENA).estado()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT bloqueado_hasta FROM seguridad.usuario WHERE correo = ?",
                java.sql.Timestamp.class, correo)).isNull();
    }

    @Test
    void elInicioDeSesionDevuelveTokenPermisosYCargo() throws Exception {
        String correo = crearUsuario("ADMINISTRADOR", true);
        jdbc.update("INSERT INTO seguridad.administrador (id, usuario_id, cargo) VALUES (?, ?, 'Dirección')",
                java.util.UUID.randomUUID().toString(), idDe(correo));

        Respuesta r = iniciarSesion(correo.toUpperCase(), CONTRASENA);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.tipoContenido()).startsWith("application/json");
        assertThat(r.cuerpo().get("tokenAcceso").asString()).isNotBlank();
        assertThat(r.cuerpo().get("expiraEn").asString()).endsWith("-05:00");
        assertThat(r.cuerpo().get("usuario").get("rol").asString()).isEqualTo("ADMINISTRADOR");
        assertThat(r.cuerpo().get("usuario").get("cargo").asString()).isEqualTo("Dirección");
        List<String> permisos = r.cuerpo().get("usuario").get("permisos").valueStream()
                .map(p -> p.asString()).toList();
        assertThat(permisos).contains("AUDITORIA_CONSULTAR", "HORARIO_CONFIGURAR").isSorted();
        assertThat(r.cuerpo().toString()).doesNotContain("hash");
    }

    @Test
    void unCorreoInexistenteRespondeIgualQueUnaContrasenaIncorrecta() throws Exception {
        Respuesta inexistente = iniciarSesion("nadie@test.gesedu.pe", "cualquiera");
        Respuesta incorrecta = iniciarSesion(crearUsuario("ALUMNO", true), "incorrecta");

        assertThat(inexistente.estado()).isEqualTo(401);
        assertThat(inexistente.tipoContenido()).startsWith("application/problem+json");
        assertThat(inexistente.cuerpo().get("codigo").asString()).isEqualTo("CREDENCIALES_INVALIDAS");
        assertThat(inexistente.cuerpo().get("detail")).isEqualTo(incorrecta.cuerpo().get("detail"));
    }

    @Test
    void laCuentaInactivaSoloSeRevelaConLaContrasenaCorrecta() throws Exception {
        String correo = crearUsuario("ALUMNO", false);

        assertThat(iniciarSesion(correo, "incorrecta").cuerpo().get("codigo").asString())
                .isEqualTo("CREDENCIALES_INVALIDAS");
        Respuesta r = iniciarSesion(correo, CONTRASENA);
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("CUENTA_INACTIVA");
    }

    @Test
    void sinCorreoNiContrasenaLaValidacionResponde400ConErrores() throws Exception {
        Respuesta r = post("/api/v1/auth/inicio-sesion", "{}", null);

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.tipoContenido()).startsWith("application/problem+json");
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
        assertThat(r.cuerpo().get("errores").size()).isEqualTo(2);
    }

    private int intentos(String correo) {
        return jdbc.queryForObject("SELECT intentos_fallidos FROM seguridad.usuario WHERE correo = ?",
                Integer.class, correo);
    }
}
