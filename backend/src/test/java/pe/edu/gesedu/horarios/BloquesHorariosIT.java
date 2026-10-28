package pe.edu.gesedu.horarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;

/** Bloques horarios (RF-46): lectura para todos los roles, creación solo con HORARIO_CONFIGURAR y auditada. */
class BloquesHorariosIT extends BaseIT {

    private static final int ANIO = 2088;
    private static final String RUTA = "/api/v1/anios-escolares/" + ANIO + "/bloques";

    @BeforeEach
    void anioSinBloques() {
        jdbc.update("DELETE FROM horarios.bloque_horario WHERE anio_escolar_id IN "
                + "(SELECT id FROM academico.anio_escolar WHERE anio = ?)", ANIO);
        jdbc.update("INSERT INTO academico.anio_escolar (id, anio, fecha_inicio, fecha_fin, estado) "
                + "VALUES (?, ?, DATE '2088-02-01', DATE '2088-12-20', 'PLANIFICADO') ON CONFLICT (anio) DO NOTHING",
                java.util.UUID.randomUUID().toString(), ANIO);
    }

    private static String cuerpo(String dia, String inicio, String fin) {
        return "{\"diaSemana\":\"" + dia + "\",\"horaInicio\":\"" + inicio + "\",\"horaFin\":\"" + fin + "\"}";
    }

    @Test
    void elAdministradorCreaUnBloqueYSeAudita() throws Exception {
        String correo = crearUsuario("ADMINISTRADOR", true);

        Respuesta r = post(RUTA, cuerpo("LUNES", "07:45", "08:30"), tokenDe(correo));

        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("diaSemana").asString()).isEqualTo("LUNES");
        assertThat(r.cuerpo().get("horaInicio").asString()).isEqualTo("07:45");
        assertThat(r.cuerpo().get("horaFin").asString()).isEqualTo("08:30");

        List<Map<String, Object>> registros = jdbc.queryForList(
                "SELECT * FROM auditoria.registro_auditoria WHERE id_entidad_afectada = ?",
                r.cuerpo().get("id").asString());
        assertThat(registros).hasSize(1);
        assertThat(registros.getFirst()).containsEntry("accion", "CREAR_BLOQUE_HORARIO")
                .containsEntry("modulo", "HORARIOS").containsEntry("tipo_entidad", "BloqueHorario")
                .containsEntry("usuario_id", idDe(correo));
        assertThat(jdbc.queryForList("SELECT campo FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ?",
                String.class, registros.getFirst().get("id"))).containsExactlyInAnyOrder("anio", "diaSemana",
                "horaInicio", "horaFin");
    }

    @Test
    void lasHorasSeGuardanYSeLeenTalCualSinDesplazamientoDeZona() throws Exception {
        String admin = tokenDe(crearUsuario("ADMINISTRADOR", true));

        // Lo que escribe la API queda en la base con la misma hora…
        post(RUTA, cuerpo("LUNES", "07:45", "08:30"), admin);
        assertThat(jdbc.queryForObject("SELECT hora_inicio::text || '-' || hora_fin::text FROM horarios.bloque_horario "
                + "WHERE dia_semana = 'LUNES'", String.class)).isEqualTo("07:45:00-08:30:00");

        // …y lo que se siembra con SQL (como db/demo) se lee con la misma hora
        jdbc.update("INSERT INTO horarios.bloque_horario (id, anio_escolar_id, dia_semana, hora_inicio, hora_fin) "
                + "SELECT ?, id, 'MARTES', TIME '12:30', TIME '13:15' FROM academico.anio_escolar WHERE anio = ?",
                java.util.UUID.randomUUID().toString(), ANIO);
        Respuesta r = get(RUTA, admin);
        assertThat(r.cuerpo().get(1).get("horaInicio").asString()).isEqualTo("12:30");
        assertThat(r.cuerpo().get(1).get("horaFin").asString()).isEqualTo("13:15");
    }

    @Test
    void cualquierRolConsultaLosBloquesOrdenadosPorDiaYHora() throws Exception {
        String admin = tokenDe(crearUsuario("ADMINISTRADOR", true));
        post(RUTA, cuerpo("MARTES", "08:30", "09:15"), admin);
        post(RUTA, cuerpo("LUNES", "08:30", "09:15"), admin);
        post(RUTA, cuerpo("LUNES", "07:45", "08:30"), admin);

        for (String rol : List.of("ALUMNO", "APODERADO", "DOCENTE", "ADMINISTRADOR")) {
            Respuesta r = get(RUTA, tokenDe(crearUsuario(rol, true)));
            assertThat(r.estado()).as(rol).isEqualTo(200);
            assertThat(r.cuerpo().valueStream().map(b -> b.get("diaSemana").asString() + " "
                    + b.get("horaInicio").asString()).toList())
                    .containsExactly("LUNES 07:45", "LUNES 08:30", "MARTES 08:30");
        }
    }

    @Test
    void soloQuienTieneHorarioConfigurarPuedeCrear() throws Exception {
        for (String rol : List.of("ALUMNO", "APODERADO", "DOCENTE")) {
            Respuesta r = post(RUTA, cuerpo("LUNES", "07:45", "08:30"), tokenDe(crearUsuario(rol, true)));
            assertThat(r.estado()).as(rol).isEqualTo(403);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("ACCESO_DENEGADO");
        }
        assertThat(post(RUTA, cuerpo("LUNES", "07:45", "08:30"), null).estado()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.bloque_horario", Integer.class)).isZero();
    }

    @Test
    void unBloqueQueSeCruzaResponde409ConLosConflictosYNoSeAudita() throws Exception {
        String admin = tokenDe(crearUsuario("ADMINISTRADOR", true));
        Respuesta existente = post(RUTA, cuerpo("LUNES", "07:45", "08:30"), admin);
        long auditoriasAntes = jdbc.queryForObject(
                "SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'CREAR_BLOQUE_HORARIO'", Long.class);

        Respuesta r = post(RUTA, cuerpo("LUNES", "08:00", "08:45"), admin);

        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("CONFLICTO_HORARIO");
        assertThat(r.cuerpo().get("conflictos").get(0).get("id").asString())
                .isEqualTo(existente.cuerpo().get("id").asString());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'CREAR_BLOQUE_HORARIO'", Long.class))
                .isEqualTo(auditoriasAntes);
    }

    @Test
    void dosBloquesQueSoloSeTocanEnElBordeConvivenYOtroDiaNoCruza() throws Exception {
        String admin = tokenDe(crearUsuario("ADMINISTRADOR", true));
        assertThat(post(RUTA, cuerpo("LUNES", "07:45", "08:30"), admin).estado()).isEqualTo(201);
        assertThat(post(RUTA, cuerpo("LUNES", "08:30", "09:15"), admin).estado()).isEqualTo(201);
        assertThat(post(RUTA, cuerpo("MARTES", "07:45", "08:30"), admin).estado()).isEqualTo(201);
    }

    @Test
    void laValidacionRechazaHorasInvertidasDiasInvalidosYCamposFaltantes() throws Exception {
        String admin = tokenDe(crearUsuario("ADMINISTRADOR", true));

        Respuesta invertidas = post(RUTA, cuerpo("LUNES", "09:00", "08:00"), admin);
        assertThat(invertidas.estado()).isEqualTo(400);
        assertThat(invertidas.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
        assertThat(invertidas.cuerpo().get("errores").get(0).get("mensaje").asString()).contains("posterior");

        assertThat(post(RUTA, cuerpo("FUNDADOR", "07:45", "08:30"), admin).estado()).isEqualTo(400);
        assertThat(post(RUTA, "{\"diaSemana\":\"LUNES\"}", admin).cuerpo().get("errores").size()).isEqualTo(2);
        assertThat(post(RUTA, cuerpo("LUNES", "7 y media", "08:30"), admin).estado()).isEqualTo(400);
    }

    @Test
    void unAnioInexistenteResponde404() throws Exception {
        String admin = tokenDe(crearUsuario("ADMINISTRADOR", true));

        Respuesta r = get("/api/v1/anios-escolares/1999/bloques", admin);

        assertThat(r.estado()).isEqualTo(404);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("RECURSO_NO_ENCONTRADO");
        assertThat(post("/api/v1/anios-escolares/1999/bloques", cuerpo("LUNES", "07:45", "08:30"), admin).estado())
                .isEqualTo(404);
    }
}
