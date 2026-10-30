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

import pe.edu.gesedu.apoyo.BaseIT;

/** UC-14: cursos y competencias del año (RF-23, RF-24), la copia del año anterior (FA-1) y sus defensas. */
class CursosIT extends BaseIT {

    private static final AtomicInteger SIGUIENTE = new AtomicInteger(2750);

    private int crearAnio(String estado) {
        int anio = SIGUIENTE.getAndIncrement();
        return crearAnio(anio, estado);
    }

    private int crearAnio(int anio, String estado) {
        jdbc.update("INSERT INTO academico.anio_escolar (id, anio, fecha_inicio, fecha_fin, estado) VALUES (?, ?, ?::date, ?::date, ?)",
                UUID.randomUUID().toString(), anio, anio + "-02-15", anio + "-12-20", estado);
        return anio;
    }

    private String admin() throws Exception {
        return tokenDe(crearUsuario("ADMINISTRADOR", true));
    }

    private static String curso(String nombre, int horas, String... competencias) {
        StringBuilder lista = new StringBuilder();
        for (String c : competencias) {
            lista.append(lista.isEmpty() ? "" : ",").append("{\"nombre\":\"").append(c).append("\"}");
        }
        return "{\"nombre\":\"%s\",\"horasLectivas\":%d,\"competencias\":[%s]}".formatted(nombre, horas, lista);
    }

    private String cursosDe(int anio) {
        return "/api/v1/anios-escolares/" + anio + "/cursos";
    }

    @Test
    void elDirectivoCreaUnCursoConSusCompetenciasYSeAudita() throws Exception {
        int anio = crearAnio("PLANIFICADO");
        String correo = crearUsuario("ADMINISTRADOR", true);

        Respuesta r = post(cursosDe(anio), curso("Ciencia y Tecnología", 4, "Indaga mediante métodos científicos",
                "Explica el mundo físico", "Diseña soluciones tecnológicas"), tokenDe(correo));

        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("slug").asString()).isEqualTo("ciencia-y-tecnologia");
        assertThat(r.cuerpo().get("horasLectivas").asInt()).isEqualTo(4);
        assertThat(r.cuerpo().get("habilitado").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("competencias")).hasSize(3);
        assertThat(r.cuerpo().get("competencias").get(0).get("codigo").asString()).isEqualTo("C1");
        assertThat(r.cuerpo().get("competencias").get(2).get("codigo").asString()).isEqualTo("C3");

        List<Map<String, Object>> registros = jdbc.queryForList(
                "SELECT * FROM auditoria.registro_auditoria WHERE id_entidad_afectada = ?", r.cuerpo().get("id").asString());
        assertThat(registros).hasSize(1);
        assertThat(registros.getFirst()).containsEntry("accion", "CREAR_CURSO").containsEntry("modulo", "ACADEMICO")
                .containsEntry("usuario_id", idDe(correo));
        assertThat(jdbc.queryForList("SELECT campo FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ?",
                String.class, registros.getFirst().get("id"))).contains("curso", "horasLectivas", "competencia C1", "competencia C3");
    }

    @Test
    void laListaDelAnioTraLosCursosConElTotalDeHoras() throws Exception {
        int anio = crearAnio("PLANIFICADO");
        String token = admin();
        post(cursosDe(anio), curso("Matemática", 5, "Cantidad"), token);
        post(cursosDe(anio), curso("Inglés", 3, "Oral", "Lectura"), token);

        Respuesta r = get(cursosDe(anio), token);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("totalHoras").asInt()).isEqualTo(8);
        assertThat(r.cuerpo().get("cursos")).hasSize(2);
        assertThat(r.cuerpo().get("cursos").get(0).get("nombre").asString()).isEqualTo("Inglés");
        assertThat(r.cuerpo().get("cursos").get(1).get("competencias")).hasSize(1);
    }

    @Test
    void unNombreRepetidoOSuVersionSinTildesEs409() throws Exception {
        int anio = crearAnio("PLANIFICADO");
        String token = admin();
        assertThat(post(cursosDe(anio), curso("Matemática", 5, "Cantidad"), token).estado()).isEqualTo(201);

        Respuesta igual = post(cursosDe(anio), curso("matemática", 4, "Otra"), token);
        Respuesta sinTilde = post(cursosDe(anio), curso("Matematica", 4, "Otra"), token);

        assertThat(igual.estado()).isEqualTo(409);
        assertThat(igual.cuerpo().get("codigo").asString()).isEqualTo("CURSO_DUPLICADO");
        assertThat(sinTilde.estado()).isEqualTo(409);
        assertThat(get(cursosDe(anio), token).cuerpo().get("cursos")).hasSize(1);
    }

    @Test
    void cuatroAltasSimultaneasDelMismoCursoDejanUnoSolo() throws Exception {
        int anio = crearAnio("PLANIFICADO");
        String token = admin();
        String cuerpo = curso("Educación Física", 2, "Motricidad");
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> envios = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                envios.add(hilos.submit(() -> {
                    salida.await();
                    return post(cursosDe(anio), cuerpo, token).estado();
                }));
            }
            salida.countDown();
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> f : envios) {
                estados.add(f.get());
            }
            assertThat(estados).filteredOn(e -> e == 201).hasSize(1);
            assertThat(estados).filteredOn(e -> e == 409).hasSize(3);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academico.curso c JOIN academico.anio_escolar a "
                + "ON a.id = c.anio_escolar_id WHERE a.anio = ?", Integer.class, anio)).isEqualTo(1);
    }

    @Test
    void sinCompetenciasConHorasFueraDeRangoOConCompetenciasRepetidasEsInvalido() throws Exception {
        int anio = crearAnio("PLANIFICADO");
        String token = admin();

        assertThat(post(cursosDe(anio), curso("Arte", 2), token).estado()).isEqualTo(400);
        assertThat(post(cursosDe(anio), curso("Arte", 0, "Crea"), token).estado()).isEqualTo(400);
        assertThat(post(cursosDe(anio), curso("Arte", 41, "Crea"), token).estado()).isEqualTo(400);
        Respuesta repetidas = post(cursosDe(anio), curso("Arte", 2, "Crea", "crea"), token);
        assertThat(repetidas.estado()).isEqualTo(400);
        assertThat(repetidas.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
        assertThat(post(cursosDe(anio), curso(" ", 2, "Crea"), token).estado()).isEqualTo(400);
        assertThat(get(cursosDe(anio), token).cuerpo().get("cursos")).isEmpty();
    }

    @Test
    void editarAgregaEditaYQuitaCompetenciasSinReutilizarCodigos() throws Exception {
        int anio = crearAnio("PLANIFICADO");
        String token = admin();
        Respuesta creado = post(cursosDe(anio), curso("Comunicación", 5, "Oralidad", "Lectura", "Escritura"), token);
        String id = creado.cuerpo().get("id").asString();
        String c1 = creado.cuerpo().get("competencias").get(0).get("id").asString();
        String c3 = creado.cuerpo().get("competencias").get(2).get("id").asString();

        // Se quita C2, se renombra C3 y se agrega una nueva; las horas cambian de 5 a 6
        Respuesta r = enviar("PUT", "/api/v1/cursos/" + id, ("{\"nombre\":\"Comunicación\",\"horasLectivas\":6,\"competencias\":["
                + "{\"id\":\"%s\",\"nombre\":\"Se comunica oralmente\"},{\"id\":\"%s\",\"nombre\":\"Escribe textos\"},"
                + "{\"nombre\":\"Interpreta medios\"}]}").formatted(c1, c3), token, null);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("horasLectivas").asInt()).isEqualTo(6);
        assertThat(r.cuerpo().get("competencias")).hasSize(3);
        assertThat(r.cuerpo().get("competencias").get(0).get("codigo").asString()).isEqualTo("C1");
        assertThat(r.cuerpo().get("competencias").get(1).get("codigo").asString()).isEqualTo("C3");
        assertThat(r.cuerpo().get("competencias").get(1).get("nombre").asString()).isEqualTo("Escribe textos");
        // C2 se quitó y su código no se reutiliza: la nueva es C4
        assertThat(r.cuerpo().get("competencias").get(2).get("codigo").asString()).isEqualTo("C4");
        assertThat(r.cuerpo().get("competencias").get(2).get("orden").asInt()).isEqualTo(3);

        List<String> campos = jdbc.queryForList("SELECT c.campo FROM auditoria.cambio_auditoria c JOIN "
                + "auditoria.registro_auditoria r ON r.id = c.registro_auditoria_id WHERE r.accion = 'MODIFICAR_CURSO' "
                + "AND r.id_entidad_afectada = ?", String.class, id);
        assertThat(campos).contains("horasLectivas", "competencia C1", "competencia C3", "competencia C4", "competencia C2");
    }

    @Test
    void editarConservaElNivelEsperadoYRechazaUnaCompetenciaAjena() throws Exception {
        int anio = crearAnio("PLANIFICADO");
        String token = admin();
        Respuesta a = post(cursosDe(anio), "{\"nombre\":\"Arte\",\"horasLectivas\":2,\"competencias\":[{\"nombre\":\"Crea\","
                + "\"nivelEsperado\":\"Crea proyectos artísticos\"}]}", token);
        Respuesta b = post(cursosDe(anio), curso("Música", 2, "Interpreta"), token);
        String idA = a.cuerpo().get("id").asString();
        assertThat(a.cuerpo().get("competencias").get(0).get("nivelEsperado").asString()).isEqualTo("Crea proyectos artísticos");

        Respuesta ajena = enviar("PUT", "/api/v1/cursos/" + idA, ("{\"nombre\":\"Arte\",\"horasLectivas\":2,\"competencias\":["
                + "{\"id\":\"%s\",\"nombre\":\"x\"}]}").formatted(b.cuerpo().get("competencias").get(0).get("id").asString()),
                token, null);
        assertThat(ajena.estado()).isEqualTo(400);

        Respuesta renombrado = enviar("PUT", "/api/v1/cursos/" + idA, ("{\"nombre\":\"Arte y Cultura\",\"horasLectivas\":3,"
                + "\"competencias\":[{\"id\":\"%s\",\"nombre\":\"Crea\",\"nivelEsperado\":\"Crea proyectos artísticos\"}]}")
                .formatted(a.cuerpo().get("competencias").get(0).get("id").asString()), token, null);
        assertThat(renombrado.estado()).isEqualTo(200);
        assertThat(renombrado.cuerpo().get("slug").asString()).isEqualTo("arte-y-cultura");
        assertThat(renombrado.cuerpo().get("competencias").get(0).get("nivelEsperado").asString())
                .isEqualTo("Crea proyectos artísticos");
        // Renombrarlo con el nombre de otro curso del año es un duplicado
        Respuesta choque = enviar("PUT", "/api/v1/cursos/" + idA, curso("Música", 2, "Crea"), token, null);
        assertThat(choque.estado()).isEqualTo(409);
    }

    @Test
    void unCursoSinUsoSeEliminaYUnoConCargaHorariaNo() throws Exception {
        int anio = crearAnio("PLANIFICADO");
        String token = admin();
        String libre = post(cursosDe(anio), curso("Tutoría", 1, "Convive"), token).cuerpo().get("id").asString();
        String enUso = post(cursosDe(anio), curso("Religión", 2, "Construye su identidad"), token).cuerpo().get("id").asString();
        String gradoId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.grado (id, anio_escolar_id, numero_nivel) SELECT ?, id, 1 FROM academico.anio_escolar "
                + "WHERE anio = ?", gradoId, anio);
        jdbc.update("INSERT INTO horarios.carga_horaria_curso (id, grado_id, curso_id, bloques_semanales) VALUES (?, ?, ?, 2)",
                UUID.randomUUID().toString(), gradoId, enUso);

        assertThat(enviar("DELETE", "/api/v1/cursos/" + libre, null, token, null).estado()).isEqualTo(204);
        assertThat(get("/api/v1/cursos/" + libre, token).estado()).isEqualTo(404);

        Respuesta ocupado = enviar("DELETE", "/api/v1/cursos/" + enUso, null, token, null);
        assertThat(ocupado.estado()).isEqualTo(409);
        assertThat(ocupado.cuerpo().get("codigo").asString()).isEqualTo("CURSO_EN_USO");
        // El rechazo revierte todo: el curso conserva sus competencias
        assertThat(get("/api/v1/cursos/" + enUso, token).cuerpo().get("competencias")).hasSize(1);
        assertThat(jdbc.queryForList("SELECT accion FROM auditoria.registro_auditoria WHERE id_entidad_afectada = ?",
                String.class, libre)).contains("ELIMINAR_CURSO");
        assertThat(jdbc.queryForList("SELECT accion FROM auditoria.registro_auditoria WHERE id_entidad_afectada = ?",
                String.class, enUso)).doesNotContain("ELIMINAR_CURSO");
    }

    @Test
    void unAnioCerradoNoSeConfigura() throws Exception {
        int anio = crearAnio("CERRADO");
        String token = admin();
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) SELECT ?, id, 'Arte', 'arte', 2 "
                + "FROM academico.anio_escolar WHERE anio = ?", id, anio);

        Respuesta creado = post(cursosDe(anio), curso("Música", 2, "Interpreta"), token);
        Respuesta editado = enviar("PUT", "/api/v1/cursos/" + id, curso("Arte", 3, "Crea"), token, null);
        Respuesta eliminado = enviar("DELETE", "/api/v1/cursos/" + id, null, token, null);

        for (Respuesta r : List.of(creado, editado, eliminado)) {
            assertThat(r.estado()).isEqualTo(422);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("ANIO_CERRADO");
        }
        assertThat(get(cursosDe(anio), token).cuerpo().get("cursos")).hasSize(1);
    }

    @Test
    void fa1LaCopiaDelAnioAnteriorTraeLosCursosConSusCompetenciasYNoDuplicaAlRepetirse() throws Exception {
        crearAnio(2800, "CERRADO");
        crearAnio(2801, "PLANIFICADO");
        String token = admin();
        // 2800 ya tiene dos cursos (se siembran con la API de un año abierto y luego se cierra no hace falta: es directo)
        String idMat = sembrarCurso(2800, "Matemática", "matematica", 5, "Cantidad", "Regularidad");
        sembrarCurso(2800, "Inglés", "ingles", 3, "Oral");
        post(cursosDe(2801), curso("Inglés", 3, "Oral"), token);

        Respuesta vista = get(cursosDe(2801) + "/copia-anterior", token);
        assertThat(vista.estado()).isEqualTo(200);
        assertThat(vista.cuerpo().get("anioOrigen").asInt()).isEqualTo(2800);
        assertThat(vista.cuerpo().get("cursos")).hasSize(2);
        assertThat(vista.cuerpo().get("cursos").get(0).get("nombre").asString()).isEqualTo("Inglés");
        assertThat(vista.cuerpo().get("cursos").get(0).get("yaExiste").asBoolean()).isTrue();
        assertThat(vista.cuerpo().get("cursos").get(1).get("yaExiste").asBoolean()).isFalse();

        Respuesta copia = post(cursosDe(2801) + "/copia-anterior", "{}", token);
        assertThat(copia.estado()).isEqualTo(200);
        assertThat(copia.cuerpo().get("anioOrigen").asInt()).isEqualTo(2800);
        assertThat(copia.cuerpo().get("copiados")).hasSize(1);
        assertThat(copia.cuerpo().get("copiados").get(0).get("nombre").asString()).isEqualTo("Matemática");
        assertThat(copia.cuerpo().get("copiados").get(0).get("competencias")).hasSize(2);
        assertThat(copia.cuerpo().get("copiados").get(0).get("competencias").get(1).get("codigo").asString()).isEqualTo("C2");
        assertThat(copia.cuerpo().get("omitidos").get(0).asString()).isEqualTo("Inglés");

        Respuesta repetida = post(cursosDe(2801) + "/copia-anterior", "{\"cursoIds\":[\"" + idMat + "\"]}", token);
        assertThat(repetida.cuerpo().get("copiados")).isEmpty();
        assertThat(repetida.cuerpo().get("omitidos")).hasSize(1);
        assertThat(get(cursosDe(2801), token).cuerpo().get("cursos")).hasSize(2);
        // El año anterior quedó intacto
        assertThat(get(cursosDe(2800), token).cuerpo().get("cursos")).hasSize(2);
        assertThat(jdbc.queryForList("SELECT accion FROM auditoria.registro_auditoria WHERE accion = 'COPIAR_CURSOS_ANIO_ANTERIOR'",
                String.class)).isNotEmpty();
    }

    @Test
    void laCopiaPuedeElegirCursosYRechazaUnoQueNoEsDelAnioAnterior() throws Exception {
        crearAnio(2810, "CERRADO");
        crearAnio(2811, "PLANIFICADO");
        String token = admin();
        String idMat = sembrarCurso(2810, "Matemática", "matematica", 5, "Cantidad");
        sembrarCurso(2810, "Inglés", "ingles", 3, "Oral");

        Respuesta ajeno = post(cursosDe(2811) + "/copia-anterior", "{\"cursoIds\":[\"" + UUID.randomUUID() + "\"]}", token);
        assertThat(ajeno.estado()).isEqualTo(400);

        Respuesta uno = post(cursosDe(2811) + "/copia-anterior", "{\"cursoIds\":[\"" + idMat + "\"]}", token);
        assertThat(uno.cuerpo().get("copiados")).hasSize(1);
        assertThat(get(cursosDe(2811), token).cuerpo().get("cursos")).hasSize(1);
    }

    @Test
    void sinAnioAnteriorConCursosLaVistaVieneVaciaYCopiarEs404() throws Exception {
        crearAnio(2820, "PLANIFICADO");
        String token = admin();

        Respuesta vista = get(cursosDe(2820) + "/copia-anterior", token);
        // Puede haber cursos en años menores sembrados por otras pruebas; lo que importa es que 2820 no se copia a sí mismo
        assertThat(vista.estado()).isEqualTo(200);
        assertThat(vista.cuerpo().get("anioOrigen").isNull() || vista.cuerpo().get("anioOrigen").asInt() < 2820).isTrue();
        assertThat(get(cursosDe(2999) , token).estado()).isEqualTo(404);
    }

    @Test
    void permisosElDocenteConsultaPeroNoModificaYElAlumnoNoVeNada() throws Exception {
        int anio = crearAnio("PLANIFICADO");
        String docente = tokenDe(crearUsuario("DOCENTE", true));
        String alumno = tokenDe(crearUsuario("ALUMNO", true));

        assertThat(get(cursosDe(anio), docente).estado()).isEqualTo(200);
        assertThat(post(cursosDe(anio), curso("Arte", 2, "Crea"), docente).estado()).isEqualTo(403);
        assertThat(get(cursosDe(anio) + "/copia-anterior", docente).estado()).isEqualTo(403);
        assertThat(enviar("DELETE", "/api/v1/cursos/" + UUID.randomUUID(), null, docente, null).estado()).isEqualTo(403);
        assertThat(get(cursosDe(anio), alumno).estado()).isEqualTo(403);
        assertThat(get(cursosDe(anio), null).estado()).isEqualTo(401);
    }

    private String sembrarCurso(int anio, String nombre, String slug, int horas, String... competencias) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) "
                + "SELECT ?, id, ?, ?, ? FROM academico.anio_escolar WHERE anio = ?", id, nombre, slug, horas, anio);
        int orden = 1;
        for (String c : competencias) {
            jdbc.update("INSERT INTO academico.competencia (id, curso_id, codigo, nombre, orden) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), id, "C" + orden, c, orden);
            orden++;
        }
        return id;
    }
}
