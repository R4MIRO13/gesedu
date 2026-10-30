package pe.edu.gesedu.academico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import pe.edu.gesedu.apoyo.BaseIT;

/** UC-13: creación del año escolar y sus cuatro bimestres (RF-03, RF-25), con FA-1 y FA-2 y la defensa de la base. */
class AnioEscolarIT extends BaseIT {

    private static final AtomicInteger SIGUIENTE = new AtomicInteger(2700);

    private static String bimestres(int anio) {
        return ("[{\"numero\":1,\"fechaInicio\":\"%1$d-02-15\",\"fechaFin\":\"%1$d-04-30\"},"
                + "{\"numero\":2,\"fechaInicio\":\"%1$d-05-03\",\"fechaFin\":\"%1$d-07-09\"},"
                + "{\"numero\":3,\"fechaInicio\":\"%1$d-07-26\",\"fechaFin\":\"%1$d-10-01\"},"
                + "{\"numero\":4,\"fechaInicio\":\"%1$d-10-04\",\"fechaFin\":\"%1$d-12-20\"}]").formatted(anio);
    }

    private static String cuerpo(int anio, String inicio, String fin, String bimestres) {
        return "{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\",\"bimestres\":%s}".formatted(inicio, fin, bimestres);
    }

    private String admin() throws Exception {
        return tokenDe(crearUsuario("ADMINISTRADOR", true));
    }

    @Test
    void elDirectivoCreaElAnioConSusBimestresYGradosYSeAudita() throws Exception {
        int anio = SIGUIENTE.getAndIncrement();
        String correo = crearUsuario("ADMINISTRADOR", true);

        Respuesta r = post("/api/v1/anios-escolares", cuerpo(anio, anio + "-02-15", anio + "-12-20", bimestres(anio)),
                tokenDe(correo));

        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("anio").asInt()).isEqualTo(anio);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("PLANIFICADO");
        assertThat(r.cuerpo().get("bimestres")).hasSize(4);
        assertThat(r.cuerpo().get("bimestres").get(2).get("fechaInicio").asString()).isEqualTo(anio + "-07-26");

        String anioId = r.cuerpo().get("id").asString();
        assertThat(jdbc.queryForList("SELECT numero_nivel FROM academico.grado WHERE anio_escolar_id = ? ORDER BY 1",
                Integer.class, anioId)).containsExactly(1, 2, 3, 4, 5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academico.bimestre WHERE anio_escolar_id = ?",
                Integer.class, anioId)).isEqualTo(4);

        List<Map<String, Object>> registros = jdbc.queryForList(
                "SELECT * FROM auditoria.registro_auditoria WHERE id_entidad_afectada = ?", anioId);
        assertThat(registros).hasSize(1);
        assertThat(registros.getFirst()).containsEntry("accion", "CREAR_ANIO_ESCOLAR").containsEntry("modulo", "ACADEMICO")
                .containsEntry("usuario_id", idDe(correo));
        assertThat(jdbc.queryForList("SELECT campo FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ?",
                String.class, registros.getFirst().get("id"))).contains("anio", "fechaInicio", "fechaFin", "bimestre 1",
                "bimestre 4");
    }

    @Test
    void fa1UnAnioDeNueveMesesSeRechazaYNoQuedaNada() throws Exception {
        int anio = SIGUIENTE.getAndIncrement();

        Respuesta r = post("/api/v1/anios-escolares", cuerpo(anio, anio + "-02-15", anio + "-11-14", bimestres(anio)), admin());

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("RANGO_ANIO_INVALIDO");
        assertThat(r.cuerpo().get("detail").asString()).isEqualTo("El año escolar debe durar entre 10 y 12 meses");
        assertThat(r.cuerpo().get("problemas").get(0).get("tipo").asString()).isEqualTo("ANIO_FUERA_DE_DURACION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academico.anio_escolar WHERE anio = ?", Integer.class, anio))
                .isZero();
    }

    @Test
    void fa2LosBimestresCruzadosSeRechazanConElTramoEnConflicto() throws Exception {
        int anio = SIGUIENTE.getAndIncrement();
        String cruzados = ("[{\"numero\":1,\"fechaInicio\":\"%1$d-02-15\",\"fechaFin\":\"%1$d-04-16\"},"
                + "{\"numero\":2,\"fechaInicio\":\"%1$d-04-26\",\"fechaFin\":\"%1$d-07-02\"},"
                + "{\"numero\":3,\"fechaInicio\":\"%1$d-06-25\",\"fechaFin\":\"%1$d-09-10\"},"
                + "{\"numero\":4,\"fechaInicio\":\"%1$d-09-20\",\"fechaFin\":\"%1$d-12-20\"}]").formatted(anio);

        Respuesta r = post("/api/v1/anios-escolares", cuerpo(anio, anio + "-02-15", anio + "-12-20", cruzados), admin());

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("BIMESTRES_SUPERPUESTOS");
        assertThat(r.cuerpo().get("detail").asString()).isEqualTo(
                "El Bimestre III se cruza con el Bimestre II. Corrige las fechas. (del 25/06/" + anio + " al 02/07/" + anio + ")");
        assertThat(r.cuerpo().get("problemas").get(0).get("desde").asString()).isEqualTo(anio + "-06-25");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academico.anio_escolar WHERE anio = ?", Integer.class, anio))
                .isZero();
    }

    @Test
    void unBimestreFueraDelAnioSeRechaza() throws Exception {
        int anio = SIGUIENTE.getAndIncrement();
        String fuera = bimestres(anio).replace(anio + "-12-20\"}]", anio + "-12-31\"}]");

        Respuesta r = post("/api/v1/anios-escolares", cuerpo(anio, anio + "-02-15", anio + "-12-20", fuera), admin());

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("BIMESTRES_SUPERPUESTOS");
        assertThat(r.cuerpo().get("problemas").get(0).get("tipo").asString()).isEqualTo("BIMESTRE_FUERA_DEL_ANIO");
    }

    @Test
    void sinLosCuatroBimestresOConNumerosRepetidosEsUnaSolicitudInvalida() throws Exception {
        int anio = SIGUIENTE.getAndIncrement();
        String token = admin();

        Respuesta tres = post("/api/v1/anios-escolares",
                cuerpo(anio, anio + "-02-15", anio + "-12-20", bimestres(anio).replaceFirst(",\\{\"numero\":4.*\\}]", "]")), token);
        assertThat(tres.estado()).isEqualTo(400);

        Respuesta repetidos = post("/api/v1/anios-escolares",
                cuerpo(anio, anio + "-02-15", anio + "-12-20", bimestres(anio).replace("\"numero\":4", "\"numero\":3")), token);
        assertThat(repetidos.estado()).isEqualTo(400);
        assertThat(repetidos.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
    }

    @Test
    void unAnioRepetidoEs409() throws Exception {
        int anio = SIGUIENTE.getAndIncrement();
        String token = admin();
        String cuerpo = cuerpo(anio, anio + "-02-15", anio + "-12-20", bimestres(anio));
        assertThat(post("/api/v1/anios-escolares", cuerpo, token).estado()).isEqualTo(201);

        Respuesta segundo = post("/api/v1/anios-escolares", cuerpo, token);

        assertThat(segundo.estado()).isEqualTo(409);
        assertThat(segundo.cuerpo().get("codigo").asString()).isEqualTo("ANIO_ESCOLAR_DUPLICADO");
    }

    @Test
    void dosDirectivosCreandoElMismoAnioALaVezDejanUnSoloAnio() throws Exception {
        int anio = SIGUIENTE.getAndIncrement();
        String token = admin();
        String cuerpo = cuerpo(anio, anio + "-02-15", anio + "-12-20", bimestres(anio));
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> envios = java.util.stream.IntStream.range(0, 4).mapToObj(i -> hilos.submit(() -> {
                salida.await();
                return post("/api/v1/anios-escolares", cuerpo, token).estado();
            })).toList();
            salida.countDown();
            List<Integer> estados = new java.util.ArrayList<>();
            for (Future<Integer> f : envios) {
                estados.add(f.get());
            }
            assertThat(estados).filteredOn(e -> e == 201).hasSize(1);
            assertThat(estados).filteredOn(e -> e == 409).hasSize(3);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academico.anio_escolar WHERE anio = ?", Integer.class, anio))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academico.bimestre b JOIN academico.anio_escolar a "
                + "ON a.id = b.anio_escolar_id WHERE a.anio = ?", Integer.class, anio)).isEqualTo(4);
    }

    @Test
    void validarInformaLaDuracionYLosProblemasSinGuardar() throws Exception {
        int anio = SIGUIENTE.getAndIncrement();
        String token = admin();

        Respuesta soloAnio = post("/api/v1/anios-escolares/validaciones",
                "{\"fechaInicio\":\"%1$d-02-15\",\"fechaFin\":\"%1$d-12-20\"}".formatted(anio), token);
        assertThat(soloAnio.estado()).isEqualTo(200);
        assertThat(soloAnio.cuerpo().get("valido").asBoolean()).isTrue();
        assertThat(soloAnio.cuerpo().get("duracion").get("meses").asInt()).isEqualTo(10);
        assertThat(soloAnio.cuerpo().get("duracion").get("dias").asInt()).isEqualTo(5);

        Respuesta corto = post("/api/v1/anios-escolares/validaciones",
                "{\"fechaInicio\":\"%1$d-02-15\",\"fechaFin\":\"%1$d-11-14\"}".formatted(anio), token);
        assertThat(corto.cuerpo().get("valido").asBoolean()).isFalse();
        assertThat(corto.cuerpo().get("problemas").get(0).get("tipo").asString()).isEqualTo("ANIO_FUERA_DE_DURACION");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM academico.anio_escolar WHERE anio = ?", Integer.class, anio))
                .isZero();
    }

    @Test
    void laBaseDeDatosImpideBimestresSuperpuestosAunSinPasarPorLaAplicacion() throws Exception {
        int anio = SIGUIENTE.getAndIncrement();
        post("/api/v1/anios-escolares", cuerpo(anio, anio + "-02-15", anio + "-12-20", bimestres(anio)), admin());
        String anioId = jdbc.queryForObject("SELECT id FROM academico.anio_escolar WHERE anio = ?", String.class, anio);
        jdbc.update("DELETE FROM academico.bimestre WHERE anio_escolar_id = ? AND numero = 4", anioId);

        // El Bimestre IV (insertado a mano) se cruza con el III: lo detiene la restricción EXCLUDE
        assertThatThrownBy(() -> jdbc.update("INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, "
                + "fecha_fin) VALUES (?, ?, 4, ?::date, ?::date)", java.util.UUID.randomUUID().toString(), anioId,
                anio + "-09-20", anio + "-12-20")).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_bimestre_sin_cruce");
    }

    @Test
    void listaYConsultaDelAnioConSusBimestres() throws Exception {
        int anio = SIGUIENTE.getAndIncrement();
        String token = admin();
        post("/api/v1/anios-escolares", cuerpo(anio, anio + "-02-15", anio + "-12-20", bimestres(anio)), token);

        Respuesta lista = get("/api/v1/anios-escolares", token);
        assertThat(lista.estado()).isEqualTo(200);
        assertThat(lista.cuerpo().get(0).get("anio").asInt()).isGreaterThanOrEqualTo(anio);

        Respuesta uno = get("/api/v1/anios-escolares/" + anio, token);
        assertThat(uno.cuerpo().get("bimestres")).hasSize(4);
        assertThat(get("/api/v1/anios-escolares/2999", token).estado()).isEqualTo(404);
    }

    @Test
    void permisosElDocenteConsultaPeroNoCreaYElAlumnoNoVeNada() throws Exception {
        int anio = SIGUIENTE.getAndIncrement();
        String docente = tokenDe(crearUsuario("DOCENTE", true));
        String alumno = tokenDe(crearUsuario("ALUMNO", true));
        String cuerpo = cuerpo(anio, anio + "-02-15", anio + "-12-20", bimestres(anio));

        assertThat(get("/api/v1/anios-escolares", docente).estado()).isEqualTo(200);
        assertThat(post("/api/v1/anios-escolares", cuerpo, docente).estado()).isEqualTo(403);
        assertThat(post("/api/v1/anios-escolares/validaciones", cuerpo, docente).estado()).isEqualTo(403);
        assertThat(get("/api/v1/anios-escolares", alumno).estado()).isEqualTo(403);
        assertThat(post("/api/v1/anios-escolares", cuerpo, null).estado()).isEqualTo(401);
    }
}
