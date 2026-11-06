package pe.edu.gesedu.academico;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;

/**
 * C-47, extensión de UC-13: activar un año PLANIFICADO cierra el ACTIVO anterior en una sola transacción. Como el año
 * activo es único en toda la base, cada prueba deja los estados como estaban para no afectar a las demás clases.
 */
class ActivacionAnioIT extends BaseIT {

    private static final AtomicInteger SIGUIENTE = new AtomicInteger(2550);
    private List<Map<String, Object>> estadosOriginales;

    @BeforeEach
    void guardarEstados() {
        estadosOriginales = jdbc.queryForList("SELECT id, estado FROM academico.anio_escolar");
    }

    @AfterEach
    void restaurarEstados() {
        // Primero se cierran o planifican los que no eran ACTIVO, para respetar el índice del único activo
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'CERRADO' WHERE estado = 'ACTIVO'");
        for (Map<String, Object> fila : estadosOriginales) {
            jdbc.update("UPDATE academico.anio_escolar SET estado = ? WHERE id = ?", fila.get("estado"), fila.get("id"));
        }
    }

    private String admin() throws Exception {
        return tokenDe(crearUsuario("ADMINISTRADOR", true));
    }

    /** Crea un año PLANIFICADO por la API y devuelve su número. */
    private int crearAnio(String token) throws Exception {
        int anio = SIGUIENTE.getAndIncrement();
        String bimestres = ("[{\"numero\":1,\"fechaInicio\":\"%1$d-02-15\",\"fechaFin\":\"%1$d-04-30\"},"
                + "{\"numero\":2,\"fechaInicio\":\"%1$d-05-03\",\"fechaFin\":\"%1$d-07-09\"},"
                + "{\"numero\":3,\"fechaInicio\":\"%1$d-07-26\",\"fechaFin\":\"%1$d-10-01\"},"
                + "{\"numero\":4,\"fechaInicio\":\"%1$d-10-04\",\"fechaFin\":\"%1$d-12-20\"}]").formatted(anio);
        Respuesta r = post("/api/v1/anios-escolares", "{\"fechaInicio\":\"%1$d-02-15\",\"fechaFin\":\"%1$d-12-20\",\"bimestres\":%2$s}"
                .formatted(anio, bimestres), token);
        assertThat(r.estado()).isEqualTo(201);
        return anio;
    }

    private Respuesta activar(int anio, String token) throws Exception {
        return enviar("PATCH", "/api/v1/anios-escolares/" + anio + "/activacion", null, token, null);
    }

    private String estado(int anio) {
        return jdbc.queryForObject("SELECT estado FROM academico.anio_escolar WHERE anio = ?", String.class, anio);
    }

    private long activos() {
        return jdbc.queryForObject("SELECT count(*) FROM academico.anio_escolar WHERE estado = 'ACTIVO'", Long.class);
    }

    @Test
    void activarUnAnioPlanificadoCierraElActivoAnteriorYQuedaAuditado() throws Exception {
        String admin = admin();
        int primero = crearAnio(admin);
        int segundo = crearAnio(admin);

        Respuesta r1 = activar(primero, admin);
        assertThat(r1.estado()).isEqualTo(200);
        assertThat(r1.cuerpo().get("estado").asString()).isEqualTo("ACTIVO");
        assertThat(estado(primero)).isEqualTo("ACTIVO");

        Respuesta r2 = activar(segundo, admin);

        assertThat(r2.estado()).isEqualTo(200);
        assertThat(r2.cuerpo().get("anio").asInt()).isEqualTo(segundo);
        assertThat(estado(segundo)).isEqualTo("ACTIVO");
        assertThat(estado(primero)).as("el activo anterior se cierra").isEqualTo("CERRADO");
        assertThat(activos()).isEqualTo(1);

        String id = r2.cuerpo().get("id").asString();
        Map<String, Object> registro = jdbc.queryForMap("SELECT * FROM auditoria.registro_auditoria WHERE accion = "
                + "'ACTIVAR_ANIO_ESCOLAR' AND id_entidad_afectada = ?", id);
        assertThat(registro.get("modulo")).isEqualTo("ACADEMICO");
        assertThat(registro.get("usuario_id")).isNotNull();
        assertThat(jdbc.queryForList("SELECT campo FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ?",
                String.class, registro.get("id"))).contains("anio " + primero, "anio " + segundo);
    }

    @Test
    void laSpaObtieneElAnioActivoDesdeLaApiConCualquierRol() throws Exception {
        String admin = admin();
        int anio = crearAnio(admin);
        activar(anio, admin);

        for (String rol : List.of("ADMINISTRADOR", "DOCENTE", "ALUMNO", "APODERADO")) {
            Respuesta r = get("/api/v1/anios-escolares/activo", tokenDe(crearUsuario(rol, true)));
            assertThat(r.estado()).as(rol).isEqualTo(200);
            assertThat(r.cuerpo().get("anio").asInt()).isEqualTo(anio);
        }
        assertThat(get("/api/v1/anios-escolares/activo", null).estado()).isEqualTo(401);

        jdbc.update("UPDATE academico.anio_escolar SET estado = 'CERRADO' WHERE estado = 'ACTIVO'");
        Respuesta sin = get("/api/v1/anios-escolares/activo", admin);
        assertThat(sin.estado()).isEqualTo(404);
    }

    @Test
    void unAnioCerradoNoSeReactivaYUnoActivoNoSeActivaDeNuevo() throws Exception {
        String admin = admin();
        int primero = crearAnio(admin);
        int segundo = crearAnio(admin);
        activar(primero, admin);
        activar(segundo, admin);

        Respuesta cerrado = activar(primero, admin);
        assertThat(cerrado.estado()).isEqualTo(422);
        assertThat(cerrado.cuerpo().get("codigo").asString()).isEqualTo("ANIO_CERRADO");
        Respuesta yaActivo = activar(segundo, admin);
        assertThat(yaActivo.estado()).isEqualTo(400);
        assertThat(activar(1999, admin).estado()).isEqualTo(404);
        assertThat(estado(segundo)).isEqualTo("ACTIVO");
        assertThat(estado(primero)).isEqualTo("CERRADO");
    }

    @Test
    void soloQuienConfiguraLoAcademicoPuedeActivar() throws Exception {
        String admin = admin();
        int anio = crearAnio(admin);

        for (String rol : List.of("DOCENTE", "ALUMNO", "APODERADO")) {
            assertThat(activar(anio, tokenDe(crearUsuario(rol, true))).estado()).as(rol).isEqualTo(403);
        }
        assertThat(activar(anio, null).estado()).isEqualTo(401);
        assertThat(estado(anio)).isEqualTo("PLANIFICADO");
    }

    @Test
    void dosActivacionesSimultaneasNuncaDejanDosAniosActivos() throws Exception {
        String admin = admin();
        int a = crearAnio(admin);
        int b = crearAnio(admin);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int anio : List.of(a, b)) {
            resultados.add(pool.submit(() -> {
                salida.await();
                return activar(anio, admin).estado();
            }));
        }
        salida.countDown();
        List<Integer> estados = new ArrayList<>();
        for (Future<Integer> f : resultados) {
            estados.add(f.get());
        }
        pool.shutdown();

        assertThat(estados).as("al menos una activa y la otra, o también activa o en conflicto")
                .containsAnyOf(200).allMatch(e -> e == 200 || e == 409);
        assertThat(activos()).isEqualTo(1);
    }
}
