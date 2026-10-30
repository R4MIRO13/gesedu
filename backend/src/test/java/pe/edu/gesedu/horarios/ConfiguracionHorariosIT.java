package pe.edu.gesedu.horarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.apoyo.DatosHorario.Escenario;

/** RF-46, RF-47 y RF-43: periodo de disponibilidad, docentes con sus cursos, cargas horarias y bloques. */
class ConfiguracionHorariosIT extends BaseIT {

    private DatosHorario datos;
    private Escenario e;
    private String admin;

    @BeforeEach
    void escenario() throws Exception {
        datos = new DatosHorario(jdbc);
        e = datos.crearEscenario();
        admin = tokenDe(crearUsuario("ADMINISTRADOR", true));
    }

    private String base() {
        return "/api/v1/anios-escolares/" + e.anio();
    }

    private String periodoJson(String inicio, String fin, boolean habilitado) {
        return "{\"fechaHoraInicio\":\"%s\",\"fechaHoraFin\":\"%s\",\"habilitado\":%s}".formatted(inicio, fin, habilitado);
    }

    // ---- Periodo de disponibilidad ----

    @Test
    void sinConfigurarElPeriodoVieneCerrado() throws Exception {
        Respuesta r = get(base() + "/periodo-disponibilidad", admin);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("configurado").asBoolean()).isFalse();
        assertThat(r.cuerpo().get("abierto").asBoolean()).isFalse();
    }

    @Test
    void elPeriodoVigenteEstaAbiertoUnoVencidoOCerradoManualmenteNo() throws Exception {
        Respuesta abierto = enviar("PUT", base() + "/periodo-disponibilidad",
                periodoJson("2020-01-04T08:00:00-05:00", "2099-01-15T23:59:00-05:00", true), admin, null);
        assertThat(abierto.estado()).isEqualTo(200);
        assertThat(abierto.cuerpo().get("abierto").asBoolean()).isTrue();
        assertThat(abierto.cuerpo().get("estado").asString()).isEqualTo("HABILITADO");
        assertThat(abierto.cuerpo().get("fechaHoraFin").asString()).isEqualTo("2099-01-15T23:59:00-05:00");

        Respuesta vencido = enviar("PUT", base() + "/periodo-disponibilidad",
                periodoJson("2020-01-04T08:00:00-05:00", "2020-01-15T23:59:00-05:00", true), admin, null);
        assertThat(vencido.cuerpo().get("abierto").asBoolean()).isFalse();
        assertThat(vencido.cuerpo().get("estado").asString()).isEqualTo("HABILITADO");

        Respuesta cerrado = enviar("PUT", base() + "/periodo-disponibilidad",
                periodoJson("2020-01-04T08:00:00-05:00", "2099-01-15T23:59:00-05:00", false), admin, null);
        assertThat(cerrado.cuerpo().get("abierto").asBoolean()).isFalse();
        assertThat(cerrado.cuerpo().get("estado").asString()).isEqualTo("CERRADO");

        // Siempre una sola fila por año: se actualiza, no se duplica
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.periodo_registro_disponibilidad WHERE anio_escolar_id = ?",
                Integer.class, e.anioId())).isEqualTo(1);
        assertThat(get(base() + "/periodo-disponibilidad", admin).cuerpo().get("abierto").asBoolean()).isFalse();
        assertThat(jdbc.queryForList("SELECT accion FROM auditoria.registro_auditoria WHERE accion = 'CONFIGURAR_PERIODO_DISPONIBILIDAD' "
                + "AND id_entidad_afectada = ?", String.class, String.valueOf(e.anio()))).hasSize(3);
    }

    @Test
    void unPeriodoQueTerminaAntesDeEmpezarEs400() throws Exception {
        Respuesta r = enviar("PUT", base() + "/periodo-disponibilidad",
                periodoJson("2027-01-15T08:00:00-05:00", "2027-01-04T08:00:00-05:00", true), admin, null);

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
        assertThat(get(base() + "/periodo-disponibilidad", admin).cuerpo().get("configurado").asBoolean()).isFalse();
    }

    // ---- Docentes y cursos ----


    private tools.jackson.databind.JsonNode docente(Respuesta lista, String docenteId) {
        for (var d : lista.cuerpo().get("docentes")) {
            if (d.get("docenteId").asString().equals(docenteId)) {
                return d;
            }
        }
        throw new AssertionError("el docente " + docenteId + " no está en la lista");
    }

    @Test
    void laListaMuestraAVinculadosYNoVinculadosConSuDisponibilidad() throws Exception {
        String sinVinculo = datos.docenteSinVinculacion();

        Respuesta r = get(base() + "/vinculaciones-docentes", admin);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("cursos")).hasSize(2);
        var rosa = docente(r, e.rosa());
        assertThat(rosa.get("vinculado").asBoolean()).isTrue();
        assertThat(rosa.get("habilitado").asBoolean()).isTrue();
        assertThat(rosa.get("cursoIds")).hasSize(1);
        assertThat(rosa.get("disponibilidadConfirmada").asBoolean()).isTrue();
        assertThat(rosa.get("bloquesDisponibles").asInt()).isEqualTo(3);
        assertThat(rosa.get("ultimaConfirmacion").asString()).endsWith("-05:00");
        var nuevo = docente(r, sinVinculo);
        assertThat(nuevo.get("vinculado").asBoolean()).isFalse();
        assertThat(nuevo.get("disponibilidadConfirmada").asBoolean()).isFalse();
        // Los vinculados primero
        assertThat(r.cuerpo().get("docentes").get(0).get("vinculado").asBoolean()).isTrue();
    }

    @Test
    void seVinculaAUnDocenteNuevoConSusCursosYSeAudita() throws Exception {
        String nuevo = datos.docente("Marta", "Ibáñez Ruiz");

        Respuesta r = enviar("PUT", base() + "/vinculaciones-docentes/" + nuevo,
                "{\"habilitado\":true,\"cargaSemanalReferencia\":10,\"cursoIds\":[\"%s\"]}".formatted(e.ingles()), admin, null);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("vinculado").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("cargaSemanalReferencia").asInt()).isEqualTo(10);
        assertThat(r.cuerpo().get("cursoIds").get(0).asString()).isEqualTo(e.ingles());
        assertThat(r.cuerpo().get("disponibilidadConfirmada").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.asociacion_docente_curso ad JOIN horarios.vinculacion_docente v "
                + "ON v.id = ad.vinculacion_id WHERE v.docente_id = ?", Integer.class, nuevo)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT accion FROM auditoria.registro_auditoria WHERE id_entidad_afectada = ?", String.class,
                r.cuerpo().get("vinculacionId").asString())).containsExactly("GUARDAR_VINCULACION_DOCENTE");
    }

    @Test
    void seReemplazanLosCursosAsociadosYSePuedeDeshabilitarSinClases() throws Exception {
        // Carmen dicta Inglés; ahora dicta Matemática en su lugar
        Respuesta r = enviar("PUT", base() + "/vinculaciones-docentes/" + e.carmen(),
                "{\"habilitado\":true,\"cargaSemanalReferencia\":12,\"cursoIds\":[\"%s\"]}".formatted(e.matematica()), admin, null);
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("cursoIds")).hasSize(1);
        assertThat(r.cuerpo().get("cursoIds").get(0).asString()).isEqualTo(e.matematica());

        Respuesta deshabilitada = enviar("PUT", base() + "/vinculaciones-docentes/" + e.carmen(),
                "{\"habilitado\":false,\"cargaSemanalReferencia\":12,\"cursoIds\":[\"%s\"]}".formatted(e.matematica()), admin, null);
        assertThat(deshabilitada.estado()).isEqualTo(200);
        assertThat(deshabilitada.cuerpo().get("habilitado").asBoolean()).isFalse();
    }

    private void claseEnElBorrador(String docenteId, String cursoId, String seccionId, String bloqueId) throws Exception {
        get("/api/v1/horarios/" + e.anio() + "/borrador", admin); // crea el borrador
        String borradorId = jdbc.queryForObject("SELECT id FROM horarios.horario_borrador WHERE anio_escolar_id = ?", String.class, e.anioId());
        String asociacion = jdbc.queryForObject("SELECT ad.id FROM horarios.asociacion_docente_curso ad JOIN horarios.vinculacion_docente v "
                + "ON v.id = ad.vinculacion_id WHERE v.docente_id = ? AND v.anio_escolar_id = ? AND ad.curso_id = ?", String.class,
                docenteId, e.anioId(), cursoId);
        jdbc.update("INSERT INTO horarios.asignacion_horario (id, horario_borrador_id, bloque_id, seccion_id, curso_id, docente_id, "
                + "asociacion_docente_curso_id) VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), borradorId, bloqueId,
                seccionId, cursoId, docenteId, asociacion);
    }

    @Test
    void unDocenteConClasesEnElBorradorNoSeDeshabilitaNiPierdeElCurso() throws Exception {
        claseEnElBorrador(e.rosa(), e.matematica(), e.seccion3A(), e.martes915());

        Respuesta deshabilitar = enviar("PUT", base() + "/vinculaciones-docentes/" + e.rosa(),
                "{\"habilitado\":false,\"cargaSemanalReferencia\":0,\"cursoIds\":[\"%s\"]}".formatted(e.matematica()), admin, null);
        Respuesta desasociar = enviar("PUT", base() + "/vinculaciones-docentes/" + e.rosa(),
                "{\"habilitado\":true,\"cargaSemanalReferencia\":0,\"cursoIds\":[]}", admin, null);

        for (Respuesta r : List.of(deshabilitar, desasociar)) {
            assertThat(r.estado()).isEqualTo(409);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("DOCENTE_CON_CLASES");
        }
        // Nada cambió: sigue habilitada y asociada
        var rosa = docente(get(base() + "/vinculaciones-docentes", admin), e.rosa());
        assertThat(rosa.get("habilitado").asBoolean()).isTrue();
        assertThat(rosa.get("cursoIds")).hasSize(1);
    }

    @Test
    void laVinculacionRechazaCursosDeOtroAnioCargasFueraDeRangoYDocentesInexistentes() throws Exception {
        Escenario otro = new DatosHorario(jdbc).crearEscenario();
        String ruta = base() + "/vinculaciones-docentes/" + e.rosa();

        Respuesta ajeno = enviar("PUT", ruta, "{\"habilitado\":true,\"cargaSemanalReferencia\":5,\"cursoIds\":[\"%s\"]}"
                .formatted(otro.matematica()), admin, null);
        assertThat(ajeno.estado()).isEqualTo(400);
        assertThat(enviar("PUT", ruta, "{\"habilitado\":true,\"cargaSemanalReferencia\":61,\"cursoIds\":[]}", admin, null).estado())
                .isEqualTo(400);
        assertThat(enviar("PUT", ruta, "{\"habilitado\":true,\"cargaSemanalReferencia\":-1,\"cursoIds\":[]}", admin, null).estado())
                .isEqualTo(400);
        assertThat(enviar("PUT", base() + "/vinculaciones-docentes/" + UUID.randomUUID(),
                "{\"habilitado\":true,\"cargaSemanalReferencia\":5,\"cursoIds\":[]}", admin, null).estado()).isEqualTo(404);
    }

    // ---- Cargas horarias ----

    private String gradoDe(String seccionId) {
        return jdbc.queryForObject("SELECT grado_id FROM academico.seccion WHERE id = ?", String.class, seccionId);
    }

    @Test
    void lasCargasSinConfigurarUsanLasHorasLectivasYAdviertenSiExcedenLaSemana() throws Exception {
        Respuesta r = get(base() + "/cargas-horarias", admin);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("bloquesPorSemana").asInt()).isEqualTo(3);
        assertThat(r.cuerpo().get("grados")).hasSize(2);
        var matematica = r.cuerpo().get("cursos").get(1); // «Inglés» antes que «Matemática»
        assertThat(matematica.get("curso").asString()).isEqualTo("Matemática");
        assertThat(matematica.get("horasLectivas").asInt()).isEqualTo(5);
        assertThat(matematica.get("grados").get(0).get("bloques").asInt()).isEqualTo(5);
        assertThat(matematica.get("grados").get(0).get("personalizada").asBoolean()).isFalse();
        // 5 + 3 = 8 bloques por grado en una semana de 3
        assertThat(r.cuerpo().get("totales").get(0).get("total").asInt()).isEqualTo(8);
        assertThat(r.cuerpo().get("totales").get(0).get("excedeLaSemana").asBoolean()).isTrue();
    }

    @Test
    void seGuardanSoloLasCeldasIndicadasYElBorradorLasUsaComoHorasRequeridas() throws Exception {
        String grado3 = gradoDe(e.seccion3A());
        String grado4 = gradoDe(e.seccion4B());

        Respuesta guardada = enviar("PUT", base() + "/cargas-horarias", ("{\"cargas\":[{\"gradoId\":\"%s\",\"cursoId\":\"%s\",\"bloques\":2},"
                + "{\"gradoId\":\"%s\",\"cursoId\":\"%s\",\"bloques\":1}]}").formatted(grado3, e.matematica(), grado3, e.ingles()), admin, null);

        assertThat(guardada.estado()).isEqualTo(200);
        assertThat(guardada.cuerpo().get("totales").get(0).get("total").asInt() + guardada.cuerpo().get("totales").get(1).get("total").asInt())
                .isEqualTo(3 + 8); // 3° queda en 3 bloques y 4° sigue en 8
        var filaMat = guardada.cuerpo().get("cursos").get(1);
        boolean encontrada = false;
        for (var celda : filaMat.get("grados")) {
            if (celda.get("gradoId").asString().equals(grado3)) {
                encontrada = true;
                assertThat(celda.get("bloques").asInt()).isEqualTo(2);
                assertThat(celda.get("personalizada").asBoolean()).isTrue();
            } else if (celda.get("gradoId").asString().equals(grado4)) {
                assertThat(celda.get("bloques").asInt()).isEqualTo(5);
                assertThat(celda.get("personalizada").asBoolean()).isFalse();
            }
        }
        assertThat(encontrada).isTrue();

        // La elaboración del horario ve la carga nueva: 3° A necesita 3 bloques en total
        Respuesta borrador = get("/api/v1/horarios/" + e.anio() + "/borrador?perspectiva=SECCION&seccionId=" + e.seccion3A(), admin);
        assertThat(borrador.cuerpo().get("carga").get("totalRequeridas").asInt()).isEqualTo(3);

        // Un segundo guardado actualiza la fila existente (no duplica) y se audita
        enviar("PUT", base() + "/cargas-horarias", "{\"cargas\":[{\"gradoId\":\"%s\",\"cursoId\":\"%s\",\"bloques\":3}]}"
                .formatted(grado3, e.matematica()), admin, null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.carga_horaria_curso WHERE grado_id = ? AND curso_id = ?",
                Integer.class, grado3, e.matematica())).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT c.campo FROM auditoria.cambio_auditoria c JOIN auditoria.registro_auditoria r ON "
                + "r.id = c.registro_auditoria_id WHERE r.accion = 'CONFIGURAR_CARGA_HORARIA' AND r.id_entidad_afectada = ?",
                String.class, String.valueOf(e.anio()))).contains("Matemática 3°", "Inglés 3°");
    }

    @Test
    void lasCargasRechazanBloquesFueraDeRangoCeldasRepetidasYGradosAjenos() throws Exception {
        String grado3 = gradoDe(e.seccion3A());
        Escenario otro = new DatosHorario(jdbc).crearEscenario();
        String ruta = base() + "/cargas-horarias";
        String celda = "{\"gradoId\":\"%s\",\"cursoId\":\"%s\",\"bloques\":%d}";

        assertThat(enviar("PUT", ruta, "{\"cargas\":[" + celda.formatted(grado3, e.matematica(), 0) + "]}", admin, null).estado()).isEqualTo(400);
        assertThat(enviar("PUT", ruta, "{\"cargas\":[" + celda.formatted(grado3, e.matematica(), 21) + "]}", admin, null).estado()).isEqualTo(400);
        assertThat(enviar("PUT", ruta, "{\"cargas\":[" + celda.formatted(grado3, e.matematica(), 2) + ","
                + celda.formatted(grado3, e.matematica(), 3) + "]}", admin, null).estado()).isEqualTo(400);
        assertThat(enviar("PUT", ruta, "{\"cargas\":[" + celda.formatted(gradoDe(otro.seccion3A()), e.matematica(), 2) + "]}", admin, null)
                .estado()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.carga_horaria_curso WHERE grado_id = ?", Integer.class, grado3)).isZero();
    }

    // ---- Bloques ----

    @Test
    void unBloqueSinUsoSeEliminaYUnoConDisponibilidadNo() throws Exception {
        String libre = post(base() + "/bloques", "{\"diaSemana\":\"VIERNES\",\"horaInicio\":\"13:15\",\"horaFin\":\"14:00\"}", admin)
                .cuerpo().get("id").asString();

        assertThat(enviar("DELETE", base() + "/bloques/" + libre, null, admin, null).estado()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.bloque_horario WHERE id = ?", Integer.class, libre)).isZero();
        assertThat(jdbc.queryForList("SELECT accion FROM auditoria.registro_auditoria WHERE id_entidad_afectada = ?", String.class, libre))
                .contains("CREAR_BLOQUE_HORARIO", "ELIMINAR_BLOQUE_HORARIO");

        // El del escenario lo marcaron los docentes como disponible
        Respuesta usado = enviar("DELETE", base() + "/bloques/" + e.lunes745(), null, admin, null);
        assertThat(usado.estado()).isEqualTo(409);
        assertThat(usado.cuerpo().get("codigo").asString()).isEqualTo("BLOQUE_EN_USO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.bloque_horario WHERE id = ?", Integer.class, e.lunes745())).isEqualTo(1);
        // Un bloque de otro año no se elimina desde este
        Escenario otro = new DatosHorario(jdbc).crearEscenario();
        assertThat(enviar("DELETE", base() + "/bloques/" + otro.martes915(), null, admin, null).estado()).isEqualTo(404);
    }

    // ---- Permisos ----

    @Test
    void soloQuienConfiguraHorariosUsaEstosEndpoints() throws Exception {
        String docente = tokenDe(crearUsuario("DOCENTE", true));
        String alumno = tokenDe(crearUsuario("ALUMNO", true));
        String celdas = "{\"cargas\":[]}";

        for (String token : new String[] {docente, alumno}) {
            assertThat(get(base() + "/periodo-disponibilidad", token).estado()).isEqualTo(403);
            assertThat(enviar("PUT", base() + "/periodo-disponibilidad", periodoJson("2027-01-04T08:00:00-05:00",
                    "2027-01-15T08:00:00-05:00", true), token, null).estado()).isEqualTo(403);
            assertThat(get(base() + "/vinculaciones-docentes", token).estado()).isEqualTo(403);
            assertThat(enviar("PUT", base() + "/vinculaciones-docentes/" + e.rosa(),
                    "{\"habilitado\":true,\"cargaSemanalReferencia\":1,\"cursoIds\":[]}", token, null).estado()).isEqualTo(403);
            assertThat(get(base() + "/cargas-horarias", token).estado()).isEqualTo(403);
            assertThat(enviar("PUT", base() + "/cargas-horarias", celdas, token, null).estado()).isEqualTo(403);
            assertThat(enviar("DELETE", base() + "/bloques/" + e.lunes745(), null, token, null).estado()).isEqualTo(403);
        }
        assertThat(get(base() + "/vinculaciones-docentes", null).estado()).isEqualTo(401);
    }

    @Test
    void unAnioCerradoNoSeConfigura() throws Exception {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'CERRADO' WHERE id = ?", e.anioId());
        String grado3 = gradoDe(e.seccion3A());

        List<Respuesta> respuestas = List.of(
                enviar("PUT", base() + "/periodo-disponibilidad", periodoJson("2027-01-04T08:00:00-05:00", "2027-01-15T08:00:00-05:00", true), admin, null),
                enviar("PUT", base() + "/vinculaciones-docentes/" + e.rosa(), "{\"habilitado\":true,\"cargaSemanalReferencia\":1,\"cursoIds\":[]}", admin, null),
                enviar("PUT", base() + "/cargas-horarias", "{\"cargas\":[{\"gradoId\":\"%s\",\"cursoId\":\"%s\",\"bloques\":2}]}".formatted(grado3, e.matematica()), admin, null),
                enviar("DELETE", base() + "/bloques/" + e.martes915(), null, admin, null));

        for (Respuesta r : respuestas) {
            assertThat(r.estado()).isEqualTo(422);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("ANIO_CERRADO");
        }
    }
}
