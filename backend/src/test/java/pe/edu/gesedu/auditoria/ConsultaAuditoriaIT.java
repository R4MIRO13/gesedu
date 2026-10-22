package pe.edu.gesedu.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;

/** Consulta de auditoría (UC-25, RF-51): filtros, paginación, orden y permiso AUDITORIA_CONSULTAR. */
class ConsultaAuditoriaIT extends BaseIT {

    private static final String RUTA = "/api/v1/auditoria/registros";

    private void insertar(String modulo, String accion, String usuarioId, String fecha) {
        jdbc.update("INSERT INTO auditoria.registro_auditoria (id, fecha_hora, usuario_id, accion, modulo, "
                        + "tipo_entidad, id_entidad_afectada, descripcion) "
                        + "VALUES (?, ?::timestamptz, ?, ?, ?, 'Cosa', ?, 'prueba')",
                UUID.randomUUID().toString(), fecha, usuarioId, accion, modulo, accion);
    }

    private static String moduloUnico(String prefijo) {
        return prefijo + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Test
    void soloQuienTieneAuditoriaConsultarAccede() throws Exception {
        for (String rol : List.of("ALUMNO", "APODERADO", "DOCENTE")) {
            Respuesta r = get(RUTA, tokenDe(crearUsuario(rol, true)));
            assertThat(r.estado()).as(rol).isEqualTo(403);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("ACCESO_DENEGADO");
        }
        assertThat(get(RUTA, null).estado()).isEqualTo(401);
        assertThat(get(RUTA, tokenDe(crearUsuario("ADMINISTRADOR", true))).estado()).isEqualTo(200);
    }

    @Test
    void filtraPorModuloAccionYUsuarioConNombreOSistema() throws Exception {
        String modulo = moduloUnico("MOD");
        String correo = crearUsuario("ADMINISTRADOR", true);
        insertar(modulo, "ACCION_A", idDe(correo), "2031-05-10T10:00:00-05:00");
        insertar(modulo, "ACCION_B", null, "2031-05-11T10:00:00-05:00");
        String token = tokenDe(correo);

        Respuesta todos = get(RUTA + "?modulo=" + modulo, token);
        assertThat(todos.cuerpo().get("totalElementos").asInt()).isEqualTo(2);
        assertThat(todos.cuerpo().get("contenido").get(0).get("accion").asString()).as("más reciente primero")
                .isEqualTo("ACCION_B");
        assertThat(todos.cuerpo().get("contenido").get(0).get("usuario").isNull()).as("Sistema").isTrue();
        assertThat(todos.cuerpo().get("contenido").get(1).get("usuario").asString()).isEqualTo("Prueba Usuario");
        assertThat(todos.cuerpo().get("contenido").get(1).get("fechaHora").asString()).endsWith("-05:00");

        assertThat(get(RUTA + "?modulo=" + modulo + "&accion=ACCION_A", token).cuerpo().get("totalElementos").asInt())
                .isEqualTo(1);
        assertThat(get(RUTA + "?modulo=" + modulo + "&usuarioId=" + idDe(correo), token).cuerpo()
                .get("contenido").get(0).get("accion").asString()).isEqualTo("ACCION_A");
    }

    @Test
    void filtraPorRangoDeFechasInclusivoEnLaZonaInstitucional() throws Exception {
        String modulo = moduloUnico("FEC");
        // 23:30 en Lima del 10/05 ya es el 11/05 en UTC: el filtro debe contar el día de Lima
        insertar(modulo, "NOCHE_10", null, "2032-05-10T23:30:00-05:00");
        insertar(modulo, "MANANA_11", null, "2032-05-11T08:00:00-05:00");
        insertar(modulo, "DIA_12", null, "2032-05-12T08:00:00-05:00");
        String token = tokenDe(crearUsuario("ADMINISTRADOR", true));
        String base = RUTA + "?modulo=" + modulo;

        assertThat(acciones(get(base + "&desde=2032-05-11&hasta=2032-05-11", token))).containsExactly("MANANA_11");
        assertThat(acciones(get(base + "&desde=2032-05-10&hasta=2032-05-11&sort=fechaHora,asc", token)))
                .containsExactly("NOCHE_10", "MANANA_11");
        assertThat(acciones(get(base + "&desde=2032-05-12", token))).containsExactly("DIA_12");
        assertThat(acciones(get(base + "&hasta=2032-05-10", token))).containsExactly("NOCHE_10");
        assertThat(get(base + "&desde=2032-05-12&hasta=2032-05-10", token).estado()).isEqualTo(400);
        assertThat(get(base + "&desde=ayer", token).estado()).isEqualTo(400);
    }

    @Test
    void paginaYOrdenaConListaBlanca() throws Exception {
        String modulo = moduloUnico("PAG");
        for (int i = 1; i <= 5; i++) {
            insertar(modulo, "A" + i, null, "2033-01-0" + i + "T10:00:00-05:00");
        }
        String token = tokenDe(crearUsuario("ADMINISTRADOR", true));
        String base = RUTA + "?modulo=" + modulo + "&size=2&sort=accion,asc";

        Respuesta p0 = get(base + "&page=0", token);
        assertThat(p0.cuerpo().get("pagina").asInt()).isZero();
        assertThat(p0.cuerpo().get("tamano").asInt()).isEqualTo(2);
        assertThat(p0.cuerpo().get("totalElementos").asInt()).isEqualTo(5);
        assertThat(p0.cuerpo().get("totalPaginas").asInt()).isEqualTo(3);
        assertThat(acciones(p0)).containsExactly("A1", "A2");
        assertThat(acciones(get(base + "&page=2", token))).containsExactly("A5");

        for (String malo : List.of("&size=101", "&size=0", "&page=-1", "&sort=descripcion", "&sort=accion,sideways",
                "&sort=usuarioId")) {
            Respuesta r = get(RUTA + "?modulo=" + modulo + malo, token);
            assertThat(r.estado()).as(malo).isEqualTo(400);
            assertThat(r.cuerpo().get("codigo").asString()).as(malo).isEqualTo("VALIDACION_INVALIDA");
        }
    }

    private static List<String> acciones(Respuesta r) {
        return r.cuerpo().get("contenido").valueStream().map(x -> x.get("accion").asString()).toList();
    }
}
