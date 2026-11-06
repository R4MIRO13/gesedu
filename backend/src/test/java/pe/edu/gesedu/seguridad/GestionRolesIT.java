package pe.edu.gesedu.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;

/**
 * Roles y permisos (RF-48, RNF-07): el catálogo, el reemplazo de permisos de un rol y, sobre todo, que el cambio se
 * refleje en el siguiente request del usuario aunque su token siga vigente (caché de cuentas). Cada prueba deja los
 * permisos de los roles como estaban para no afectar a las demás clases.
 */
class GestionRolesIT extends BaseIT {

    private static final String RUTA_DOCENTE_PROTEGIDA = "/api/v1/anios-escolares"; // ACADEMICO_CONSULTAR

    private List<Map<String, Object>> permisosOriginales;

    @BeforeEach
    void guardar() {
        permisosOriginales = jdbc.queryForList("SELECT rol_acceso_id, permiso_id FROM seguridad.rol_permiso");
    }

    @AfterEach
    void restaurar() {
        jdbc.update("DELETE FROM seguridad.rol_permiso");
        for (Map<String, Object> fila : permisosOriginales) {
            jdbc.update("INSERT INTO seguridad.rol_permiso (rol_acceso_id, permiso_id) VALUES (?, ?)",
                    fila.get("rol_acceso_id"), fila.get("permiso_id"));
        }
        invalidarCuentas();
    }

    private String admin() throws Exception {
        return tokenDe(crearUsuario("ADMINISTRADOR", true));
    }

    private String idRol(String codigo) {
        return jdbc.queryForObject("SELECT id FROM seguridad.rol_acceso WHERE codigo = ?", String.class, codigo);
    }

    private List<String> permisosDe(String rol) {
        return jdbc.queryForList("SELECT p.codigo FROM seguridad.rol_permiso rp JOIN seguridad.permiso p ON p.id = rp.permiso_id "
                + "JOIN seguridad.rol_acceso r ON r.id = rp.rol_acceso_id WHERE r.codigo = ? ORDER BY 1", String.class, rol);
    }

    private Respuesta reemplazar(String rol, List<String> permisos, String token) throws Exception {
        return enviar("PUT", "/api/v1/roles/" + idRol(rol) + "/permisos", json.writeValueAsString(Map.of("permisos", permisos)), token, null);
    }

    @Test
    void elCatalogoLista_losRolesConSusPermisosYTodosLosPermisosDisponibles() throws Exception {
        Respuesta r = get("/api/v1/roles", admin());

        assertThat(r.estado()).isEqualTo(200);
        List<String> codigos = r.cuerpo().get("roles").valueStream().map(x -> x.get("codigo").asString()).toList();
        assertThat(codigos).containsExactlyInAnyOrder("ADMINISTRADOR", "DOCENTE", "ALUMNO", "APODERADO");
        var docente = r.cuerpo().get("roles").valueStream().filter(x -> x.get("codigo").asString().equals("DOCENTE")).findFirst().orElseThrow();
        assertThat(docente.get("permisos").valueStream().map(p -> p.asString()).toList()).contains("NOTA_REGISTRAR", "ACADEMICO_CONSULTAR");
        assertThat(r.cuerpo().get("permisos").size()).isGreaterThan(25);
        assertThat(r.cuerpo().get("permisos").get(0).get("modulo").asString()).isNotBlank();
    }

    @Test
    void soloQuienGestionaRolesPuedeVerlosOCambiarlos() throws Exception {
        for (String rol : List.of("DOCENTE", "ALUMNO", "APODERADO")) {
            String token = tokenDe(crearUsuario(rol, true));
            assertThat(get("/api/v1/roles", token).estado()).as(rol).isEqualTo(403);
            assertThat(reemplazar("DOCENTE", List.of("NOTA_REGISTRAR"), token).estado()).as(rol).isEqualTo(403);
        }
        assertThat(get("/api/v1/roles", null).estado()).isEqualTo(401);
    }

    @Test
    void quitarUnPermisoSeReflejaEnElSiguienteRequestAunqueElTokenSigaVigente() throws Exception {
        String admin = admin();
        String docente = tokenDe(crearUsuario("DOCENTE", true));
        assertThat(get(RUTA_DOCENTE_PROTEGIDA, docente).estado()).isEqualTo(200);

        List<String> sinConsulta = new ArrayList<>(permisosDe("DOCENTE"));
        sinConsulta.remove("ACADEMICO_CONSULTAR");
        Respuesta r = reemplazar("DOCENTE", sinConsulta, admin);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("permisos").valueStream().map(p -> p.asString()).toList()).doesNotContain("ACADEMICO_CONSULTAR");
        assertThat(get(RUTA_DOCENTE_PROTEGIDA, docente).estado()).as("mismo token, siguiente request").isEqualTo(403);

        // Y al devolver el permiso, vuelve a entrar sin iniciar sesión de nuevo
        sinConsulta.add("ACADEMICO_CONSULTAR");
        assertThat(reemplazar("DOCENTE", sinConsulta, admin).estado()).isEqualTo(200);
        assertThat(get(RUTA_DOCENTE_PROTEGIDA, docente).estado()).isEqualTo(200);
    }

    @Test
    void agregarUnPermisoDaAccesoDeInmediatoYElMenuDeLaSesionLoTrae() throws Exception {
        String admin = admin();
        String correo = crearUsuario("ALUMNO", true);
        String alumno = tokenDe(correo);
        assertThat(get(RUTA_DOCENTE_PROTEGIDA, alumno).estado()).isEqualTo(403);

        List<String> con = new ArrayList<>(permisosDe("ALUMNO"));
        con.add("ACADEMICO_CONSULTAR");
        assertThat(reemplazar("ALUMNO", con, admin).estado()).isEqualTo(200);

        assertThat(get(RUTA_DOCENTE_PROTEGIDA, alumno).estado()).isEqualTo(200);
        assertThat(iniciarSesion(correo, CONTRASENA).cuerpo().get("usuario").get("permisos").valueStream().map(p -> p.asString()).toList())
                .contains("ACADEMICO_CONSULTAR");
    }

    @Test
    void elCambioQuedaAuditadoConLosPermisosAgregadosYQuitados() throws Exception {
        String admin = admin();
        List<String> nuevos = new ArrayList<>(permisosDe("APODERADO"));
        nuevos.remove("HORARIO_CONSULTAR");
        nuevos.add("ACADEMICO_CONSULTAR");

        assertThat(reemplazar("APODERADO", nuevos, admin).estado()).isEqualTo(200);

        Map<String, Object> registro = jdbc.queryForMap("SELECT * FROM auditoria.registro_auditoria WHERE accion = "
                + "'MODIFICAR_PERMISOS_ROL' AND id_entidad_afectada = ?", idRol("APODERADO"));
        assertThat(registro.get("usuario_id")).isNotNull();
        assertThat(jdbc.queryForList("SELECT campo || ':' || valor_anterior || '>' || valor_nuevo FROM auditoria.cambio_auditoria "
                + "WHERE registro_auditoria_id = ?", String.class, registro.get("id")))
                .containsExactlyInAnyOrder("permiso ACADEMICO_CONSULTAR:no>sí", "permiso HORARIO_CONSULTAR:sí>no");
    }

    @Test
    void unPermisoQueNoExisteSeRechazaYNoCambiaNada() throws Exception {
        List<String> antes = permisosDe("DOCENTE");

        Respuesta r = reemplazar("DOCENTE", List.of("NOTA_REGISTRAR", "PERMISO_INVENTADO"), admin());

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
        assertThat(r.cuerpo().get("detail").asString()).contains("PERMISO_INVENTADO");
        assertThat(permisosDe("DOCENTE")).isEqualTo(antes);
        assertThat(reemplazar("DOCENTE", List.of(), admin()).estado()).as("un rol puede quedarse sin permisos").isEqualTo(200);
        assertThat(permisosDe("DOCENTE")).isEmpty();
    }

    @Test
    void elAdministradorNoPuedePerderLosPermisosParaGestionarCuentasYRoles() throws Exception {
        String admin = admin();
        List<String> antes = permisosDe("ADMINISTRADOR");
        for (String esencial : List.of("ROL_GESTIONAR", "USUARIO_GESTIONAR", "USUARIO_CONSULTAR")) {
            List<String> sin = new ArrayList<>(antes);
            sin.remove(esencial);
            Respuesta r = reemplazar("ADMINISTRADOR", sin, admin);
            assertThat(r.estado()).as(esencial).isEqualTo(400);
            assertThat(r.cuerpo().get("detail").asString()).contains(esencial);
        }
        assertThat(permisosDe("ADMINISTRADOR")).isEqualTo(antes);
        assertThat(get("/api/v1/roles", admin).estado()).isEqualTo(200);
    }

    @Test
    void unRolInexistenteDa404() throws Exception {
        Respuesta r = enviar("PUT", "/api/v1/roles/no-existe/permisos", json.writeValueAsString(Map.of("permisos", List.of())), admin(), null);
        assertThat(r.estado()).isEqualTo(404);
    }

    @Test
    void cambiosSimultaneosDelMismoRolSeAplicanUnoTrasOtroSinMezclarse() throws Exception {
        String admin = admin();
        List<String> a = List.of("NOTA_REGISTRAR");
        List<String> b = List.of("NOTA_REGISTRAR", "REGLA_DEFINIR", "HORARIO_CONSULTAR");
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            List<String> pedido = i % 2 == 0 ? a : b;
            resultados.add(pool.submit(() -> {
                salida.await();
                return reemplazar("DOCENTE", pedido, admin).estado();
            }));
        }
        salida.countDown();
        for (Future<Integer> f : resultados) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();

        assertThat(permisosDe("DOCENTE")).as("queda exactamente uno de los dos conjuntos").isIn(
                List.of("NOTA_REGISTRAR"), List.of("HORARIO_CONSULTAR", "NOTA_REGISTRAR", "REGLA_DEFINIR"));
    }
}
