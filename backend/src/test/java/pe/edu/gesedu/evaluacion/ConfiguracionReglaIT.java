package pe.edu.gesedu.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.apoyo.DatosMatricula;
import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;

/**
 * UC-16, Definir regla de consolidación (RF-26, RF-38): el docente elige entre promedio ponderado (por defecto), moda y
 * último registro para su curso en su sección, y ajusta el peso de cada bimestre. Todo se guarda o nada, queda auditado y
 * solo lo hace el docente de la asignación.
 * <p>
 * Escenario: año ACTIVO propio con cuatro bimestres; Matemática (C1, C2) que Rosa dicta en 3° A y Luis en 3° B.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfiguracionReglaIT extends BaseIT {

    private Escenario e;
    private String rosa;
    private String luis;
    private String asignacionRosa;
    private String asignacionLuis;
    private final String[] bimestres = new String[5];
    private final Map<String, String> tokens = new HashMap<>();

    @BeforeAll
    void escenario() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE estado = 'ACTIVO'");
        e = new DatosMatricula(jdbc).crearEscenario(30);
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        String[][] fechas = {{"1", "02-15", "04-30"}, {"2", "05-01", "07-15"}, {"3", "07-16", "09-30"}, {"4", "10-01", "12-20"}};
        for (String[] b : fechas) {
            int numero = Integer.parseInt(b[0]);
            bimestres[numero] = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin) "
                    + "VALUES (?, ?, ?, ?::date, ?::date)", bimestres[numero], e.anioId(), numero, e.anio() + "-" + b[1],
                    e.anio() + "-" + b[2]);
        }
        String curso = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) VALUES (?, ?, 'Matemática', "
                + "'matematica', 4)", curso, e.anioId());
        for (int i = 1; i <= 2; i++) {
            jdbc.update("INSERT INTO academico.competencia (id, curso_id, codigo, nombre, nivel_esperado, orden) VALUES (?, ?, ?, ?, 'A', ?)",
                    UUID.randomUUID().toString(), curso, "C" + i, "Competencia " + i, i);
        }
        DatosHorario docentes = new DatosHorario(jdbc);
        rosa = docentes.docente("Rosa", "Quispe");
        luis = docentes.docente("Luis", "Castro");
        asignacionRosa = asignacion(rosa, curso, e.seccion3A());
        asignacionLuis = asignacion(luis, curso, e.seccion3B());
    }

    @AfterAll
    void devolverElAnio() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
    }

    @BeforeEach
    void restablecer() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        jdbc.update("DELETE FROM evaluacion.peso_bimestre WHERE asignacion_id IN (?, ?)", asignacionRosa, asignacionLuis);
        jdbc.update("UPDATE evaluacion.asignacion_docente SET regla_consolidacion = 'PROMEDIO_PONDERADO' WHERE id IN (?, ?)",
                asignacionRosa, asignacionLuis);
    }

    // ---- datos y ayudas ----

    private String asignacion(String docenteId, String cursoId, String seccionId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO evaluacion.asignacion_docente (id, docente_id, curso_id, seccion_id) VALUES (?, ?, ?, ?)",
                id, docenteId, cursoId, seccionId);
        return id;
    }

    private String token(String docenteId) throws Exception {
        String cacheado = tokens.get(docenteId);
        if (cacheado != null) {
            return cacheado;
        }
        String usuario = jdbc.queryForObject("SELECT usuario_id FROM seguridad.docente WHERE id = ?", String.class, docenteId);
        String correo = "regla-" + usuario.substring(0, 8) + "@test.gesedu.pe";
        jdbc.update("UPDATE seguridad.usuario SET correo = ?, hash_contrasena = ?, activo = TRUE WHERE id = ?", correo,
                codificador.encode(CONTRASENA), usuario);
        invalidarCuentas();
        String token = tokenDe(correo);
        tokens.put(docenteId, token);
        return token;
    }

    private Respuesta consultar(String asignacion, String token) throws Exception {
        return get("/api/v1/asignaciones-docentes/" + asignacion + "/configuracion", token);
    }

    private Respuesta guardar(String asignacion, String token, Object cuerpo) throws Exception {
        return enviar("PUT", "/api/v1/asignaciones-docentes/" + asignacion + "/configuracion", json.writeValueAsString(cuerpo),
                token, null);
    }

    private static Map<String, Object> peso(int bimestre, String peso) {
        Map<String, Object> m = new HashMap<>();
        m.put("bimestre", bimestre);
        m.put("peso", peso);
        return m;
    }

    private String reglaGuardada(String asignacion) {
        return jdbc.queryForObject("SELECT regla_consolidacion FROM evaluacion.asignacion_docente WHERE id = ?", String.class, asignacion);
    }

    private Map<Integer, String> pesosGuardados(String asignacion) {
        Map<Integer, String> resultado = new HashMap<>();
        jdbc.queryForList("SELECT b.numero, p.peso FROM evaluacion.peso_bimestre p JOIN academico.bimestre b ON b.id = p.bimestre_id "
                + "WHERE p.asignacion_id = ?", asignacion).forEach(f -> resultado.put(((Number) f.get("numero")).intValue(),
                f.get("peso").toString()));
        return resultado;
    }

    private int auditorias() {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'CONFIGURAR_REGLA_CONSOLIDACION' "
                + "AND id_entidad_afectada = ?", Integer.class, asignacionRosa);
    }

    private List<Map<String, Object>> cambiosDeLaUltimaAuditoria(List<String> previos) {
        List<Map<String, Object>> nuevos = jdbc.queryForList("SELECT id FROM auditoria.registro_auditoria WHERE accion = "
                + "'CONFIGURAR_REGLA_CONSOLIDACION' AND id_entidad_afectada = ?", asignacionRosa).stream()
                .filter(r -> !previos.contains((String) r.get("id"))).toList();
        assertThat(nuevos).hasSize(1);
        return jdbc.queryForList("SELECT campo, valor_anterior, valor_nuevo FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ?",
                nuevos.get(0).get("id"));
    }

    private List<String> auditoriasPrevias() {
        return jdbc.queryForList("SELECT id FROM auditoria.registro_auditoria WHERE accion = 'CONFIGURAR_REGLA_CONSOLIDACION' "
                + "AND id_entidad_afectada = ?", String.class, asignacionRosa);
    }

    // ---- pasos 1 a 4 y FA-2: lo que se muestra ----

    @Test
    void sinHaberElegidoLaReglaEsElPromedioPonderadoConPesosIguales() throws Exception {
        Respuesta r = consultar(asignacionRosa, token(rosa));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("curso").asString()).isEqualTo("Matemática");
        assertThat(r.cuerpo().get("seccion").asString()).isEqualTo("3° A");
        assertThat(r.cuerpo().get("regla").asString()).isEqualTo("PROMEDIO_PONDERADO");
        assertThat(r.cuerpo().get("editable").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("pesos").size()).isEqualTo(4);
        for (int i = 0; i < 4; i++) {
            assertThat(r.cuerpo().get("pesos").get(i).get("bimestre").asInt()).isEqualTo(i + 1);
            assertThat(r.cuerpo().get("pesos").get(i).get("peso").asString()).isEqualTo("1.00");
            assertThat(r.cuerpo().get("pesos").get(i).get("personalizado").asBoolean()).isFalse();
        }
        assertThat(r.cuerpo().get("competencias").size()).isEqualTo(2);
    }

    @Test
    void lasTresReglasTraenSuEjemploCalculadoPorElMotor() throws Exception {
        Respuesta r = consultar(asignacionRosa, token(rosa));

        JsonNodeLista reglas = new JsonNodeLista(r);
        assertThat(reglas.tipos()).containsExactly("PROMEDIO_PONDERADO", "MODA", "ULTIMO_REGISTRO");
        // Bimestres I a IV: AD, AD, A, B -> promedio 3,25 = A ; moda AD ; último B
        assertThat(reglas.resultado(0)).isEqualTo("A");
        assertThat(reglas.resultado(1)).isEqualTo("AD");
        assertThat(reglas.resultado(2)).isEqualTo("B");
        assertThat(r.cuerpo().get("reglas").get(0).get("porDefecto").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("reglas").get(1).get("porDefecto").asBoolean()).isFalse();
        assertThat(r.cuerpo().get("reglas").get(0).get("ejemplo").get("niveles").size()).isEqualTo(4);
        assertThat(r.cuerpo().get("reglas").get(2).get("descripcion").asString()).contains("último bimestre");
    }

    /** Lectura cómoda de la lista de reglas de la respuesta. */
    private record JsonNodeLista(Respuesta r) {
        List<String> tipos() {
            List<String> l = new ArrayList<>();
            r.cuerpo().get("reglas").forEach(x -> l.add(x.get("tipo").asString()));
            return l;
        }

        String resultado(int i) {
            return r.cuerpo().get("reglas").get(i).get("ejemplo").get("resultado").asString();
        }
    }

    // ---- pasos 5 y 6, FA-1: elegir y cambiar la regla ----

    @Test
    void elDocenteEligeUnaReglaYQuedaGuardada() throws Exception {
        List<String> previos = auditoriasPrevias();

        Respuesta r = guardar(asignacionRosa, token(rosa), Map.of("regla", "MODA"));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("regla").asString()).isEqualTo("MODA");
        assertThat(r.cuerpo().get("cambios").asInt()).isEqualTo(1);
        assertThat(reglaGuardada(asignacionRosa)).isEqualTo("MODA");
        assertThat(consultar(asignacionRosa, token(rosa)).cuerpo().get("regla").asString()).isEqualTo("MODA");
        assertThat(cambiosDeLaUltimaAuditoria(previos)).singleElement().satisfies(c -> {
            assertThat(c.get("campo")).isEqualTo("regla");
            assertThat(c.get("valor_anterior")).isEqualTo("PROMEDIO_PONDERADO");
            assertThat(c.get("valor_nuevo")).isEqualTo("MODA");
        });
        // La otra asignación no se toca
        assertThat(reglaGuardada(asignacionLuis)).isEqualTo("PROMEDIO_PONDERADO");
    }

    @Test
    void fa1CambiarLaReglaYaConfiguradaLaReemplaza() throws Exception {
        guardar(asignacionRosa, token(rosa), Map.of("regla", "MODA"));
        List<String> previos = auditoriasPrevias();

        Respuesta r = guardar(asignacionRosa, token(rosa), Map.of("regla", "ULTIMO_REGISTRO"));

        assertThat(r.cuerpo().get("regla").asString()).isEqualTo("ULTIMO_REGISTRO");
        assertThat(reglaGuardada(asignacionRosa)).isEqualTo("ULTIMO_REGISTRO");
        assertThat(cambiosDeLaUltimaAuditoria(previos)).singleElement().satisfies(c -> {
            assertThat(c.get("valor_anterior")).isEqualTo("MODA");
            assertThat(c.get("valor_nuevo")).isEqualTo("ULTIMO_REGISTRO");
        });
        // Y se puede volver a la regla por defecto
        assertThat(guardar(asignacionRosa, token(rosa), Map.of("regla", "PROMEDIO_PONDERADO")).cuerpo().get("regla").asString())
                .isEqualTo("PROMEDIO_PONDERADO");
    }

    @Test
    void guardarLoMismoNoCambiaNadaNiSeAudita() throws Exception {
        int antes = auditorias();

        Respuesta r = guardar(asignacionRosa, token(rosa), Map.of("regla", "PROMEDIO_PONDERADO"));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("cambios").asInt()).isZero();
        assertThat(auditorias()).isEqualTo(antes);
    }

    // ---- RF-38: pesos por bimestre ----

    @Test
    void elDocenteAjustaElPesoDeLosBimestres() throws Exception {
        List<String> previos = auditoriasPrevias();

        Respuesta r = guardar(asignacionRosa, token(rosa), Map.of("regla", "PROMEDIO_PONDERADO",
                "pesos", List.of(peso(1, "1"), peso(2, "1"), peso(3, "2"), peso(4, "2.50"))));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("cambios").asInt()).isEqualTo(2);
        assertThat(r.cuerpo().get("pesos").get(2).get("peso").asString()).isEqualTo("2.00");
        assertThat(r.cuerpo().get("pesos").get(2).get("personalizado").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("pesos").get(3).get("peso").asString()).isEqualTo("2.50");
        assertThat(r.cuerpo().get("pesos").get(0).get("personalizado").asBoolean()).isFalse();
        // Solo se guardan los pesos distintos de 1
        assertThat(pesosGuardados(asignacionRosa)).containsOnlyKeys(3, 4).containsEntry(3, "2.00").containsEntry(4, "2.50");
        assertThat(cambiosDeLaUltimaAuditoria(previos)).extracting(c -> c.get("campo")).containsExactlyInAnyOrder("peso.bimestre3", "peso.bimestre4");
        assertThat(consultar(asignacionRosa, token(rosa)).cuerpo().get("pesos").get(3).get("peso").asString()).isEqualTo("2.50");
    }

    @Test
    void losPesosEnviadosReemplazanALosAnterioresYLosOmitidosVuelvenAUno() throws Exception {
        guardar(asignacionRosa, token(rosa), Map.of("regla", "PROMEDIO_PONDERADO", "pesos", List.of(peso(1, "3"), peso(4, "2"))));
        assertThat(pesosGuardados(asignacionRosa)).containsOnlyKeys(1, 4);

        Respuesta r = guardar(asignacionRosa, token(rosa), Map.of("regla", "PROMEDIO_PONDERADO", "pesos", List.of(peso(4, "5"))));

        assertThat(r.cuerpo().get("cambios").asInt()).isEqualTo(2);
        assertThat(pesosGuardados(asignacionRosa)).containsOnlyKeys(4).containsEntry(4, "5.00");
        assertThat(r.cuerpo().get("pesos").get(0).get("peso").asString()).isEqualTo("1.00");
        // Una lista vacía deja todos en 1
        guardar(asignacionRosa, token(rosa), Map.of("regla", "PROMEDIO_PONDERADO", "pesos", List.of()));
        assertThat(pesosGuardados(asignacionRosa)).isEmpty();
    }

    @Test
    void sinPesosEnLaSolicitudNoSeTocanLosPesos() throws Exception {
        guardar(asignacionRosa, token(rosa), Map.of("regla", "PROMEDIO_PONDERADO", "pesos", List.of(peso(2, "4"))));

        Respuesta r = guardar(asignacionRosa, token(rosa), Map.of("regla", "MODA"));

        assertThat(r.cuerpo().get("cambios").asInt()).isEqualTo(1);
        assertThat(pesosGuardados(asignacionRosa)).containsOnlyKeys(2).containsEntry(2, "4.00");
        assertThat(r.cuerpo().get("pesos").get(1).get("peso").asString()).isEqualTo("4.00");
    }

    @Test
    void unPesoInvalidoOUnaReglaDesconocidaSeRechazanYNoSeGuardaNada() throws Exception {
        Object[][] casos = {
                {Map.of("regla", "MODA", "pesos", List.of(peso(1, "0"))), "pesos.1"},
                {Map.of("regla", "MODA", "pesos", List.of(peso(1, "-2"))), "pesos.1"},
                {Map.of("regla", "MODA", "pesos", List.of(peso(2, "1.234"))), "pesos.2"},
                {Map.of("regla", "MODA", "pesos", List.of(peso(2, "100.01"))), "pesos.2"},
                {Map.of("regla", "MODA", "pesos", List.of(peso(3, "abc"))), "pesos.3"},
                {Map.of("regla", "MODA", "pesos", List.of(peso(3, ""))), "pesos.3"},
                {Map.of("regla", "MODA", "pesos", List.of(peso(9, "2"))), "pesos.9"},
                {Map.of("regla", "MODA", "pesos", List.of(peso(1, "2"), peso(1, "3"))), "pesos.1"},
                {Map.of("regla", "MEDIANA"), "regla"},
                {Map.of("regla", "moda"), "regla"},
        };
        for (Object[] caso : casos) {
            Respuesta r = guardar(asignacionRosa, token(rosa), caso[0]);

            assertThat(r.estado()).as("%s", caso[0]).isEqualTo(400);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
            assertThat(r.cuerpo().get("errores").get(0).get("campo").asString()).isEqualTo(caso[1]);
            assertThat(reglaGuardada(asignacionRosa)).as("la regla no cambió").isEqualTo("PROMEDIO_PONDERADO");
            assertThat(pesosGuardados(asignacionRosa)).isEmpty();
        }
    }

    @Test
    void unPesoInvalidoImpideTambienElCambioDeRegla() throws Exception {
        Respuesta r = guardar(asignacionRosa, token(rosa), Map.of("regla", "ULTIMO_REGISTRO", "pesos", List.of(peso(1, "2"), peso(2, "0"))));

        assertThat(r.estado()).isEqualTo(400);
        assertThat(reglaGuardada(asignacionRosa)).isEqualTo("PROMEDIO_PONDERADO");
        assertThat(pesosGuardados(asignacionRosa)).isEmpty();
    }

    @Test
    void sinReglaEnElCuerpoEsInvalido() throws Exception {
        Respuesta r = enviar("PUT", "/api/v1/asignaciones-docentes/" + asignacionRosa + "/configuracion", "{}", token(rosa), null);

        assertThat(r.estado()).isEqualTo(400);
    }

    @Test
    void unAnioCerradoYaNoSeConfigura() throws Exception {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'CERRADO' WHERE id = ?", e.anioId());

        Respuesta ver = consultar(asignacionRosa, token(rosa));
        Respuesta guardar = guardar(asignacionRosa, token(rosa), Map.of("regla", "MODA"));

        assertThat(ver.estado()).isEqualTo(200);
        assertThat(ver.cuerpo().get("editable").asBoolean()).isFalse();
        assertThat(guardar.estado()).isEqualTo(422);
        assertThat(guardar.cuerpo().get("codigo").asString()).isEqualTo("ANIO_CERRADO");
        assertThat(reglaGuardada(asignacionRosa)).isEqualTo("PROMEDIO_PONDERADO");
    }

    // ---- alcance y permisos ----

    @Test
    void soloElDocenteDeLaAsignacionVeYConfigura() throws Exception {
        Respuesta ver = consultar(asignacionRosa, token(luis));
        Respuesta guardar = guardar(asignacionRosa, token(luis), Map.of("regla", "MODA"));
        Respuesta inexistente = consultar("no-existe", token(rosa));
        Respuesta guardarInexistente = guardar("no-existe", token(rosa), Map.of("regla", "MODA"));

        for (Respuesta r : List.of(ver, guardar, inexistente, guardarInexistente)) {
            assertThat(r.estado()).isEqualTo(403);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
        }
        assertThat(reglaGuardada(asignacionRosa)).isEqualTo("PROMEDIO_PONDERADO");
        assertThat(guardar(asignacionLuis, token(luis), Map.of("regla", "MODA")).estado()).isEqualTo(200);
        assertThat(reglaGuardada(asignacionLuis)).isEqualTo("MODA");
    }

    @Test
    void sinElPermisoReglaDefinirNoSeAccede() throws Exception {
        for (String rol : List.of("ALUMNO", "APODERADO", "ADMINISTRADOR")) {
            String token = tokenDe(crearUsuario(rol, true));

            assertThat(consultar(asignacionRosa, token).cuerpo().get("codigo").asString()).isEqualTo("ACCESO_DENEGADO");
            assertThat(guardar(asignacionRosa, token, Map.of("regla", "MODA")).estado()).isEqualTo(403);
        }
        assertThat(get("/api/v1/asignaciones-docentes/" + asignacionRosa + "/configuracion", null).estado()).isEqualTo(401);
        assertThat(reglaGuardada(asignacionRosa)).isEqualTo("PROMEDIO_PONDERADO");
    }

    // ---- concurrencia ----

    @Test
    void seisGuardadosSimultaneosDejanUnaConfiguracionCoherente() throws Exception {
        String token = token(rosa);
        String[] reglas = {"MODA", "ULTIMO_REGISTRO", "PROMEDIO_PONDERADO"};
        ExecutorService hilos = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Respuesta>> tareas = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                String regla = reglas[i % reglas.length];
                String pesoBimestre = String.valueOf(2 + i);
                tareas.add(() -> guardar(asignacionRosa, token, Map.of("regla", regla, "pesos", List.of(peso(2, pesoBimestre)))));
            }
            for (Future<Respuesta> f : hilos.invokeAll(tareas)) {
                assertThat(f.get().estado()).isEqualTo(200);
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(reglaGuardada(asignacionRosa)).isIn((Object[]) reglas);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluacion.peso_bimestre WHERE asignacion_id = ?", Integer.class,
                asignacionRosa)).as("una sola fila para el bimestre II").isEqualTo(1);
    }
}
