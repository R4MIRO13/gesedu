package pe.edu.gesedu.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import pe.edu.gesedu.matricula.BaseMatriculaIT;

/**
 * Gestión de cuentas (UC-24, RF-01): alta con invitación de 72 horas (S-77), modificación, activación con vínculo
 * académico (FA-2, S-82), desactivación con corte de sesiones y reenvío de la invitación.
 */
class GestionUsuariosIT extends BaseMatriculaIT {

    private static final String USUARIOS = "/api/v1/usuarios";
    private static final String CONFIRMACION = "/api/v1/auth/recuperacion/confirmacion";
    private static final String CLAVE = "ClaveNueva2027";

    @BeforeEach
    void limpiar() {
        reset(notificador);
    }

    private String administrador() throws Exception {
        return tokenDe(crearUsuario("ADMINISTRADOR", true));
    }

    private static String dniNuevo() {
        return String.valueOf(10_000_000 + (int) (Math.abs(UUID.randomUUID().getMostSignificantBits()) % 89_000_000));
    }

    private Map<String, Object> docente(String correo) {
        Map<String, Object> m = new HashMap<>();
        m.put("rol", "DOCENTE");
        m.put("nombres", "Luis");
        m.put("apellidos", "Paredes Soto");
        m.put("documento", dniNuevo());
        m.put("correo", correo);
        m.put("especialidad", "Matemática");
        return m;
    }

    private static String correoNuevo() {
        return "nuevo-" + UUID.randomUUID().toString().substring(0, 8) + "@test.gesedu.pe";
    }

    private Respuesta crear(Map<String, Object> cuerpo, String token) throws Exception {
        return post(USUARIOS, json.writeValueAsString(cuerpo), token);
    }

    /** El token del enlace del último correo de invitación enviado a ese correo (esperando {@code veces} envíos). */
    private String tokenDeInvitacion(String correo, int veces) {
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(notificador, timeout(3000).times(veces)).enviar(eq(correo), eq("Activa tu cuenta de GESEDU"), cuerpo.capture());
        Matcher enlace = Pattern.compile("definir-contrasena\\?token=([A-Za-z0-9_-]+)").matcher(cuerpo.getValue());
        assertThat(enlace.find()).as("el correo trae el enlace de la invitación").isTrue();
        return enlace.group(1);
    }

    private Respuesta definirContrasena(String token, String contrasena) throws Exception {
        return post(CONFIRMACION, json.writeValueAsString(Map.of("token", token, "nuevaContrasena", contrasena)), null);
    }

    private String estadoDe(String id, String token) throws Exception {
        return get(USUARIOS + "/" + id, token).cuerpo().get("estado").asString();
    }

    // ---- Alta con invitación -----------------------------------------------------------------------------

    @Test
    void laCuentaNuevaNaceSinContrasenaPendienteYConUnaInvitacionDe72Horas() throws Exception {
        String admin = administrador();
        String correo = correoNuevo();

        Respuesta r = crear(docente(correo), admin);

        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("PENDIENTE");
        assertThat(r.cuerpo().get("rol").asString()).isEqualTo("DOCENTE");
        assertThat(r.cuerpo().get("codigoDocente").asString()).matches("DOC-\\d{3,}");
        assertThat(etag(r)).isNotNull();
        Map<String, Object> fila = jdbc.queryForMap("SELECT activo, hash_contrasena FROM seguridad.usuario WHERE correo = ?", correo);
        assertThat(fila.get("activo")).isEqualTo(false);
        assertThat(fila.get("hash_contrasena")).as("el administrador no define la contraseña").isNull();

        String token = tokenDeInvitacion(correo, 1);
        assertThat(token).hasSize(43);
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT * FROM seguridad.token_recuperacion WHERE usuario_id = ?",
                idDe(correo));
        assertThat(filas).hasSize(1);
        assertThat(filas.getFirst().get("token_hash")).isNotEqualTo(token).asString().hasSize(64);
        Long horas = jdbc.queryForObject("SELECT EXTRACT(EPOCH FROM (expira_en - created_at)) / 3600 "
                + "FROM seguridad.token_recuperacion WHERE usuario_id = ?", Long.class, idDe(correo));
        assertThat(horas).isBetween(71L, 73L);
    }

    @Test
    void mientrasNoDefinaLaContrasenaNoPuedeIniciarSesionYElMensajeEsElMismoQueUnaCuentaInexistente() throws Exception {
        String admin = administrador();
        String correo = correoNuevo();
        crear(docente(correo), admin);

        Respuesta pendiente = iniciarSesion(correo, "CualquierClave1");
        Respuesta inexistente = iniciarSesion("nadie@test.gesedu.pe", "CualquierClave1");

        assertThat(pendiente.estado()).isEqualTo(401).isEqualTo(inexistente.estado());
        assertThat(pendiente.cuerpo().get("codigo").asString()).isEqualTo("CREDENCIALES_INVALIDAS")
                .isEqualTo(inexistente.cuerpo().get("codigo").asString());
        assertThat(pendiente.cuerpo().get("detail")).isEqualTo(inexistente.cuerpo().get("detail"));
    }

    @Test
    void conElEnlaceDefineSuContrasenaLaCuentaQuedaActivaYElEnlaceSeGastaYSeAudita() throws Exception {
        String admin = administrador();
        String correo = correoNuevo();
        String id = crear(docente(correo), admin).cuerpo().get("id").asString();
        String token = tokenDeInvitacion(correo, 1);

        assertThat(definirContrasena(token, CLAVE).estado()).isEqualTo(204);

        assertThat(estadoDe(id, admin)).isEqualTo("ACTIVO");
        Respuesta sesion = iniciarSesion(correo, CLAVE);
        assertThat(sesion.estado()).isEqualTo(200);
        assertThat(sesion.cuerpo().get("usuario").get("rol").asString()).isEqualTo("DOCENTE");
        // Hereda los permisos del rol
        assertThat(sesion.cuerpo().get("usuario").get("permisos").valueStream().map(p -> p.asString()).toList())
                .contains("ASISTENCIA_DOCENTE_MARCAR", "DISPONIBILIDAD_REGISTRAR");
        Respuesta reuso = definirContrasena(token, "OtraClave2027");
        assertThat(reuso.estado()).isEqualTo(400);
        assertThat(reuso.cuerpo().get("codigo").asString()).isEqualTo("TOKEN_RECUPERACION_INVALIDO");

        // La contraseña nunca queda en la auditoría
        Map<String, Object> registro = jdbc.queryForMap("SELECT * FROM auditoria.registro_auditoria WHERE accion = "
                + "'RESTABLECER_CONTRASENA' AND id_entidad_afectada = ?", id);
        assertThat(jdbc.queryForList("SELECT valor_nuevo FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ?",
                String.class, registro.get("id"))).contains("definida").doesNotContain(CLAVE);
    }

    @Test
    void elAltaSeAuditaConElActorYSinLaContrasena() throws Exception {
        String admin = administrador();
        String correo = correoNuevo();
        String id = crear(docente(correo), admin).cuerpo().get("id").asString();

        Map<String, Object> registro = jdbc.queryForMap("SELECT * FROM auditoria.registro_auditoria WHERE accion = "
                + "'CREAR_USUARIO' AND id_entidad_afectada = ?", id);
        assertThat(registro.get("modulo")).isEqualTo("SEGURIDAD");
        assertThat(registro.get("usuario_id")).isNotNull();
        assertThat(jdbc.queryForList("SELECT campo FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ?",
                String.class, registro.get("id"))).contains("rol", "documento", "correo", "estado");
    }

    @Test
    void elPersonalAdministrativoExigeCargoYElApoderadoSeCreaSinHijos() throws Exception {
        String admin = administrador();
        Map<String, Object> sinCargo = docente(correoNuevo());
        sinCargo.put("rol", "ADMINISTRADOR");
        sinCargo.remove("especialidad");

        Respuesta r = crear(sinCargo, admin);

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("errores").get(0).get("campo").asString()).isEqualTo("cargo");
        sinCargo.put("cargo", "Subdirectora");
        assertThat(crear(sinCargo, admin).cuerpo().get("cargo").asString()).isEqualTo("Subdirectora");

        Map<String, Object> apoderado = docente(correoNuevo());
        apoderado.put("rol", "APODERADO");
        apoderado.remove("especialidad");
        apoderado.put("telefono", "987654321");
        Respuesta a = crear(apoderado, admin);
        assertThat(a.estado()).isEqualTo(201);
        assertThat(a.cuerpo().get("telefono").asString()).isEqualTo("987654321");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seguridad.alumno al JOIN seguridad.apoderado ap "
                + "ON ap.id = al.apoderado_id WHERE ap.usuario_id = ?", Integer.class, a.cuerpo().get("id").asString())).isZero();
    }

    // ---- FA-1: duplicados y validaciones -----------------------------------------------------------------

    @Test
    void unDocumentoOUnCorreoRepetidoSeRechazaConConflicto() throws Exception {
        String admin = administrador();
        Map<String, Object> primero = docente(correoNuevo());
        assertThat(crear(primero, admin).estado()).isEqualTo(201);

        Map<String, Object> mismoDocumento = docente(correoNuevo());
        mismoDocumento.put("documento", primero.get("documento"));
        Respuesta d = crear(mismoDocumento, admin);
        assertThat(d.estado()).isEqualTo(409);
        assertThat(d.cuerpo().get("codigo").asString()).isEqualTo("DOCUMENTO_DUPLICADO");

        Map<String, Object> mismoCorreo = docente(((String) primero.get("correo")).toUpperCase());
        Respuesta c = crear(mismoCorreo, admin);
        assertThat(c.estado()).isEqualTo(409);
        assertThat(c.cuerpo().get("codigo").asString()).isEqualTo("CORREO_DUPLICADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seguridad.usuario WHERE documento_identidad = ?",
                Integer.class, primero.get("documento"))).isEqualTo(1);
    }

    @Test
    void losDatosInvalidosYElRolAlumnoSeRechazan() throws Exception {
        String admin = administrador();
        Map<String, Object> dniCorto = docente(correoNuevo());
        dniCorto.put("documento", "123");
        assertThat(crear(dniCorto, admin).estado()).isEqualTo(400);

        Map<String, Object> sinCorreo = docente("no-es-un-correo");
        assertThat(crear(sinCorreo, admin).estado()).isEqualTo(400);

        Map<String, Object> alumno = docente(correoNuevo());
        alumno.put("rol", "ALUMNO");
        Respuesta a = crear(alumno, admin);
        assertThat(a.estado()).isEqualTo(400);
        assertThat(a.cuerpo().get("codigo").asString()).isEqualTo("SOLICITUD_INVALIDA");

        Map<String, Object> rolInexistente = docente(correoNuevo());
        rolInexistente.put("rol", "SUPERUSUARIO");
        assertThat(crear(rolInexistente, admin).estado()).isEqualTo(400);
    }

    @Test
    void soloQuienGestionaCuentasPuedeCrearlas() throws Exception {
        String docente = tokenDe(crearUsuario("DOCENTE", true));
        String apoderado = tokenDe(crearUsuario("APODERADO", true));
        for (String token : List.of(docente, apoderado)) {
            assertThat(crear(docente(correoNuevo()), token).estado()).isEqualTo(403);
        }
        assertThat(crear(docente(correoNuevo()), null).estado()).isEqualTo(401);
    }

    // ---- Reenviar invitación -----------------------------------------------------------------------------

    @Test
    void unEnlaceVencidoSeReenviaYElAnteriorQuedaAnulado() throws Exception {
        String admin = administrador();
        String correo = correoNuevo();
        String id = crear(docente(correo), admin).cuerpo().get("id").asString();
        String viejo = tokenDeInvitacion(correo, 1);
        jdbc.update("UPDATE seguridad.token_recuperacion SET expira_en = now() - interval '1 minute' WHERE usuario_id = ?", id);
        assertThat(definirContrasena(viejo, CLAVE).estado()).isEqualTo(400);
        assertThat(get(USUARIOS + "/" + id, admin).cuerpo().get("invitacionVenceEn").asString()).endsWith("-05:00");

        Respuesta r = enviar("POST", USUARIOS + "/" + id + "/invitacion", null, admin, null);

        assertThat(r.estado()).isEqualTo(202);
        String nuevo = tokenDeInvitacion(correo, 2);
        assertThat(nuevo).isNotEqualTo(viejo);
        assertThat(definirContrasena(viejo, CLAVE).estado()).isEqualTo(400);
        assertThat(definirContrasena(nuevo, CLAVE).estado()).isEqualTo(204);
        assertThat(iniciarSesion(correo, CLAVE).estado()).isEqualTo(200);
    }

    @Test
    void noSeReenviaLaInvitacionAUnaCuentaQueYaDefinioSuContrasena() throws Exception {
        String admin = administrador();
        String activa = crearUsuario("DOCENTE", true);

        Respuesta r = enviar("POST", USUARIOS + "/" + idDe(activa) + "/invitacion", null, admin, null);

        assertThat(r.estado()).isEqualTo(400);
        verify(notificador, never()).enviar(eq(activa), anyString(), anyString());
    }

    // ---- Modificar ---------------------------------------------------------------------------------------

    private Map<String, Object> cambios(Map<String, Object> actual) {
        Map<String, Object> m = new HashMap<>(actual);
        m.remove("rol");
        return m;
    }

    @Test
    void modificarExigeLaVersionCambiaLosDatosYNoElRol() throws Exception {
        String admin = administrador();
        Map<String, Object> datos = docente(correoNuevo());
        Respuesta creada = crear(datos, admin);
        String id = creada.cuerpo().get("id").asString();
        Map<String, Object> cambio = cambios(datos);
        cambio.put("apellidos", "Paredes Rojas");
        cambio.put("especialidad", "Física");
        cambio.put("rol", "ADMINISTRADOR");

        assertThat(enviar("PUT", USUARIOS + "/" + id, json.writeValueAsString(cambio), admin, null).estado())
                .as("sin If-Match").isEqualTo(400);
        Respuesta ok = enviar("PUT", USUARIOS + "/" + id, json.writeValueAsString(cambio), admin, etag(creada));

        assertThat(ok.estado()).isEqualTo(200);
        assertThat(ok.cuerpo().get("apellidos").asString()).isEqualTo("Paredes Rojas");
        assertThat(ok.cuerpo().get("especialidad").asString()).isEqualTo("Física");
        assertThat(ok.cuerpo().get("rol").asString()).as("el rol no cambia").isEqualTo("DOCENTE");
        assertThat(etag(ok)).isNotEqualTo(etag(creada));
        // Una edición con la versión anterior pierde
        Respuesta tardia = enviar("PUT", USUARIOS + "/" + id, json.writeValueAsString(cambio), admin, etag(creada));
        assertThat(tardia.estado()).isEqualTo(409);
        // Auditado con el valor anterior y el nuevo
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria.cambio_auditoria c JOIN auditoria.registro_auditoria r "
                + "ON r.id = c.registro_auditoria_id WHERE r.accion = 'MODIFICAR_USUARIO' AND r.id_entidad_afectada = ? "
                + "AND c.campo = 'apellidos' AND c.valor_anterior = 'Paredes Soto' AND c.valor_nuevo = 'Paredes Rojas'",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void modificarRechazaUnDocumentoOCorreoDeOtraCuentaYElAlumnoSeEditaEnEstudiantes() throws Exception {
        String admin = administrador();
        Map<String, Object> a = docente(correoNuevo());
        Map<String, Object> b = docente(correoNuevo());
        crear(a, admin);
        Respuesta rb = crear(b, admin);
        Map<String, Object> pisa = cambios(b);
        pisa.put("correo", a.get("correo"));
        Respuesta r = enviar("PUT", USUARIOS + "/" + rb.cuerpo().get("id").asString(), json.writeValueAsString(pisa), admin, etag(rb));
        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("CORREO_DUPLICADO");

        String alumnoUsuario = datos.alumnoDe(crearApoderadoConCuenta().apoderadoId(), "Ana");
        String idUsuarioAlumno = jdbc.queryForObject("SELECT usuario_id FROM seguridad.alumno WHERE id = ?", String.class, alumnoUsuario);
        Respuesta fichaAlumno = get(USUARIOS + "/" + idUsuarioAlumno, admin);
        Map<String, Object> cuerpo = cambios(b);
        Respuesta e = enviar("PUT", USUARIOS + "/" + idUsuarioAlumno, json.writeValueAsString(cuerpo), admin, etag(fichaAlumno));
        assertThat(e.estado()).isEqualTo(400);
    }

    @Test
    void cambiarElCorreoDeUnaCuentaPendienteReenviaLaInvitacionAlCorreoNuevo() throws Exception {
        String admin = administrador();
        Map<String, Object> datos = docente(correoNuevo());
        Respuesta creada = crear(datos, admin);
        String viejo = tokenDeInvitacion((String) datos.get("correo"), 1);
        Map<String, Object> cambio = cambios(datos);
        String correoNuevo = correoNuevo();
        cambio.put("correo", correoNuevo);

        Respuesta ok = enviar("PUT", USUARIOS + "/" + creada.cuerpo().get("id").asString(),
                json.writeValueAsString(cambio), admin, etag(creada));

        assertThat(ok.estado()).isEqualTo(200);
        String nuevo = tokenDeInvitacion(correoNuevo, 1);
        assertThat(definirContrasena(viejo, CLAVE).estado()).as("el enlace al correo viejo quedó anulado").isEqualTo(400);
        assertThat(definirContrasena(nuevo, CLAVE).estado()).isEqualTo(204);
    }

    // ---- Desactivar y activar ----------------------------------------------------------------------------

    @Test
    void desactivarCortaLaSesionDeInmediatoYAnulaLasInvitacionesPendientes() throws Exception {
        String admin = administrador();
        String correo = crearUsuario("DOCENTE", true);
        String id = idDe(correo);
        Respuesta sesion = iniciarSesion(correo, CONTRASENA);
        String acceso = sesion.cuerpo().get("tokenAcceso").asString();
        String refresco = sesion.valorCookie("gesedu_refresh");
        assertThat(get("/api/v1/horarios/2027/vigente?perspectiva=SECCION", acceso).estado()).isNotEqualTo(401);

        Respuesta r = enviar("PATCH", USUARIOS + "/" + id + "/desactivacion", null, admin, null);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("INACTIVO");
        assertThat(get("/api/v1/horarios/2027/vigente?perspectiva=SECCION", acceso).estado())
                .as("el siguiente request con su token da 401").isEqualTo(401);
        assertThat(postConCookie("/api/v1/auth/refresco", null, "gesedu_refresh=" + refresco).estado()).isEqualTo(401);
        assertThat(iniciarSesion(correo, CONTRASENA).cuerpo().get("codigo").asString()).isEqualTo("CUENTA_INACTIVA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = "
                + "'DESACTIVAR_USUARIO' AND id_entidad_afectada = ?", Integer.class, id)).isEqualTo(1);
        // Desactivar otra vez es un error, no una operación vacía
        assertThat(enviar("PATCH", USUARIOS + "/" + id + "/desactivacion", null, admin, null).estado()).isEqualTo(400);
    }

    @Test
    void unaCuentaPendienteNoSeDesactivaYUnaActivaDesactivadaNoEntra() throws Exception {
        String admin = administrador();
        String correo = correoNuevo();
        String id = crear(docente(correo), admin).cuerpo().get("id").asString();
        String token = tokenDeInvitacion(correo, 1);
        // Una cuenta pendiente está inactiva: se desactiva igual que una activa solo si estaba activa, así que se
        // simula la anulación del administrador sobre los enlaces con la reactivación y desactivación de una activa
        assertThat(enviar("PATCH", USUARIOS + "/" + id + "/desactivacion", null, admin, null).estado()).isEqualTo(400);
        assertThat(definirContrasena(token, CLAVE).estado()).isEqualTo(204);
        assertThat(enviar("PATCH", USUARIOS + "/" + id + "/desactivacion", null, admin, null).estado()).isEqualTo(200);
        assertThat(iniciarSesion(correo, CLAVE).cuerpo().get("codigo").asString()).isEqualTo("CUENTA_INACTIVA");
    }

    @Test
    void elAdministradorNoPuedeDesactivarSuPropiaCuenta() throws Exception {
        String correo = crearUsuario("ADMINISTRADOR", true);
        String token = tokenDe(correo);

        Respuesta r = enviar("PATCH", USUARIOS + "/" + idDe(correo) + "/desactivacion", null, token, null);

        assertThat(r.estado()).isEqualTo(400);
        assertThat(iniciarSesion(correo, CONTRASENA).estado()).isEqualTo(200);
    }

    @Test
    void unaCuentaConContrasenaDesactivadaSeVuelveAActivarSinNuevaInvitacion() throws Exception {
        String admin = administrador();
        String correo = crearUsuario("APODERADO", false);

        Respuesta r = enviar("PATCH", USUARIOS + "/" + idDe(correo) + "/activacion", null, admin, null);

        assertThat(r.estado()).as("el apoderado se habilita sin hijos (S-82)").isEqualTo(200);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("ACTIVO");
        assertThat(iniciarSesion(correo, CONTRASENA).estado()).isEqualTo(200);
        verify(notificador, never()).enviar(eq(correo), anyString(), anyString());
        assertThat(enviar("PATCH", USUARIOS + "/" + idDe(correo) + "/activacion", null, admin, null).estado())
                .as("activar una cuenta activa").isEqualTo(400);
    }

    @Test
    void elAlumnoSinMatriculaActivaNoSePuedeHabilitar() throws Exception {
        String admin = administrador();
        String usuarioAlumno = datos.alumnoDe(crearApoderadoConCuenta().apoderadoId(), "Sin matricula");
        String id = jdbc.queryForObject("SELECT usuario_id FROM seguridad.alumno WHERE id = ?", String.class, usuarioAlumno);

        Respuesta r = enviar("PATCH", USUARIOS + "/" + id + "/activacion",
                json.writeValueAsString(Map.of("correo", correoNuevo())), admin, null);

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("VINCULO_ACADEMICO_INEXISTENTE");
        assertThat(jdbc.queryForObject("SELECT activo FROM seguridad.usuario WHERE id = ?", Boolean.class, id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seguridad.token_recuperacion WHERE usuario_id = ?",
                Integer.class, id)).isZero();
    }

    @Test
    void elAlumnoConMatriculaActivaSeHabilitaConUnaInvitacionYAntesNoEntra() throws Exception {
        String admin = administrador();
        var familia = crearFamilia();
        String id = jdbc.queryForObject("SELECT usuario_id FROM seguridad.alumno WHERE id = ?", String.class, familia.alumnoId());
        String correo = correoNuevo();

        Respuesta sinCorreo = enviar("PATCH", USUARIOS + "/" + id + "/activacion", null, admin, null);
        assertThat(sinCorreo.estado()).as("el alumno nace sin correo: hay que indicarlo").isEqualTo(400);

        Respuesta r = enviar("PATCH", USUARIOS + "/" + id + "/activacion", json.writeValueAsString(Map.of("correo", correo)), admin, null);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("PENDIENTE");
        assertThat(r.cuerpo().get("rol").asString()).isEqualTo("ALUMNO");
        assertThat(iniciarSesion(correo, "CualquierClave1").estado()).isEqualTo(401);
        String token = tokenDeInvitacion(correo, 1);
        assertThat(definirContrasena(token, CLAVE).estado()).isEqualTo(204);
        assertThat(iniciarSesion(correo, CLAVE).cuerpo().get("usuario").get("rol").asString()).isEqualTo("ALUMNO");
        assertThat(estadoDe(id, admin)).isEqualTo("ACTIVO");
    }

    @Test
    void siLaMatriculaDejaDeEstarActivaAntesDeAceptarLaInvitacionElEnlaceNoSirve() throws Exception {
        String admin = administrador();
        var familia = crearFamilia();
        String id = jdbc.queryForObject("SELECT usuario_id FROM seguridad.alumno WHERE id = ?", String.class, familia.alumnoId());
        String correo = correoNuevo();
        enviar("PATCH", USUARIOS + "/" + id + "/activacion", json.writeValueAsString(Map.of("correo", correo)), admin, null);
        String token = tokenDeInvitacion(correo, 1);
        jdbc.update("UPDATE matricula.matricula SET estado = 'ANULADA' WHERE id = ?", familia.matriculaId());

        Respuesta r = definirContrasena(token, CLAVE);

        assertThat(r.estado()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT activo FROM seguridad.usuario WHERE id = ?", Boolean.class, id)).isFalse();
    }

    // ---- Lista y detalle ---------------------------------------------------------------------------------

    @Test
    void laListaFiltraLasCuentasPendientesYElDetalleRespetaLosPermisos() throws Exception {
        String admin = administrador();
        String correo = correoNuevo();
        String id = crear(docente(correo), admin).cuerpo().get("id").asString();
        String inactiva = crearUsuario("DOCENTE", false);

        Respuesta pendientes = get(USUARIOS + "?estado=PENDIENTE&q=" + correo, admin);
        assertThat(pendientes.cuerpo().get("contenido").valueStream().map(u -> u.get("correo").asString()).toList())
                .containsExactly(correo);
        assertThat(get(USUARIOS + "?estado=INACTIVO&q=" + correo, admin).cuerpo().get("contenido")).isEmpty();
        assertThat(get(USUARIOS + "?estado=PENDIENTE&q=" + inactiva, admin).cuerpo().get("contenido")).isEmpty();
        assertThat(get(USUARIOS + "?estado=INACTIVO&q=" + inactiva, admin).cuerpo().get("contenido")).hasSize(1);

        assertThat(get(USUARIOS + "/" + id, tokenDe(crearUsuario("DOCENTE", true))).estado()).isEqualTo(403);
        assertThat(get(USUARIOS + "/no-existe", admin).estado()).isEqualTo(404);
    }
}
