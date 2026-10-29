package pe.edu.gesedu.horarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.apoyo.DatosHorario.Escenario;

/** UC-21, Elaborar horario: borrador, perspectivas, validación en vivo, altas, cambios y bajas (RF-39 a RF-42, RF-47). */
class BorradorHorarioIT extends BaseIT {

    private Escenario esc;
    private String admin;

    @BeforeEach
    void preparar() throws Exception {
        esc = new DatosHorario(jdbc).crearEscenario();
        admin = tokenDe(crearUsuario("ADMINISTRADOR", true));
    }

    private String base() {
        return "/api/v1/horarios/" + esc.anio() + "/borrador";
    }

    private String cuerpo(String bloque, String seccion, String curso, String docente) throws Exception {
        return json.writeValueAsString(Map.of("bloqueId", bloque, "seccionId", seccion, "cursoId", curso,
                "docenteId", docente));
    }

    private Respuesta obtener(String perspectivaYFiltro) throws Exception {
        return get(base() + "?" + perspectivaYFiltro, admin);
    }

    private Respuesta crear(String bloque, String seccion, String curso, String docente, String ifMatch)
            throws Exception {
        return enviar("POST", base() + "/asignaciones", cuerpo(bloque, seccion, curso, docente), admin, ifMatch);
    }

    /** Versión actual del borrador (la que hay que mandar en If-Match). */
    private String version() throws Exception {
        return etag(obtener("perspectiva=SECCION"));
    }

    private static void assertProblema(Respuesta r, int estado, String codigo) {
        assertThat(r.estado()).isEqualTo(estado);
        assertThat(r.tipoContenido()).startsWith("application/problem+json");
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo(codigo);
    }

    private int registrosDeAuditoria(String accion, String idEntidad) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = ? "
                + "AND id_entidad_afectada = ?", Integer.class, accion, idEntidad);
    }

    // --- Acceso (RF-44, RNF-07) ---

    @Test
    void soloQuienGestionaHorariosVeElBorrador() throws Exception {
        for (String rol : List.of("ALUMNO", "APODERADO", "DOCENTE")) {
            String token = tokenDe(crearUsuario(rol, true));
            assertProblema(get(base(), token), 403, "ACCESO_DENEGADO");
            assertProblema(enviar("POST", base() + "/validaciones",
                    cuerpo(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.rosa()), token, null), 403,
                    "ACCESO_DENEGADO");
        }
        assertProblema(get(base(), null), 401, "NO_AUTENTICADO");
    }

    // --- Pasos 1 a 3: el borrador y sus perspectivas ---

    @Test
    void elBorradorSeCreaSiNoExisteYDevuelveElETag() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.horario_borrador WHERE anio_escolar_id = "
                + "(SELECT id FROM academico.anio_escolar WHERE anio = ?)", Integer.class, esc.anio())).isZero();

        Respuesta r = obtener("perspectiva=SECCION");

        assertThat(r.estado()).isEqualTo(200);
        assertThat(etag(r)).isEqualTo("\"0\"");
        JsonNode borrador = r.cuerpo();
        assertThat(borrador.get("anio").asInt()).isEqualTo(esc.anio());
        assertThat(borrador.get("version").asInt()).isZero();
        assertThat(borrador.get("perspectiva").asString()).isEqualTo("SECCION");
        // Sin seccionId se muestra la primera sección (3° A)
        assertThat(borrador.get("seccionId").asString()).isEqualTo(esc.seccion3A());
        assertThat(borrador.get("secciones").valueStream().map(s -> s.get("nombre").asString()).toList())
                .containsExactly("3° A", "4° B");
        assertThat(borrador.get("bloques").size()).isEqualTo(3);
        assertThat(borrador.get("bloques").get(0).get("diaSemana").asString()).isEqualTo("LUNES");
        assertThat(borrador.get("docentes").size()).isEqualTo(4);
        assertThat(borrador.get("asignaciones").size()).isZero();
        // Segunda consulta: el mismo borrador
        assertThat(obtener("perspectiva=SECCION").cuerpo().get("id").asString()).isEqualTo(borrador.get("id").asString());
    }

    @Test
    void lasPerspectivasSeccionYDocenteVenElMismoBorrador() throws Exception {
        assertThat(crear(esc.martes915(), esc.seccion4B(), esc.matematica(), esc.rosa(), version()).estado())
                .isEqualTo(201);
        assertThat(crear(esc.lunes745(), esc.seccion3A(), esc.matematica(), esc.fernando(), version()).estado())
                .isEqualTo(201);

        JsonNode porSeccion = obtener("perspectiva=SECCION&seccionId=" + esc.seccion4B()).cuerpo();
        JsonNode porDocente = obtener("perspectiva=DOCENTE&docenteId=" + esc.rosa()).cuerpo();
        JsonNode porOtroDocente = obtener("perspectiva=DOCENTE&docenteId=" + esc.fernando()).cuerpo();

        assertThat(porSeccion.get("id").asString()).isEqualTo(porDocente.get("id").asString());
        assertThat(porSeccion.get("version").asInt()).isEqualTo(porDocente.get("version").asInt()).isEqualTo(2);
        assertThat(porSeccion.get("asignaciones")).hasSize(1);
        assertThat(porSeccion.get("asignaciones").get(0).get("docente").asString()).isEqualTo("Rosa Quispe Mendoza");
        assertThat(porDocente.get("asignaciones")).hasSize(1);
        assertThat(porDocente.get("asignaciones").get(0).get("seccion").asString()).isEqualTo("4° B");
        assertThat(porDocente.get("carga").isNull()).isTrue();
        assertThat(porOtroDocente.get("asignaciones").get(0).get("seccion").asString()).isEqualTo("3° A");
    }

    @Test
    void unaSeccionOUnDocenteInexistentesResponden404() throws Exception {
        assertProblema(obtener("perspectiva=SECCION&seccionId=no-existe"), 404, "RECURSO_NO_ENCONTRADO");
        assertProblema(obtener("perspectiva=DOCENTE&docenteId=no-existe"), 404, "RECURSO_NO_ENCONTRADO");
    }

    // --- Paso 5: opciones del panel de asignación (RF-43, RF-47) ---

    @Test
    void laCargaHorariaDeLaSeccionMuestraHorasAsignadasYRequeridas() throws Exception {
        crear(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.rosa(), version());

        JsonNode carga = obtener("perspectiva=SECCION&seccionId=" + esc.seccion3A()).cuerpo().get("carga");

        assertThat(carga.get("totalAsignadas").asInt()).isEqualTo(1);
        assertThat(carga.get("totalRequeridas").asInt()).isEqualTo(8); // Matemática 5 + Inglés 3
        JsonNode matematica = carga.get("cursos").valueStream()
                .filter(c -> c.get("curso").asString().equals("Matemática")).findFirst().orElseThrow();
        assertThat(matematica.get("horasAsignadas").asInt()).isEqualTo(1);
        assertThat(matematica.get("horasRequeridas").asInt()).isEqualTo(5);
    }

    @Test
    void laCargaUsaLaCargaHorariaDelGradoSiEstaConfigurada() throws Exception {
        jdbc.update("INSERT INTO horarios.carga_horaria_curso (id, grado_id, curso_id, bloques_semanales) "
                + "SELECT gen_random_uuid()::text, s.grado_id, ?, 6 FROM academico.seccion s WHERE s.id = ?",
                esc.matematica(), esc.seccion3A());

        JsonNode carga = obtener("perspectiva=SECCION&seccionId=" + esc.seccion3A()).cuerpo().get("carga");

        assertThat(carga.get("cursos").valueStream().filter(c -> c.get("curso").asString().equals("Matemática"))
                .findFirst().orElseThrow().get("horasRequeridas").asInt()).isEqualTo(6);
        assertThat(carga.get("totalRequeridas").asInt()).isEqualTo(9);
    }

    @Test
    void lasOpcionesOfrecenSoloLosDocentesAsociadosConSuDisponibilidadYOcupacion() throws Exception {
        crear(esc.martes915(), esc.seccion4B(), esc.matematica(), esc.rosa(), version());

        JsonNode opciones = get(base() + "/opciones?bloqueId=" + esc.martes915() + "&seccionId=" + esc.seccion3A()
                + "&cursoId=" + esc.matematica(), admin).cuerpo();

        assertThat(opciones.get("cursos")).hasSize(2);
        List<JsonNode> docentes = opciones.get("docentes").valueStream().toList();
        // Carmen (solo Inglés) no se ofrece para Matemática (RF-47)
        assertThat(docentes.stream().map(d -> d.get("nombre").asString()).toList())
                .containsExactly("Fernando Medina Soto", "Carlos Ríos Vega", "Rosa Quispe Mendoza");
        JsonNode fernando = docentes.get(0);
        assertThat(fernando.get("disponible").asBoolean()).isTrue();
        assertThat(fernando.get("ocupadoEn").isNull()).isTrue();
        JsonNode carlos = docentes.get(1);
        assertThat(carlos.get("disponible").asBoolean()).isFalse();
        JsonNode rosa = docentes.get(2);
        assertThat(rosa.get("disponible").asBoolean()).isTrue();
        assertThat(rosa.get("ocupadoEn").asString()).isEqualTo("4° B");
        assertThat(rosa.get("detalleOcupado").asString()).isEqualTo("tiene clase en 4° B el martes de 9:15 a 10:00");
    }

    @Test
    void sinCursoLasOpcionesSoloTraenLosCursos() throws Exception {
        JsonNode opciones = get(base() + "/opciones?bloqueId=" + esc.martes915() + "&seccionId=" + esc.seccion3A(),
                admin).cuerpo();

        assertThat(opciones.get("cursos")).hasSize(2);
        assertThat(opciones.get("docentes")).isEmpty();
    }

    // --- Pasos 6 y 7: validación en vivo ---

    @Test
    void laValidacionEnVivoInformaTodosLosConflictosSinGuardar() throws Exception {
        crear(esc.martes915(), esc.seccion4B(), esc.matematica(), esc.rosa(), version());

        Respuesta rosa = enviar("POST", base() + "/validaciones",
                cuerpo(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.rosa()), admin, null);
        Respuesta carlos = enviar("POST", base() + "/validaciones",
                cuerpo(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.carlos()), admin, null);
        Respuesta fernando = enviar("POST", base() + "/validaciones",
                cuerpo(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.fernando()), admin, null);

        assertThat(rosa.estado()).isEqualTo(200);
        assertThat(rosa.cuerpo().get("valido").asBoolean()).isFalse();
        assertThat(rosa.cuerpo().get("conflictos").get(0).get("tipo").asString()).isEqualTo("CRUCE_DOCENTE");
        assertThat(rosa.cuerpo().get("conflictos").get(0).get("mensaje").asString())
                .isEqualTo("Prof. R. Quispe ya tiene clase en 4° B el martes de 9:15 a 10:00");
        assertThat(carlos.cuerpo().get("conflictos").get(0).get("tipo").asString()).isEqualTo("DOCENTE_NO_DISPONIBLE");
        assertThat(carlos.cuerpo().get("conflictos").get(0).get("mensaje").asString())
                .isEqualTo("Prof. C. Ríos no está disponible el martes de 9:15 a 10:00");
        assertThat(fernando.cuerpo().get("valido").asBoolean()).isTrue();
        assertThat(fernando.cuerpo().get("conflictos")).isEmpty();
        // Validar no guarda nada
        assertThat(obtener("perspectiva=SECCION&seccionId=" + esc.seccion3A()).cuerpo().get("asignaciones")).isEmpty();
    }

    // --- Paso 8: alta (flujo básico) y FE-1, FE-2 ---

    @Test
    void agregarUnaClaseSubeLaVersionDevuelveElETagYQuedaAuditada() throws Exception {
        Respuesta r = crear(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.fernando(), version());

        assertThat(r.estado()).isEqualTo(201);
        assertThat(etag(r)).isEqualTo("\"1\"");
        JsonNode asignacion = r.cuerpo();
        assertThat(asignacion.get("seccion").asString()).isEqualTo("3° A");
        assertThat(asignacion.get("curso").asString()).isEqualTo("Matemática");
        assertThat(asignacion.get("docente").asString()).isEqualTo("Fernando Medina Soto");
        assertThat(version()).isEqualTo("\"1\"");

        String id = asignacion.get("id").asString();
        assertThat(registrosDeAuditoria("CREAR_ASIGNACION_HORARIO", id)).isEqualTo(1);
        Map<String, Object> registro = jdbc.queryForMap("SELECT * FROM auditoria.registro_auditoria WHERE "
                + "accion = 'CREAR_ASIGNACION_HORARIO' AND id_entidad_afectada = ?", id);
        assertThat(registro.get("modulo")).isEqualTo("HORARIOS");
        assertThat(registro.get("usuario_id")).isNotNull();
        List<String> campos = jdbc.queryForList("SELECT campo FROM auditoria.cambio_auditoria WHERE "
                + "registro_auditoria_id = ? ORDER BY campo", String.class, registro.get("id"));
        assertThat(campos).containsExactly("bloque", "curso", "docente", "sección");
    }

    @Test
    void elCruceDeDocenteSeRechazaConLaCausaYNoSeGuardaNiSeAudita() throws Exception {
        crear(esc.martes915(), esc.seccion4B(), esc.matematica(), esc.rosa(), version());
        int antes = jdbc.queryForObject("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = "
                + "'CREAR_ASIGNACION_HORARIO'", Integer.class);
        String versionAntes = version();

        Respuesta r = crear(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.rosa(), versionAntes);

        assertProblema(r, 409, "CONFLICTO_HORARIO");
        assertThat(r.cuerpo().get("detail").asString())
                .isEqualTo("Prof. R. Quispe ya tiene clase en 4° B el martes de 9:15 a 10:00");
        assertThat(r.cuerpo().get("conflictos").get(0).get("tipo").asString()).isEqualTo("CRUCE_DOCENTE");
        assertThat(version()).isEqualTo(versionAntes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = "
                + "'CREAR_ASIGNACION_HORARIO'", Integer.class)).isEqualTo(antes);
    }

    @Test
    void elCruceDeSeccionSeRechazaConLaCausa() throws Exception {
        crear(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.fernando(), version());

        Respuesta r = crear(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.rosa(), version());

        assertProblema(r, 409, "CONFLICTO_HORARIO");
        assertThat(r.cuerpo().get("detail").asString()).isEqualTo("3° A ya tiene Matemática el martes de 9:15 a 10:00");
        assertThat(r.cuerpo().get("conflictos").get(0).get("tipo").asString()).isEqualTo("CRUCE_SECCION");
    }

    @Test
    void unDocenteNoDisponibleSeRechazaConDocenteNoDisponible() throws Exception {
        Respuesta r = crear(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.carlos(), version());

        assertProblema(r, 422, "DOCENTE_NO_DISPONIBLE");
        assertThat(r.cuerpo().get("detail").asString()).isEqualTo("Prof. C. Ríos no está disponible el martes de 9:15 a 10:00");
    }

    @Test
    void unDocenteNoAsociadoAlCursoSeRechazaConDocenteNoAsociado() throws Exception {
        Respuesta r = crear(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.carmen(), version());

        assertProblema(r, 422, "DOCENTE_NO_ASOCIADO");
        assertThat(r.cuerpo().get("detail").asString()).isEqualTo("Prof. C. Villanueva no está asociado al curso Matemática");
    }

    @Test
    void unDocenteSinVinculacionEnElAnioNoEsAsociable() throws Exception {
        String suelto = new DatosHorario(jdbc).docenteSinVinculacion();

        assertProblema(crear(esc.martes915(), esc.seccion3A(), esc.matematica(), suelto, version()), 422,
                "DOCENTE_NO_ASOCIADO");
    }

    @Test
    void identificadoresInexistentesOEnBlancoSeRechazan() throws Exception {
        assertProblema(crear("no-existe", esc.seccion3A(), esc.matematica(), esc.rosa(), version()), 404,
                "RECURSO_NO_ENCONTRADO");
        assertProblema(crear(esc.martes915(), "no-existe", esc.matematica(), esc.rosa(), version()), 404,
                "RECURSO_NO_ENCONTRADO");
        assertProblema(crear(esc.martes915(), esc.seccion3A(), "no-existe", esc.rosa(), version()), 404,
                "RECURSO_NO_ENCONTRADO");
        Respuesta enBlanco = enviar("POST", base() + "/asignaciones", json.writeValueAsString(
                Map.of("bloqueId", "", "seccionId", esc.seccion3A(), "cursoId", esc.matematica(), "docenteId",
                        esc.rosa())), admin, version());
        assertProblema(enBlanco, 400, "VALIDACION_INVALIDA");
    }

    // --- Concurrencia por versión (If-Match) ---

    @Test
    void sinIfMatchResponde400YConUnaVersionVencidaResponde409() throws Exception {
        String vieja = version();
        assertThat(crear(esc.lunes745(), esc.seccion3A(), esc.matematica(), esc.rosa(), vieja).estado()).isEqualTo(201);

        assertProblema(crear(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.fernando(), null), 400,
                "VERSION_REQUERIDA");
        Respuesta desactualizada = crear(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.fernando(), vieja);
        assertProblema(desactualizada, 409, "CONFLICTO_CONCURRENCIA");
        assertThat(obtener("perspectiva=SECCION").cuerpo().get("asignaciones")).hasSize(1);
    }

    // --- FA-1 y FA-2 ---

    @Test
    void modificarUnaClaseCambiaElDocenteYAudita() throws Exception {
        String id = crear(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.rosa(), version())
                .cuerpo().get("id").asString();

        // FA-1: Rosa → Fernando en el mismo bloque. Rosa no choca consigo misma al mantener bloque y sección
        Respuesta r = enviar("PUT", base() + "/asignaciones/" + id,
                cuerpo(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.fernando()), admin, version());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(etag(r)).isEqualTo("\"2\"");
        assertThat(r.cuerpo().get("docente").asString()).isEqualTo("Fernando Medina Soto");
        assertThat(obtener("perspectiva=SECCION&seccionId=" + esc.seccion3A()).cuerpo().get("asignaciones"))
                .hasSize(1);
        Map<String, Object> registro = jdbc.queryForMap("SELECT * FROM auditoria.registro_auditoria WHERE "
                + "accion = 'MODIFICAR_ASIGNACION_HORARIO' AND id_entidad_afectada = ?", id);
        Map<String, Object> cambio = jdbc.queryForMap("SELECT * FROM auditoria.cambio_auditoria WHERE "
                + "registro_auditoria_id = ?", registro.get("id"));
        assertThat(cambio.get("campo")).isEqualTo("docente");
        assertThat(cambio.get("valor_anterior")).isEqualTo("Rosa Quispe Mendoza");
        assertThat(cambio.get("valor_nuevo")).isEqualTo("Fernando Medina Soto");
    }

    @Test
    void modificarValidaDeNuevoLosConflictos() throws Exception {
        crear(esc.martes915(), esc.seccion4B(), esc.matematica(), esc.rosa(), version());
        String id = crear(esc.lunes745(), esc.seccion3A(), esc.matematica(), esc.rosa(), version())
                .cuerpo().get("id").asString();

        // Mover la clase de 3° A al martes de 9:15 con Rosa choca con su clase en 4° B
        Respuesta r = enviar("PUT", base() + "/asignaciones/" + id,
                cuerpo(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.rosa()), admin, version());

        assertProblema(r, 409, "CONFLICTO_HORARIO");
        assertThat(r.cuerpo().get("conflictos").get(0).get("tipo").asString()).isEqualTo("CRUCE_DOCENTE");
    }

    @Test
    void eliminarUnaClaseActualizaLaMatrizYAudita() throws Exception {
        String id = crear(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.rosa(), version())
                .cuerpo().get("id").asString();

        Respuesta r = enviar("DELETE", base() + "/asignaciones/" + id, null, admin, version());

        assertThat(r.estado()).isEqualTo(204);
        assertThat(etag(r)).isEqualTo("\"2\"");
        assertThat(obtener("perspectiva=SECCION&seccionId=" + esc.seccion3A()).cuerpo().get("asignaciones")).isEmpty();
        assertThat(registrosDeAuditoria("ELIMINAR_ASIGNACION_HORARIO", id)).isEqualTo(1);
        // Y el bloque queda libre para otro docente
        assertThat(crear(esc.martes915(), esc.seccion3A(), esc.matematica(), esc.fernando(), version()).estado())
                .isEqualTo(201);
        assertProblema(enviar("DELETE", base() + "/asignaciones/" + id, null, admin, version()), 404,
                "RECURSO_NO_ENCONTRADO");
    }
}
