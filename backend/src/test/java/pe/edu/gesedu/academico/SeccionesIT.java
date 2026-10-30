package pe.edu.gesedu.academico;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;

/** RF-03 y RF-04: secciones por grado con capacidad, vacantes y tutor, y su convivencia con la matrícula. */
class SeccionesIT extends BaseIT {

    private static final AtomicInteger SIGUIENTE = new AtomicInteger(2900);

    @Autowired
    private CatalogoAcademicoService catalogo;

    /** Año con su grado de 3°; devuelve {anio, gradoId}. */
    private record Escenario(int anio, String anioId, String gradoId) {
    }

    private Escenario escenario(String estado) {
        int anio = SIGUIENTE.getAndIncrement();
        String anioId = UUID.randomUUID().toString();
        String gradoId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.anio_escolar (id, anio, fecha_inicio, fecha_fin, estado) VALUES (?, ?, ?::date, ?::date, ?)",
                anioId, anio, anio + "-02-15", anio + "-12-20", estado);
        jdbc.update("INSERT INTO academico.grado (id, anio_escolar_id, numero_nivel) VALUES (?, ?, 3)", gradoId, anioId);
        return new Escenario(anio, anioId, gradoId);
    }

    private String seccionSql(Escenario e, String letra, int capacidad, int vacantes) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.seccion (id, grado_id, anio_escolar_id, letra, capacidad, vacantes_disponibles) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, e.gradoId(), e.anioId(), letra, capacidad, vacantes);
        return id;
    }

    private String admin() throws Exception {
        return tokenDe(crearUsuario("ADMINISTRADOR", true));
    }

    private String docente(String nombres) {
        return new DatosHorario(jdbc).docente(nombres, "Tutor Prueba");
    }

    private Respuesta seccion(String id, String token) throws Exception {
        return get("/api/v1/secciones/" + id, token);
    }

    @Test
    void laListaTraeLosGradosConSusSeccionesElCupoYLosTutoresCandidatos() throws Exception {
        Escenario e = escenario("PLANIFICADO");
        String tutor = docente("Rosa");
        String id = seccionSql(e, "A", 30, 4);
        jdbc.update("UPDATE academico.seccion SET tutor_docente_id = ? WHERE id = ?", tutor, id);
        seccionSql(e, "B", 30, 30);

        Respuesta r = get("/api/v1/anios-escolares/" + e.anio() + "/grados", admin());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("PLANIFICADO");
        assertThat(r.cuerpo().get("grados")).hasSize(1);
        var secciones = r.cuerpo().get("grados").get(0).get("secciones");
        assertThat(secciones).hasSize(2);
        assertThat(secciones.get(0).get("nombre").asString()).isEqualTo("3° A");
        assertThat(secciones.get(0).get("capacidad").asInt()).isEqualTo(30);
        assertThat(secciones.get(0).get("vacantes").asInt()).isEqualTo(4);
        assertThat(secciones.get(0).get("matriculados").asInt()).isEqualTo(26);
        assertThat(secciones.get(0).get("tutor").asString()).isEqualTo("Rosa Tutor Prueba");
        assertThat(secciones.get(1).get("tutor").isNull()).isTrue();
        assertThat(java.util.stream.StreamSupport.stream(r.cuerpo().get("docentes").spliterator(), false)
                .map(d -> d.get("id").asString())).contains(tutor);
    }

    @Test
    void agregaUnaSeccionConTodasSusVacantesYSeAudita() throws Exception {
        Escenario e = escenario("PLANIFICADO");
        String correo = crearUsuario("ADMINISTRADOR", true);
        String tutor = docente("Ana");

        Respuesta r = post("/api/v1/grados/" + e.gradoId() + "/secciones",
                "{\"letra\":\"c\",\"capacidad\":28,\"tutorDocenteId\":\"%s\"}".formatted(tutor), tokenDe(correo));

        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("nombre").asString()).isEqualTo("3° C");
        assertThat(r.cuerpo().get("capacidad").asInt()).isEqualTo(28);
        assertThat(r.cuerpo().get("vacantes").asInt()).isEqualTo(28);
        assertThat(r.cuerpo().get("matriculados").asInt()).isZero();
        assertThat(r.cuerpo().get("tutor").asString()).isEqualTo("Ana Tutor Prueba");
        assertThat(etag(r)).isNotNull();
        assertThat(jdbc.queryForList("SELECT accion FROM auditoria.registro_auditoria WHERE id_entidad_afectada = ?",
                String.class, r.cuerpo().get("id").asString())).containsExactly("CREAR_SECCION");
    }

    @Test
    void laLetraRepetidaLaLetraInvalidaYLaCapacidadFueraDeRangoSeRechazan() throws Exception {
        Escenario e = escenario("PLANIFICADO");
        String token = admin();
        seccionSql(e, "A", 30, 30);
        String ruta = "/api/v1/grados/" + e.gradoId() + "/secciones";

        Respuesta repetida = post(ruta, "{\"letra\":\"a\",\"capacidad\":30}", token);
        assertThat(repetida.estado()).isEqualTo(409);
        assertThat(repetida.cuerpo().get("codigo").asString()).isEqualTo("SECCION_DUPLICADA");
        assertThat(post(ruta, "{\"letra\":\"AB\",\"capacidad\":30}", token).estado()).isEqualTo(400);
        assertThat(post(ruta, "{\"letra\":\"1\",\"capacidad\":30}", token).estado()).isEqualTo(400);
        assertThat(post(ruta, "{\"letra\":\"B\",\"capacidad\":0}", token).estado()).isEqualTo(400);
        assertThat(post(ruta, "{\"letra\":\"B\",\"capacidad\":61}", token).estado()).isEqualTo(400);
        assertThat(post("/api/v1/grados/" + UUID.randomUUID() + "/secciones", "{\"letra\":\"B\",\"capacidad\":30}", token).estado())
                .isEqualTo(404);
    }

    @Test
    void aumentarLaCapacidadSumaLasMismasVacantesYSubeLaVersion() throws Exception {
        Escenario e = escenario("PLANIFICADO");
        String token = admin();
        String id = seccionSql(e, "A", 30, 4);
        Respuesta antes = seccion(id, token);

        Respuesta r = enviar("PUT", "/api/v1/secciones/" + id, "{\"capacidad\":35}", token, etag(antes));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("capacidad").asInt()).isEqualTo(35);
        assertThat(r.cuerpo().get("vacantes").asInt()).isEqualTo(9);
        assertThat(r.cuerpo().get("matriculados").asInt()).isEqualTo(26);
        assertThat(Long.parseLong(etag(r).replace("\"", ""))).isGreaterThan(Long.parseLong(etag(antes).replace("\"", "")));
        List<String> campos = jdbc.queryForList("SELECT c.campo FROM auditoria.cambio_auditoria c JOIN auditoria.registro_auditoria r "
                + "ON r.id = c.registro_auditoria_id WHERE r.accion = 'MODIFICAR_SECCION' AND r.id_entidad_afectada = ?", String.class, id);
        assertThat(campos).contains("capacidad", "vacantes");
    }

    @Test
    void laCapacidadNoPuedeQuedarPorDebajoDeLosMatriculados() throws Exception {
        Escenario e = escenario("PLANIFICADO");
        String token = admin();
        String id = seccionSql(e, "A", 30, 5); // 25 matriculados

        Respuesta menor = enviar("PUT", "/api/v1/secciones/" + id, "{\"capacidad\":20}", token, etag(seccion(id, token)));
        assertThat(menor.estado()).isEqualTo(422);
        assertThat(menor.cuerpo().get("codigo").asString()).isEqualTo("CAPACIDAD_INSUFICIENTE");
        assertThat(menor.cuerpo().get("detail").asString()).contains("25");

        Respuesta justa = enviar("PUT", "/api/v1/secciones/" + id, "{\"capacidad\":25}", token, etag(seccion(id, token)));
        assertThat(justa.estado()).isEqualTo(200);
        assertThat(justa.cuerpo().get("vacantes").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT vacantes_disponibles FROM academico.seccion WHERE id = ?", Integer.class, id)).isZero();
    }

    @Test
    void sinIfMatchEs400YConUnaVersionVencidaEs409() throws Exception {
        Escenario e = escenario("PLANIFICADO");
        String token = admin();
        String id = seccionSql(e, "A", 30, 30);
        String version = etag(seccion(id, token));

        assertThat(enviar("PUT", "/api/v1/secciones/" + id, "{\"capacidad\":31}", token, null).estado()).isEqualTo(400);
        assertThat(enviar("PUT", "/api/v1/secciones/" + id, "{\"capacidad\":31}", token, version).estado()).isEqualTo(200);
        Respuesta vieja = enviar("PUT", "/api/v1/secciones/" + id, "{\"capacidad\":32}", token, version);
        assertThat(vieja.estado()).isEqualTo(409);
        assertThat(vieja.cuerpo().get("codigo").asString()).isEqualTo("CONFLICTO_CONCURRENCIA");
        assertThat(jdbc.queryForObject("SELECT capacidad FROM academico.seccion WHERE id = ?", Integer.class, id)).isEqualTo(31);
    }

    @Test
    void unDocenteSoloEsTutorDeUnaSeccionPorAnio() throws Exception {
        Escenario e = escenario("PLANIFICADO");
        String token = admin();
        String tutor = docente("Luis");
        String a = seccionSql(e, "A", 30, 30);
        String b = seccionSql(e, "B", 30, 30);

        assertThat(enviar("PUT", "/api/v1/secciones/" + a, "{\"capacidad\":30,\"tutorDocenteId\":\"%s\"}".formatted(tutor), token,
                etag(seccion(a, token))).estado()).isEqualTo(200);
        Respuesta segunda = enviar("PUT", "/api/v1/secciones/" + b, "{\"capacidad\":30,\"tutorDocenteId\":\"%s\"}".formatted(tutor),
                token, etag(seccion(b, token)));
        assertThat(segunda.estado()).isEqualTo(409);
        assertThat(segunda.cuerpo().get("codigo").asString()).isEqualTo("TUTOR_YA_ASIGNADO");
        // También al crear una sección nueva
        Respuesta nueva = post("/api/v1/grados/" + e.gradoId() + "/secciones",
                "{\"letra\":\"C\",\"capacidad\":30,\"tutorDocenteId\":\"%s\"}".formatted(tutor), token);
        assertThat(nueva.estado()).isEqualTo(409);
        assertThat(nueva.cuerpo().get("codigo").asString()).isEqualTo("TUTOR_YA_ASIGNADO");

        // Quitar el tutor de A lo libera
        assertThat(enviar("PUT", "/api/v1/secciones/" + a, "{\"capacidad\":30}", token, etag(seccion(a, token))).estado()).isEqualTo(200);
        assertThat(seccion(a, token).cuerpo().get("tutorDocenteId").isNull()).isTrue();
        assertThat(enviar("PUT", "/api/v1/secciones/" + b, "{\"capacidad\":30,\"tutorDocenteId\":\"%s\"}".formatted(tutor), token,
                etag(seccion(b, token))).estado()).isEqualTo(200);
    }

    @Test
    void unTutorInexistenteEs400() throws Exception {
        Escenario e = escenario("PLANIFICADO");
        String token = admin();
        String id = seccionSql(e, "A", 30, 30);

        Respuesta r = enviar("PUT", "/api/v1/secciones/" + id,
                "{\"capacidad\":30,\"tutorDocenteId\":\"%s\"}".formatted(UUID.randomUUID()), token, etag(seccion(id, token)));

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
    }

    @Test
    void unAnioCerradoNoSeConfigura() throws Exception {
        Escenario e = escenario("CERRADO");
        String token = admin();
        String id = seccionSql(e, "A", 30, 30);

        Respuesta creada = post("/api/v1/grados/" + e.gradoId() + "/secciones", "{\"letra\":\"B\",\"capacidad\":30}", token);
        Respuesta editada = enviar("PUT", "/api/v1/secciones/" + id, "{\"capacidad\":31}", token, etag(seccion(id, token)));

        for (Respuesta r : List.of(creada, editada)) {
            assertThat(r.estado()).isEqualTo(422);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("ANIO_CERRADO");
        }
    }

    @Test
    void cambiarLaCapacidadMientrasSeMatriculaNuncaPierdeUnaVacanteNiLaDesbordaNiPisaUnaMatricula() throws Exception {
        Escenario e = escenario("PLANIFICADO");
        String token = admin();
        String id = seccionSql(e, "A", 30, 10); // 20 matriculados
        int intentos = 8;
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(intentos + 1);
        AtomicInteger matriculas = new AtomicInteger();
        try {
            List<Future<?>> trabajos = new ArrayList<>();
            for (int i = 0; i < intentos; i++) {
                trabajos.add(hilos.submit(() -> {
                    salida.await();
                    try {
                        catalogo.reservarVacante(id);
                        matriculas.incrementAndGet();
                    } catch (ExcepcionNegocio sinVacante) {
                        // no ocurre con 10 vacantes y 8 intentos
                    }
                    return null;
                }));
            }
            String version = etag(seccion(id, token));
            trabajos.add(hilos.submit(() -> {
                salida.await();
                // Puede ganar (200) o perder contra una matrícula que subió la versión (409): ambas son correctas
                enviar("PUT", "/api/v1/secciones/" + id, "{\"capacidad\":40}", token, version);
                return null;
            }));
            salida.countDown();
            for (Future<?> f : trabajos) {
                f.get();
            }
        } finally {
            hilos.shutdownNow();
        }

        Map<String, Object> fila = jdbc.queryForMap("SELECT capacidad, vacantes_disponibles FROM academico.seccion WHERE id = ?", id);
        int capacidad = (Integer) fila.get("capacidad");
        int vacantes = (Integer) fila.get("vacantes_disponibles");
        assertThat(matriculas.get()).isEqualTo(intentos);
        // Matriculados = los 20 de partida + las matrículas que entraron, gane quien gane
        assertThat(capacidad - vacantes).isEqualTo(20 + intentos);
        assertThat(vacantes).isBetween(0, capacidad);
    }

    @Test
    void permisosElDocenteConsultaPeroNoModificaYElAlumnoNoVeNada() throws Exception {
        Escenario e = escenario("PLANIFICADO");
        String id = seccionSql(e, "A", 30, 30);
        String docente = tokenDe(crearUsuario("DOCENTE", true));
        String alumno = tokenDe(crearUsuario("ALUMNO", true));

        assertThat(get("/api/v1/anios-escolares/" + e.anio() + "/grados", docente).estado()).isEqualTo(200);
        assertThat(seccion(id, docente).estado()).isEqualTo(200);
        assertThat(enviar("PUT", "/api/v1/secciones/" + id, "{\"capacidad\":31}", docente, "\"0\"").estado()).isEqualTo(403);
        assertThat(post("/api/v1/grados/" + e.gradoId() + "/secciones", "{\"letra\":\"B\",\"capacidad\":30}", docente).estado())
                .isEqualTo(403);
        assertThat(get("/api/v1/anios-escolares/" + e.anio() + "/grados", alumno).estado()).isEqualTo(403);
        assertThat(get("/api/v1/anios-escolares/" + e.anio() + "/grados", null).estado()).isEqualTo(401);
    }
}
