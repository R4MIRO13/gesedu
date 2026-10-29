package pe.edu.gesedu.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.transversal.notificaciones.Notificador;

/** Recuperación de contraseña (UC-24 FA-5, RF-52, S-01): enlace de un solo uso de 30 minutos. */
class RecuperacionContrasenaIT extends BaseIT {

    private static final String SOLICITUD = "/api/v1/auth/recuperacion";
    private static final String CONFIRMACION = "/api/v1/auth/recuperacion/confirmacion";
    private static final String NUEVA = "NuevaClave2027";

    @MockitoBean
    private Notificador notificador;

    @BeforeEach
    void limpiar() {
        reset(notificador);
    }

    private Respuesta solicitar(String correo) throws Exception {
        return post(SOLICITUD, json.writeValueAsString(Map.of("correo", correo)), null);
    }

    private Respuesta confirmar(String token, String contrasena) throws Exception {
        return post(CONFIRMACION, json.writeValueAsString(Map.of("token", token, "nuevaContrasena", contrasena)), null);
    }

    /** Pide el enlace y devuelve el token que viaja en el correo. */
    private String pedirEnlace(String correo) throws Exception {
        assertThat(solicitar(correo).estado()).isEqualTo(202);
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(notificador, timeout(3000)).enviar(eq(correo), anyString(), cuerpo.capture());
        Matcher enlace = Pattern.compile("restablecer-contrasena\\?token=([A-Za-z0-9_-]+)").matcher(cuerpo.getValue());
        assertThat(enlace.find()).as("el correo trae el enlace").isTrue();
        return enlace.group(1);
    }

    @Test
    void laRespuestaEsLaMismaExistaONoLaCuentaYSoloLaExistenteRecibeElCorreo() throws Exception {
        String correo = crearUsuario("APODERADO", true);

        Respuesta existente = solicitar(correo);
        Respuesta inexistente = solicitar("nadie@test.gesedu.pe");

        assertThat(existente.estado()).isEqualTo(202).isEqualTo(inexistente.estado());
        assertThat(existente.cuerpo()).isEqualTo(inexistente.cuerpo());
        verify(notificador, timeout(3000)).enviar(eq(correo), eq("Restablece tu contraseña de GESEDU"), anyString());
        verify(notificador, never()).enviar(eq("nadie@test.gesedu.pe"), anyString(), anyString());
    }

    @Test
    void elCorreoLlevaUnEnlaceConElTokenYSoloSeGuardaSuHash() throws Exception {
        String correo = crearUsuario("DOCENTE", true);
        String token = pedirEnlace(correo);

        assertThat(token).hasSize(43);
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT * FROM seguridad.token_recuperacion WHERE usuario_id = ?",
                idDe(correo));
        assertThat(filas).hasSize(1);
        assertThat(filas.getFirst().get("token_hash")).isNotEqualTo(token).asString().hasSize(64);
        assertThat(filas.getFirst().get("usado_en")).isNull();
        // Vence a los 30 minutos
        Long minutos = jdbc.queryForObject("SELECT EXTRACT(EPOCH FROM (expira_en - created_at)) / 60 FROM "
                + "seguridad.token_recuperacion WHERE usuario_id = ?", Long.class, idDe(correo));
        assertThat(minutos).isBetween(29L, 31L);
    }

    @Test
    void elEnlaceFuncionaUnaSolaVezCambiaLaContrasenaYSeAudita() throws Exception {
        String correo = crearUsuario("ALUMNO", true);
        String token = pedirEnlace(correo);

        Respuesta r = confirmar(token, NUEVA);

        assertThat(r.estado()).isEqualTo(204);
        assertThat(iniciarSesion(correo, NUEVA).estado()).isEqualTo(200);
        assertThat(iniciarSesion(correo, CONTRASENA).estado()).as("la anterior ya no sirve").isEqualTo(401);
        // Reusar el enlace: rechazado, y se puede pedir otro
        Respuesta reuso = confirmar(token, "OtraClave2027");
        assertThat(reuso.estado()).isEqualTo(400);
        assertThat(reuso.cuerpo().get("codigo").asString()).isEqualTo("TOKEN_RECUPERACION_INVALIDO");
        assertThat(iniciarSesion(correo, NUEVA).estado()).isEqualTo(200);

        Map<String, Object> registro = jdbc.queryForMap("SELECT * FROM auditoria.registro_auditoria WHERE accion = "
                + "'RESTABLECER_CONTRASENA' AND id_entidad_afectada = ?", idDe(correo));
        assertThat(registro.get("modulo")).isEqualTo("SEGURIDAD");
        assertThat(registro.get("usuario_id")).as("la persona aún no inició sesión: actor «Sistema»").isNull();
        // La contraseña nunca queda en la auditoría
        assertThat(jdbc.queryForList("SELECT valor_nuevo FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ?",
                String.class, registro.get("id"))).containsExactly("restablecida");
    }

    @Test
    void alDefinirLaContrasenaSeRevocanLasSesionesAbiertas() throws Exception {
        String correo = crearUsuario("DOCENTE", true);
        String refresco = iniciarSesion(correo, CONTRASENA).valorCookie("gesedu_refresh");
        String token = pedirEnlace(correo);

        assertThat(confirmar(token, NUEVA).estado()).isEqualTo(204);

        assertThat(postConCookie("/api/v1/auth/refresco", null, "gesedu_refresh=" + refresco).estado())
                .as("la sesión que estaba abierta quedó revocada").isEqualTo(401);
    }

    @Test
    void pedirOtroEnlaceAnulaElAnterior() throws Exception {
        String correo = crearUsuario("APODERADO", true);
        String primero = pedirEnlace(correo);
        reset(notificador);
        String segundo = pedirEnlace(correo);

        assertThat(segundo).isNotEqualTo(primero);
        assertThat(confirmar(primero, NUEVA).estado()).isEqualTo(400);
        assertThat(confirmar(segundo, NUEVA).estado()).isEqualTo(204);
    }

    @Test
    void unEnlaceVencidoSeRechazaYSePuedePedirOtro() throws Exception {
        String correo = crearUsuario("APODERADO", true);
        String token = pedirEnlace(correo);
        jdbc.update("UPDATE seguridad.token_recuperacion SET expira_en = now() - interval '1 minute' WHERE usuario_id = ?",
                idDe(correo));

        Respuesta r = confirmar(token, NUEVA);

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("TOKEN_RECUPERACION_INVALIDO");
        reset(notificador);
        assertThat(confirmar(pedirEnlace(correo), NUEVA).estado()).isEqualTo(204);
    }

    @Test
    void unTokenInventadoOUnaContrasenaDebilSeRechazan() throws Exception {
        assertThat(confirmar("inventado", NUEVA).cuerpo().get("codigo").asString()).isEqualTo("TOKEN_RECUPERACION_INVALIDO");

        String correo = crearUsuario("ALUMNO", true);
        String token = pedirEnlace(correo);
        for (String debil : List.of("corta1", "sololetrasaqui", "123456789012")) {
            Respuesta r = confirmar(token, debil);
            assertThat(r.estado()).as(debil).isEqualTo(400);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
        }
        // El enlace sigue sirviendo: una contraseña débil no lo gasta
        assertThat(confirmar(token, NUEVA).estado()).isEqualTo(204);
    }

    @Test
    void unaCuentaInactivaNoRecibeElEnlace() throws Exception {
        String correo = crearUsuario("APODERADO", false);

        assertThat(solicitar(correo).estado()).isEqualTo(202);

        Thread.sleep(300);
        verify(notificador, never()).enviar(eq(correo), anyString(), anyString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seguridad.token_recuperacion WHERE usuario_id = ?",
                Integer.class, idDe(correo))).isZero();
    }

    @Test
    void laContrasenaNuevaNoDesbloqueaUnaCuentaBloqueada() throws Exception {
        String correo = crearUsuario("DOCENTE", true);
        for (int i = 0; i < 5; i++) {
            iniciarSesion(correo, "incorrecta");
        }
        String token = pedirEnlace(correo);

        assertThat(confirmar(token, NUEVA).estado()).isEqualTo(204);

        Respuesta r = iniciarSesion(correo, NUEVA);
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("CUENTA_BLOQUEADA");
    }
}
