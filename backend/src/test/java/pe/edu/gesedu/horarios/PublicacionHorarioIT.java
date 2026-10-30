package pe.edu.gesedu.horarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.apoyo.DatosHorario.Escenario;

/** UC-22: completitud (RF-43) y publicación inmutable (RF-44) del horario, con la sincronización de S-13. */
class PublicacionHorarioIT extends BaseIT {

    private Escenario e;
    private String admin;
    private String borradorId;

    /**
     * Cargas del escenario: 3° exige Matemática 2 e Inglés 1; 4° exige Matemática 1 e Inglés 1. Hay tres bloques en la
     * semana (lunes 7:45, martes 9:15 y martes 10:15).
     */
    @BeforeEach
    void escenario() throws Exception {
        e = new DatosHorario(jdbc).crearEscenario();
        admin = tokenDe(crearUsuario("ADMINISTRADOR", true));
        carga(e.seccion3A(), e.matematica(), 2);
        carga(e.seccion3A(), e.ingles(), 1);
        carga(e.seccion4B(), e.matematica(), 1);
        carga(e.seccion4B(), e.ingles(), 1);
        get("/api/v1/horarios/" + e.anio() + "/borrador", admin); // crea el borrador
        borradorId = jdbc.queryForObject("SELECT id FROM horarios.horario_borrador WHERE anio_escolar_id = ?", String.class, e.anioId());
    }

    private void carga(String seccionId, String cursoId, int bloques) {
        String grado = jdbc.queryForObject("SELECT grado_id FROM academico.seccion WHERE id = ?", String.class, seccionId);
        jdbc.update("INSERT INTO horarios.carga_horaria_curso (id, grado_id, curso_id, bloques_semanales) VALUES (?, ?, ?, ?)",
                UUID.randomUUID().toString(), grado, cursoId, bloques);
    }

    private String clase(String bloqueId, String seccionId, String cursoId, String docenteId) {
        String id = UUID.randomUUID().toString();
        String asociacion = jdbc.queryForObject("SELECT ad.id FROM horarios.asociacion_docente_curso ad JOIN horarios.vinculacion_docente v "
                + "ON v.id = ad.vinculacion_id WHERE v.docente_id = ? AND v.anio_escolar_id = ? AND ad.curso_id = ?", String.class,
                docenteId, e.anioId(), cursoId);
        jdbc.update("INSERT INTO horarios.asignacion_horario (id, horario_borrador_id, bloque_id, seccion_id, curso_id, docente_id, "
                + "asociacion_docente_curso_id) VALUES (?, ?, ?, ?, ?, ?, ?)", id, borradorId, bloqueId, seccionId, cursoId, docenteId, asociacion);
        return id;
    }

    /** Un horario completo: 3° A con Matemática ×2 (Rosa) e Inglés (Carmen); 4° B con Matemática (Fernando) e Inglés (Carmen). */
    private void horarioCompleto() {
        clase(e.martes915(), e.seccion3A(), e.matematica(), e.rosa());
        clase(e.lunes745(), e.seccion3A(), e.matematica(), e.rosa());
        clase(e.martes1015(), e.seccion3A(), e.ingles(), e.carmen());
        clase(e.martes915(), e.seccion4B(), e.matematica(), e.fernando());
        clase(e.lunes745(), e.seccion4B(), e.ingles(), e.carmen());
    }

    private String ruta(String parte) {
        return "/api/v1/horarios/" + e.anio() + parte;
    }

    private Respuesta completitud() throws Exception {
        return get(ruta("/borrador/completitud"), admin);
    }

    private Respuesta publicar(String token, String ifMatch) throws Exception {
        return enviar("POST", ruta("/publicaciones"), null, token, ifMatch);
    }

    private tools.jackson.databind.JsonNode seccion(Respuesta r, String id) {
        for (var s : r.cuerpo().get("secciones")) {
            if (s.get("seccionId").asString().equals(id)) {
                return s;
            }
        }
        throw new AssertionError("no está la sección");
    }

    // ---- Completitud ----

    @Test
    void sinClasesTodasLasSeccionesFaltanPorCompleto() throws Exception {
        Respuesta r = completitud();

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("publicable").asBoolean()).isFalse();
        assertThat(r.cuerpo().get("completas").asInt()).isZero();
        assertThat(r.cuerpo().get("conObservaciones").asInt()).isEqualTo(2);
        var tercero = seccion(r, e.seccion3A());
        assertThat(tercero.get("estado").asString()).isEqualTo("FALTAN");
        assertThat(tercero.get("horasRequeridas").asInt()).isEqualTo(3);
        assertThat(tercero.get("faltan").asInt()).isEqualTo(3);
        assertThat(etag(r)).isNotNull();
    }

    @Test
    void unHorarioExactoEsPublicableUnoConFaltanteOExcesoNoYElDetalleDiceDonde() throws Exception {
        horarioCompleto();
        Respuesta completo = completitud();
        assertThat(completo.cuerpo().get("publicable").asBoolean()).isTrue();
        assertThat(completo.cuerpo().get("completas").asInt()).isEqualTo(2);
        assertThat(seccion(completo, e.seccion3A()).get("estado").asString()).isEqualTo("COMPLETO");

        // 3° A pierde su clase de Inglés (falta 1 h) y 4° B recibe una de Inglés de más (excede 1 h)
        jdbc.update("DELETE FROM horarios.asignacion_horario WHERE seccion_id = ? AND curso_id = ?", e.seccion3A(), e.ingles());
        clase(e.martes1015(), e.seccion4B(), e.ingles(), e.carmen());
        Respuesta r = completitud();

        assertThat(r.cuerpo().get("publicable").asBoolean()).isFalse();
        var tercero = seccion(r, e.seccion3A());
        assertThat(tercero.get("estado").asString()).isEqualTo("FALTAN");
        assertThat(tercero.get("faltan").asInt()).isEqualTo(1);
        assertThat(tercero.get("detalle").get(0).get("curso").asString()).isEqualTo("Inglés");
        assertThat(tercero.get("detalle").get(0).get("asignadas").asInt()).isZero();
        assertThat(tercero.get("detalle").get(0).get("requeridas").asInt()).isEqualTo(1);
        var cuarto = seccion(r, e.seccion4B());
        assertThat(cuarto.get("estado").asString()).isEqualTo("EXCEDE");
        assertThat(cuarto.get("sobran").asInt()).isEqualTo(1);
    }

    @Test
    void sinCargaPropiaRigenLasHorasLectivasDelCurso() throws Exception {
        jdbc.update("DELETE FROM horarios.carga_horaria_curso WHERE grado_id = (SELECT grado_id FROM academico.seccion WHERE id = ?)", e.seccion3A());

        var tercero = seccion(completitud(), e.seccion3A());

        assertThat(tercero.get("horasRequeridas").asInt()).isEqualTo(8); // Matemática 5 + Inglés 3
    }

    @Test
    void unDocenteHabilitadoSinDisponibilidadConfirmadaGeneraUnaAdvertenciaQueNoBloquea() throws Exception {
        horarioCompleto();
        String nuevo = new DatosHorario(jdbc).docente("Marta", "Ibáñez Ruiz");
        assertThat(enviar("PUT", "/api/v1/anios-escolares/" + e.anio() + "/vinculaciones-docentes/" + nuevo,
                "{\"habilitado\":true,\"cargaSemanalReferencia\":5,\"cursoIds\":[]}", admin, null).estado()).isEqualTo(200);

        Respuesta r = completitud();

        assertThat(r.cuerpo().get("publicable").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("advertencias")).hasSize(1);
        assertThat(r.cuerpo().get("advertencias").get(0).get("mensaje").asString())
                .isEqualTo("1 docente habilitado no confirmó su disponibilidad: Marta Ibáñez Ruiz.");
    }

    // ---- Publicación ----

    @Test
    void publicarCreaLaVersion1VigenteConUnaCopiaInmutableYSincronizaLosResponsables() throws Exception {
        horarioCompleto();
        Respuesta revisada = completitud();

        Respuesta r = publicar(admin, etag(revisada));

        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("numeroVersion").asInt()).isEqualTo(1);
        assertThat(r.cuerpo().get("vigente").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("clases").asInt()).isEqualTo(5);
        assertThat(r.cuerpo().get("fechaHoraPublicacion").asString()).endsWith("-05:00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.asignacion_publicada WHERE horario_publicado_id = ?",
                Integer.class, r.cuerpo().get("id").asString())).isEqualTo(5);
        // S-13: un responsable por curso y sección
        assertThat(jdbc.queryForList("SELECT docente_id FROM evaluacion.asignacion_docente WHERE seccion_id = ? AND curso_id = ?",
                String.class, e.seccion3A(), e.matematica())).containsExactly(e.rosa());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluacion.asignacion_docente WHERE seccion_id IN (?, ?)", Integer.class,
                e.seccion3A(), e.seccion4B())).isEqualTo(4);
        // El borrador sigue y su versión subió
        assertThat(Long.parseLong(etag(completitud()).replace("\"", ""))).isGreaterThan(Long.parseLong(etag(revisada).replace("\"", "")));
        assertThat(jdbc.queryForList("SELECT accion FROM auditoria.registro_auditoria WHERE id_entidad_afectada = ?", String.class,
                r.cuerpo().get("id").asString())).containsExactly("PUBLICAR_HORARIO");
        var despues = completitud();
        assertThat(despues.cuerpo().get("sinCambios").asBoolean()).isTrue();
        assertThat(despues.cuerpo().get("vigente").get("numeroVersion").asInt()).isEqualTo(1);
    }

    @Test
    void fa1UnHorarioIncompletoNoSePublicaYElBorradorNoCambia() throws Exception {
        clase(e.martes915(), e.seccion3A(), e.matematica(), e.rosa());
        Respuesta revisada = completitud();

        Respuesta r = publicar(admin, etag(revisada));

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("HORARIO_INCOMPLETO");
        assertThat(r.cuerpo().get("secciones")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.horario_publicado WHERE anio_escolar_id = ?", Integer.class, e.anioId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluacion.asignacion_docente WHERE seccion_id = ?", Integer.class, e.seccion3A())).isZero();
        assertThat(etag(completitud())).isEqualTo(etag(revisada));
    }

    @Test
    void sinBorradorNiSeccionesCompletasNoHayQuePublicar() throws Exception {
        Escenario otro = new DatosHorario(jdbc).crearEscenario();

        Respuesta r = enviar("POST", "/api/v1/horarios/" + otro.anio() + "/publicaciones", null, admin, "\"0\"");

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("HORARIO_INCOMPLETO");
    }

    @Test
    void publicarUnaSegundaVersionRetiraLaVigenteYNumeraEnOrden() throws Exception {
        horarioCompleto();
        publicar(admin, etag(completitud()));
        // El administrador cambia una clase: Carlos dicta la Matemática de 4° B en lugar de Fernando
        jdbc.update("UPDATE horarios.asignacion_horario SET docente_id = ?, asociacion_docente_curso_id = (SELECT ad.id FROM "
                + "horarios.asociacion_docente_curso ad JOIN horarios.vinculacion_docente v ON v.id = ad.vinculacion_id WHERE "
                + "v.docente_id = ? AND v.anio_escolar_id = ? AND ad.curso_id = ?) WHERE seccion_id = ? AND curso_id = ?",
                e.carlos(), e.carlos(), e.anioId(), e.matematica(), e.seccion4B(), e.matematica());
        Respuesta revisada = completitud();
        assertThat(revisada.cuerpo().get("sinCambios").asBoolean()).isFalse();

        Respuesta v2 = publicar(admin, etag(revisada));

        assertThat(v2.estado()).isEqualTo(201);
        assertThat(v2.cuerpo().get("numeroVersion").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT numero_version || ':' || vigente FROM horarios.horario_publicado WHERE anio_escolar_id = ? "
                + "ORDER BY numero_version", String.class, e.anioId())).containsExactly("1:false", "2:true");
        // El responsable de Matemática en 4° B cambió de docente
        assertThat(jdbc.queryForList("SELECT docente_id FROM evaluacion.asignacion_docente WHERE seccion_id = ? AND curso_id = ?",
                String.class, e.seccion4B(), e.matematica())).containsExactly(e.carlos());
        // La versión 1 conserva sus clases tal como se publicaron
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.asignacion_publicada ap JOIN horarios.horario_publicado h ON "
                + "h.id = ap.horario_publicado_id WHERE h.anio_escolar_id = ? AND h.numero_version = 1 AND ap.docente_id = ?",
                Integer.class, e.anioId(), e.fernando())).isEqualTo(1);
        assertThat(get(ruta("/publicaciones"), admin).cuerpo().get(0).get("numeroVersion").asInt()).isEqualTo(2);
    }

    @Test
    void sinIfMatchEs400YConElBorradorCambiadoDespuesDeRevisarEs409() throws Exception {
        horarioCompleto();
        String revisada = etag(completitud());

        assertThat(publicar(admin, null).estado()).isEqualTo(400);
        // Otra persona cambia el borrador (sube su versión) después de que se revisó
        String clase = jdbc.queryForObject("SELECT id FROM horarios.asignacion_horario WHERE seccion_id = ? AND curso_id = ? LIMIT 1",
                String.class, e.seccion4B(), e.ingles());
        assertThat(enviar("DELETE", ruta("/borrador/asignaciones/" + clase), null, admin, revisada).estado()).isEqualTo(204);

        Respuesta r = publicar(admin, revisada);
        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("CONFLICTO_CONCURRENCIA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.horario_publicado", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.horario_publicado WHERE anio_escolar_id = ?", Integer.class, e.anioId())).isZero();
    }

    @Test
    void cuatroPublicacionesSimultaneasConLaMismaRevisionDejanUnaSolaVersion() throws Exception {
        horarioCompleto();
        String revisada = etag(completitud());
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(4);
        List<Integer> estados = new ArrayList<>();
        try {
            List<Future<Integer>> envios = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                envios.add(hilos.submit(() -> {
                    salida.await();
                    return publicar(admin, revisada).estado();
                }));
            }
            salida.countDown();
            for (Future<Integer> f : envios) {
                estados.add(f.get());
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(estados).filteredOn(s -> s == 201).hasSize(1);
        assertThat(estados).filteredOn(s -> s == 409).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.horario_publicado WHERE anio_escolar_id = ?", Integer.class, e.anioId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.horario_publicado WHERE anio_escolar_id = ? AND vigente", Integer.class, e.anioId())).isEqualTo(1);
    }

    @Test
    void laBaseGarantizaLaInmutabilidadYUnaSolaVersionVigente() throws Exception {
        horarioCompleto();
        String id = publicar(admin, etag(completitud())).cuerpo().get("id").asString();

        assertThatThrownBy(() -> jdbc.update("UPDATE horarios.asignacion_publicada SET docente_id = ? WHERE horario_publicado_id = ?", e.carlos(), id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("inmutables");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM horarios.asignacion_publicada WHERE horario_publicado_id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("inmutables");
        assertThatThrownBy(() -> jdbc.update("UPDATE horarios.horario_publicado SET numero_version = 9 WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("inmutables");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM horarios.horario_publicado WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("inmutables");
        // Una segunda versión vigente a la vez la rechaza el índice único parcial
        assertThatThrownBy(() -> jdbc.update("INSERT INTO horarios.horario_publicado (id, anio_escolar_id, numero_version, "
                + "fecha_hora_publicacion, vigente, publicado_por) SELECT ?, anio_escolar_id, 2, now(), TRUE, publicado_por "
                + "FROM horarios.horario_publicado WHERE id = ?", UUID.randomUUID().toString(), id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ux_horario_publicado_vigente");
    }

    @Test
    void editarElBorradorDespuesDePublicarNoTocaLoPublicado() throws Exception {
        horarioCompleto();
        String id = publicar(admin, etag(completitud())).cuerpo().get("id").asString();
        String clase = jdbc.queryForObject("SELECT id FROM horarios.asignacion_horario WHERE seccion_id = ? AND curso_id = ? LIMIT 1",
                String.class, e.seccion4B(), e.ingles());

        assertThat(enviar("DELETE", ruta("/borrador/asignaciones/" + clase), null, admin, etag(completitud())).estado()).isEqualTo(204);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.asignacion_publicada WHERE horario_publicado_id = ?", Integer.class, id)).isEqualTo(5);
        var r = completitud();
        assertThat(r.cuerpo().get("sinCambios").asBoolean()).isFalse();
        assertThat(r.cuerpo().get("publicable").asBoolean()).isFalse();
    }

    @Test
    void unAnioCerradoNoSePublica() throws Exception {
        horarioCompleto();
        String revisada = etag(completitud());
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'CERRADO' WHERE id = ?", e.anioId());

        Respuesta r = publicar(admin, revisada);

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("ANIO_CERRADO");
    }

    @Test
    void soloQuienGestionaHorariosValidaYPublica() throws Exception {
        horarioCompleto();
        String docente = tokenDe(crearUsuario("DOCENTE", true));
        String alumno = tokenDe(crearUsuario("ALUMNO", true));

        for (String token : new String[] {docente, alumno}) {
            assertThat(get(ruta("/borrador/completitud"), token).estado()).isEqualTo(403);
            assertThat(publicar(token, "\"0\"").estado()).isEqualTo(403);
            assertThat(get(ruta("/publicaciones"), token).estado()).isEqualTo(403);
        }
        assertThat(get(ruta("/borrador/completitud"), null).estado()).isEqualTo(401);
    }
}
