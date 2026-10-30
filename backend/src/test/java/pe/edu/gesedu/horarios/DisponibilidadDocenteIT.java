package pe.edu.gesedu.horarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.apoyo.DatosHorario.Escenario;

/** UC-20: el docente registra su disponibilidad mientras el periodo está abierto (RF-46, RF-47). */
class DisponibilidadDocenteIT extends BaseIT {

    private record Cuenta(String correo, String docenteId) {
    }

    private Escenario e;
    private String admin;

    @BeforeEach
    void escenario() throws Exception {
        e = new DatosHorario(jdbc).crearEscenario();
        admin = tokenDe(crearUsuario("ADMINISTRADOR", true));
    }

    private String ruta() {
        return "/api/v1/anios-escolares/" + e.anio() + "/disponibilidad/mia";
    }

    private void periodo(boolean habilitado) throws Exception {
        assertThat(enviar("PUT", "/api/v1/anios-escolares/" + e.anio() + "/periodo-disponibilidad",
                "{\"fechaHoraInicio\":\"2020-01-04T08:00:00-05:00\",\"fechaHoraFin\":\"2099-01-15T23:59:00-05:00\",\"habilitado\":%s}"
                        .formatted(habilitado), admin, null).estado()).isEqualTo(200);
    }

    /** Un docente con cuenta, vinculado al año con Matemática y una carga de referencia. */
    private Cuenta docenteConCuenta(int carga) throws Exception {
        String correo = crearUsuario("DOCENTE", true);
        String docenteId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO seguridad.docente (id, usuario_id, codigo_docente) VALUES (?, ?, ?)", docenteId, idDe(correo),
                "D" + docenteId.substring(0, 8));
        assertThat(enviar("PUT", "/api/v1/anios-escolares/" + e.anio() + "/vinculaciones-docentes/" + docenteId,
                "{\"habilitado\":true,\"cargaSemanalReferencia\":%d,\"cursoIds\":[\"%s\"]}".formatted(carga, e.matematica()),
                admin, null).estado()).isEqualTo(200);
        return new Cuenta(correo, docenteId);
    }

    private static String bloques(String... ids) {
        return "[" + String.join(",", java.util.Arrays.stream(ids).map(i -> "\"" + i + "\"").toList()) + "]";
    }

    private Respuesta confirmar(String token, String bloquesJson, String observaciones) throws Exception {
        return enviar("PUT", ruta(), "{\"bloqueIds\":%s%s}".formatted(bloquesJson,
                observaciones == null ? "" : ",\"observaciones\":\"" + observaciones + "\""), token, null);
    }

    @Test
    void flujoBasicoLaMatrizVieneVaciaYAlConfirmarQuedaRegistrada() throws Exception {
        periodo(true);
        Cuenta d = docenteConCuenta(3);
        String token = tokenDe(d.correo());

        Respuesta antes = get(ruta(), token);
        assertThat(antes.estado()).isEqualTo(200);
        assertThat(antes.cuerpo().get("vinculado").asBoolean()).isTrue();
        assertThat(antes.cuerpo().get("periodo").get("abierto").asBoolean()).isTrue();
        assertThat(antes.cuerpo().get("bloques")).hasSize(3);
        assertThat(antes.cuerpo().get("bloqueIds")).isEmpty();
        assertThat(antes.cuerpo().get("ultimaConfirmacion").isNull()).isTrue();
        assertThat(antes.cuerpo().get("cargaSemanalReferencia").asInt()).isEqualTo(3);
        assertThat(antes.cuerpo().get("cursos").get(0).get("nombre").asString()).isEqualTo("Matemática");

        Respuesta r = confirmar(token, bloques(e.lunes745(), e.martes915()), "Los viernes dicto en otra institución");

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("bloqueIds")).hasSize(2);
        assertThat(r.cuerpo().get("observaciones").asString()).isEqualTo("Los viernes dicto en otra institución");
        assertThat(r.cuerpo().get("ultimaConfirmacion").asString()).endsWith("-05:00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.bloque_disponible bd JOIN horarios.disponibilidad_docente dd "
                + "ON dd.id = bd.disponibilidad_id JOIN horarios.vinculacion_docente v ON v.id = dd.vinculacion_id "
                + "WHERE v.docente_id = ?", Integer.class, d.docenteId())).isEqualTo(2);
        // Lo ve el administrador en la configuración
        var lista = get("/api/v1/anios-escolares/" + e.anio() + "/vinculaciones-docentes", admin).cuerpo().get("docentes");
        boolean visto = false;
        for (var fila : lista) {
            if (fila.get("docenteId").asString().equals(d.docenteId())) {
                visto = true;
                assertThat(fila.get("disponibilidadConfirmada").asBoolean()).isTrue();
                assertThat(fila.get("bloquesDisponibles").asInt()).isEqualTo(2);
            }
        }
        assertThat(visto).isTrue();
    }

    @Test
    void fa1ActualizarConservaSoloLaUltimaVersionConfirmada() throws Exception {
        periodo(true);
        Cuenta d = docenteConCuenta(1);
        String token = tokenDe(d.correo());
        confirmar(token, bloques(e.lunes745(), e.martes915()), "primera");
        java.time.Instant primera = jdbc.queryForObject("SELECT fecha_hora_confirmacion FROM horarios.disponibilidad_docente dd "
                + "JOIN horarios.vinculacion_docente v ON v.id = dd.vinculacion_id WHERE v.docente_id = ?",
                java.sql.Timestamp.class, d.docenteId()).toInstant();

        Respuesta r = confirmar(token, bloques(e.martes1015()), "segunda");

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("bloqueIds")).hasSize(1);
        assertThat(r.cuerpo().get("bloqueIds").get(0).asString()).isEqualTo(e.martes1015());
        assertThat(r.cuerpo().get("observaciones").asString()).isEqualTo("segunda");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.disponibilidad_docente dd JOIN horarios.vinculacion_docente v "
                + "ON v.id = dd.vinculacion_id WHERE v.docente_id = ?", Integer.class, d.docenteId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.bloque_disponible bd JOIN horarios.disponibilidad_docente dd "
                + "ON dd.id = bd.disponibilidad_id JOIN horarios.vinculacion_docente v ON v.id = dd.vinculacion_id "
                + "WHERE v.docente_id = ?", Integer.class, d.docenteId())).isEqualTo(1);
        java.time.Instant segunda = jdbc.queryForObject("SELECT fecha_hora_confirmacion FROM horarios.disponibilidad_docente dd "
                + "JOIN horarios.vinculacion_docente v ON v.id = dd.vinculacion_id WHERE v.docente_id = ?",
                java.sql.Timestamp.class, d.docenteId()).toInstant();
        assertThat(segunda).isAfterOrEqualTo(primera);
        // La auditoría guarda el antes y el después
        assertThat(jdbc.queryForList("SELECT c.valor_anterior || '→' || c.valor_nuevo FROM auditoria.cambio_auditoria c JOIN "
                + "auditoria.registro_auditoria r ON r.id = c.registro_auditoria_id WHERE r.accion = 'CONFIRMAR_DISPONIBILIDAD' "
                + "AND c.campo = 'bloques disponibles' AND r.usuario_id = ? ORDER BY r.fecha_hora", String.class, idDe(d.correo())))
                .containsExactly("0→2", "2→1");
    }

    @Test
    void fa2UnaDisponibilidadInsuficienteAdvierteYAunAsiSePuedeConfirmar() throws Exception {
        periodo(true);
        Cuenta d = docenteConCuenta(3);
        String token = tokenDe(d.correo());

        Respuesta poca = post(ruta() + "/validacion", "{\"bloqueIds\":%s}".formatted(bloques(e.lunes745())), token);
        assertThat(poca.estado()).isEqualTo(200);
        assertThat(poca.cuerpo().get("suficiente").asBoolean()).isFalse();
        assertThat(poca.cuerpo().get("faltan").asInt()).isEqualTo(2);
        assertThat(poca.cuerpo().get("bloquesMarcados").asInt()).isEqualTo(1);
        assertThat(poca.cuerpo().get("mensaje").asString())
                .isEqualTo("Tu disponibilidad podría ser insuficiente para tu carga horaria. Faltan al menos 2 bloques.");

        Respuesta suficiente = post(ruta() + "/validacion", "{\"bloqueIds\":%s}".formatted(bloques(e.lunes745(), e.martes915(), e.martes1015())), token);
        assertThat(suficiente.cuerpo().get("suficiente").asBoolean()).isTrue();
        assertThat(suficiente.cuerpo().get("mensaje").isNull()).isTrue();

        Respuesta uno = post(ruta() + "/validacion", "{\"bloqueIds\":%s}".formatted(bloques(e.lunes745(), e.martes915())), token);
        assertThat(uno.cuerpo().get("mensaje").asString()).endsWith("Faltan al menos 1 bloque.");

        // Validar no guarda nada; confirmar con poca disponibilidad sí se permite y devuelve la advertencia
        assertThat(get(ruta(), token).cuerpo().get("bloqueIds")).isEmpty();
        Respuesta confirmada = confirmar(token, bloques(e.lunes745()), null);
        assertThat(confirmada.estado()).isEqualTo(200);
        assertThat(confirmada.cuerpo().get("validacion").get("faltan").asInt()).isEqualTo(2);
    }

    @Test
    void fe1ConElPeriodoCerradoSeRechazaYSeConservaLaUltimaDisponibilidad() throws Exception {
        periodo(true);
        Cuenta d = docenteConCuenta(2);
        String token = tokenDe(d.correo());
        confirmar(token, bloques(e.lunes745(), e.martes915()), null);

        periodo(false);
        Respuesta r = confirmar(token, bloques(e.martes1015()), "tarde");

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("PERIODO_DISPONIBILIDAD_CERRADO");
        assertThat(r.cuerpo().get("detail").asString()).contains("ya no se permiten modificaciones");
        Respuesta vista = get(ruta(), token);
        assertThat(vista.cuerpo().get("periodo").get("abierto").asBoolean()).isFalse();
        assertThat(vista.cuerpo().get("bloqueIds")).hasSize(2);
        assertThat(vista.cuerpo().get("observaciones").isNull()).isTrue();
    }

    @Test
    void sinPeriodoConfiguradoElRegistroEstaCerrado() throws Exception {
        Cuenta d = docenteConCuenta(1);
        String token = tokenDe(d.correo());

        assertThat(get(ruta(), token).cuerpo().get("periodo").get("abierto").asBoolean()).isFalse();
        Respuesta r = confirmar(token, bloques(e.lunes745()), null);
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("PERIODO_DISPONIBILIDAD_CERRADO");
    }

    @Test
    void unPeriodoVencidoPorFechaTambienEstaCerrado() throws Exception {
        assertThat(enviar("PUT", "/api/v1/anios-escolares/" + e.anio() + "/periodo-disponibilidad",
                "{\"fechaHoraInicio\":\"2020-01-04T08:00:00-05:00\",\"fechaHoraFin\":\"2020-01-15T23:59:00-05:00\",\"habilitado\":true}",
                admin, null).estado()).isEqualTo(200);
        String token = tokenDe(docenteConCuenta(1).correo());

        assertThat(confirmar(token, bloques(e.lunes745()), null).cuerpo().get("codigo").asString())
                .isEqualTo("PERIODO_DISPONIBILIDAD_CERRADO");
    }

    @Test
    void losBloquesDebenSerDelAnioYLasObservacionesNoPasanDe500() throws Exception {
        periodo(true);
        String token = tokenDe(docenteConCuenta(1).correo());
        Escenario otro = new DatosHorario(jdbc).crearEscenario();

        assertThat(confirmar(token, bloques(otro.lunes745()), null).estado()).isEqualTo(400);
        assertThat(confirmar(token, bloques(e.lunes745()), "x".repeat(501)).estado()).isEqualTo(400);
        assertThat(post(ruta() + "/validacion", "{\"bloqueIds\":%s}".formatted(bloques(otro.lunes745())), token).estado()).isEqualTo(400);
        // Los repetidos cuentan una sola vez
        Respuesta repetidos = confirmar(token, bloques(e.lunes745(), e.lunes745()), null);
        assertThat(repetidos.estado()).isEqualTo(200);
        assertThat(repetidos.cuerpo().get("bloqueIds")).hasSize(1);
    }

    @Test
    void unDocenteNoVinculadoODeshabilitadoNoRegistra() throws Exception {
        periodo(true);
        String correo = crearUsuario("DOCENTE", true);
        String docenteId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO seguridad.docente (id, usuario_id, codigo_docente) VALUES (?, ?, ?)", docenteId, idDe(correo),
                "D" + docenteId.substring(0, 8));
        String token = tokenDe(correo);

        Respuesta vista = get(ruta(), token);
        assertThat(vista.estado()).isEqualTo(200);
        assertThat(vista.cuerpo().get("vinculado").asBoolean()).isFalse();
        Respuesta sinVinculo = confirmar(token, bloques(e.lunes745()), null);
        assertThat(sinVinculo.estado()).isEqualTo(422);
        assertThat(sinVinculo.cuerpo().get("codigo").asString()).isEqualTo("VINCULO_ACADEMICO_INEXISTENTE");

        assertThat(enviar("PUT", "/api/v1/anios-escolares/" + e.anio() + "/vinculaciones-docentes/" + docenteId,
                "{\"habilitado\":false,\"cargaSemanalReferencia\":1,\"cursoIds\":[]}", admin, null).estado()).isEqualTo(200);
        assertThat(confirmar(token, bloques(e.lunes745()), null).cuerpo().get("codigo").asString()).isEqualTo("VINCULO_ACADEMICO_INEXISTENTE");
        assertThat(get(ruta(), token).cuerpo().get("vinculado").asBoolean()).isFalse();
    }

    @Test
    void cadaDocenteVeSoloLoSuyoYSoloLosDocentesRegistran() throws Exception {
        periodo(true);
        Cuenta a = docenteConCuenta(1);
        Cuenta b = docenteConCuenta(1);
        confirmar(tokenDe(a.correo()), bloques(e.lunes745()), "de A");

        assertThat(get(ruta(), tokenDe(b.correo())).cuerpo().get("bloqueIds")).isEmpty();
        assertThat(get(ruta(), tokenDe(a.correo())).cuerpo().get("observaciones").asString()).isEqualTo("de A");
        // Una cuenta con rol docente pero sin perfil de docente no es un docente del año
        Respuesta sinPerfil = get(ruta(), tokenDe(crearUsuario("DOCENTE", true)));
        assertThat(sinPerfil.estado()).isEqualTo(403);
        assertThat(sinPerfil.cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
        // Administrador, alumno y apoderado no tienen el permiso
        for (String rol : new String[] {"ADMINISTRADOR", "ALUMNO", "APODERADO"}) {
            Respuesta r = get(ruta(), tokenDe(crearUsuario(rol, true)));
            assertThat(r.estado()).isEqualTo(403);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("ACCESO_DENEGADO");
        }
        assertThat(get(ruta(), null).estado()).isEqualTo(401);
    }

    @Test
    void seisConfirmacionesSimultaneasDelMismoDocenteDejanUnaSolaDisponibilidadCoherente() throws Exception {
        periodo(true);
        Cuenta d = docenteConCuenta(1);
        String token = tokenDe(d.correo());
        String[] conjuntos = {bloques(e.lunes745()), bloques(e.martes915()), bloques(e.lunes745(), e.martes915()),
                bloques(e.martes1015()), bloques(e.lunes745(), e.martes1015()), bloques(e.martes915(), e.martes1015())};
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(conjuntos.length);
        List<Integer> estados = new ArrayList<>();
        try {
            List<Future<Integer>> envios = new ArrayList<>();
            for (String conjunto : conjuntos) {
                envios.add(hilos.submit(() -> {
                    salida.await();
                    return confirmar(token, conjunto, null).estado();
                }));
            }
            salida.countDown();
            for (Future<Integer> f : envios) {
                estados.add(f.get());
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(estados).containsOnly(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.disponibilidad_docente dd JOIN horarios.vinculacion_docente v "
                + "ON v.id = dd.vinculacion_id WHERE v.docente_id = ?", Integer.class, d.docenteId())).isEqualTo(1);
        // Lo que queda es exactamente uno de los conjuntos enviados, sin mezclar filas de dos
        List<String> finales = jdbc.queryForList("SELECT bd.bloque_id FROM horarios.bloque_disponible bd JOIN horarios.disponibilidad_docente dd "
                + "ON dd.id = bd.disponibilidad_id JOIN horarios.vinculacion_docente v ON v.id = dd.vinculacion_id "
                + "WHERE v.docente_id = ? ORDER BY 1", String.class, d.docenteId());
        List<List<String>> posibles = new ArrayList<>();
        for (String conjunto : conjuntos) {
            posibles.add(java.util.Arrays.stream(conjunto.replaceAll("[\\[\\]\"]", "").split(",")).sorted().toList());
        }
        assertThat(posibles).contains(finales);
    }

    @Test
    void laDisponibilidadConfirmadaDecideQueDocentesSeOfrecenAlElaborarElHorario() throws Exception {
        periodo(true);
        Cuenta d = docenteConCuenta(1);
        // Antes de confirmar nada, el docente nuevo no aparece como disponible en el bloque
        String opciones = "/api/v1/horarios/" + e.anio() + "/borrador/opciones?bloqueId=" + e.martes1015() + "&seccionId=" + e.seccion3A()
                + "&cursoId=" + e.matematica();
        Respuesta antes = get(opciones, admin);
        assertThat(disponible(antes, d.docenteId())).isFalse();

        confirmar(tokenDe(d.correo()), bloques(e.martes1015()), null);

        assertThat(disponible(get(opciones, admin), d.docenteId())).isTrue();
    }

    private static boolean disponible(Respuesta opciones, String docenteId) {
        for (var o : opciones.cuerpo().get("docentes")) {
            if (o.get("docenteId").asString().equals(docenteId)) {
                return o.get("disponible").asBoolean();
            }
        }
        throw new AssertionError("el docente no se ofrece para el curso");
    }
}
