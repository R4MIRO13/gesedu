package pe.edu.gesedu.pagos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;
import pe.edu.gesedu.apoyo.DatosMatricula.Solicitud;
import pe.edu.gesedu.matricula.BaseMatriculaIT;

/** UC-06, Consultar historial de pagos (RF-10, RF-11): flujo básico, FA-1, FA-2, solo lectura y permisos. */
class HistorialFinancieroIT extends BaseMatriculaIT {

    private String codigoDe(String alumnoId) {
        return jdbc.queryForObject("SELECT codigo_alumno FROM seguridad.alumno WHERE id = ?", String.class, alumnoId);
    }

    private Respuesta historial(String alumnoId, String token) throws Exception {
        return get("/api/v1/alumnos/" + codigoDe(alumnoId) + "/historial-financiero", token);
    }

    private void pagar(String obligacionId, String apoderadoId, String operacion, String boleta, String monto) {
        String comprobanteId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO pagos.comprobante_pago (id, obligacion_id, apoderado_id, numero_operacion, fecha_pago, "
                + "monto_presentado, medio_pago, referencia_comprobante, estado_revision) "
                + "VALUES (?, ?, ?, ?, DATE '2027-03-10', ?::numeric, 'Transferencia - BCP', 'comprobantes/prueba.pdf', 'APROBADO')",
                comprobanteId, obligacionId, apoderadoId, operacion, monto);
        jdbc.update("INSERT INTO pagos.registro_pago (id, comprobante_id, obligacion_id, numero_boleta, fecha_registro, "
                + "monto_total) VALUES (?, ?, ?, ?, now(), ?::numeric)", UUID.randomUUID().toString(), comprobanteId,
                obligacionId, boleta, monto);
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'PAGADA' WHERE id = ?", obligacionId);
    }

    @Test
    void flujoBasicoMuestraResumenObligacionesBoletasYDeudasConSuPenalidad() throws Exception {
        Familia f = crearFamilia();
        pagar(datos.obligacion(f.matriculaId(), String.valueOf(f.escenario().anio())), f.apoderado().apoderadoId(), "HF-0001", "B001-90001", "350.00");
        pagar(f.pension(datos, 3), f.apoderado().apoderadoId(), "HF-0002", "B001-90002", "350.00");
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'VENCIDA', monto_penalidad = 17.50, penalidad_aplicada_en = now() "
                + "WHERE id = ?", f.pension(datos, 4));

        Respuesta r = historial(f.alumnoId(), tokenAdministrador());

        assertThat(r.estado()).isEqualTo(200);
        var h = r.cuerpo();
        assertThat(h.get("estudiante").get("codigo").asString()).isEqualTo(codigoDe(f.alumnoId()));
        assertThat(h.get("estudiante").get("nombre").asString()).startsWith("Lucía");
        assertThat(h.get("estudiante").get("apoderado").asString()).isEqualTo("Prueba Usuario");
        assertThat(h.get("matricula").get("grado").asString()).isEqualTo("3° de secundaria");
        assertThat(h.get("matricula").get("seccion").asString()).isEqualTo("3° A");
        assertThat(h.get("matricula").get("anio").asInt()).isEqualTo(f.escenario().anio());
        assertThat(h.get("matricula").get("estado").asString()).isEqualTo("Confirmada");

        var resumen = h.get("resumen");
        assertThat(resumen.get("totalPagado").asString()).isEqualTo("700.00");
        assertThat(resumen.get("deudaVencida").asString()).isEqualTo("367.50");
        assertThat(resumen.get("totalPendiente").asString()).isEqualTo("2800.00");
        assertThat(resumen.get("penalidadesAplicadas").asString()).isEqualTo("17.50");
        assertThat(resumen.get("obligaciones").asInt()).isEqualTo(11);
        assertThat(resumen.get("pagadas").asInt()).isEqualTo(2);
        assertThat(resumen.get("vencidas").asInt()).isEqualTo(1);
        assertThat(resumen.get("pendientes").asInt()).isEqualTo(8);

        assertThat(h.get("obligaciones")).hasSize(11);
        assertThat(h.get("obligaciones").get(1).get("boleta").get("numero").asString()).isEqualTo("B001-90002");
        assertThat(h.get("pagos")).hasSize(2);
        var pago = h.get("pagos").get(0);
        assertThat(pago.get("numeroBoleta").asString()).startsWith("B001-9000");
        assertThat(pago.get("numeroOperacion").asString()).startsWith("HF-000");
        assertThat(pago.get("medioPago").asString()).isEqualTo("Transferencia - BCP");
        assertThat(pago.get("fechaPago").asString()).isEqualTo("2027-03-10");
        assertThat(h.get("deudas")).hasSize(1);
        assertThat(h.get("deudas").get(0).get("montoBase").asString()).isEqualTo("350.00");
        assertThat(h.get("deudas").get(0).get("montoPenalidad").asString()).isEqualTo("17.50");
        assertThat(h.get("deudas").get(0).get("total").asString()).isEqualTo("367.50");
        assertThat(h.get("sinHistorial").asBoolean()).isFalse();
        assertThat(h.get("sinPagos").asBoolean()).isFalse();
    }

    @Test
    void fa1UnEstudianteSinObligacionesNiPagosLoInformaSinHistorial() throws Exception {
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String alumno = datos.alumnoDe(ap.apoderadoId(), "Andrés");

        var h = historial(alumno, tokenAdministrador()).cuerpo();

        assertThat(h.get("sinHistorial").asBoolean()).isTrue();
        assertThat(h.get("obligaciones")).isEmpty();
        assertThat(h.get("pagos")).isEmpty();
        assertThat(h.get("matricula").isNull()).isTrue();
        assertThat(h.get("resumen").get("totalPagado").asString()).isEqualTo("0.00");
    }

    @Test
    void fa2ConObligacionesPeroSinPagosSeñalaLasPendientesYVencidas() throws Exception {
        Familia f = crearFamilia();
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'VENCIDA' WHERE id = ?", f.pension(datos, 3));

        var h = historial(f.alumnoId(), tokenAdministrador()).cuerpo();

        assertThat(h.get("sinHistorial").asBoolean()).isFalse();
        assertThat(h.get("sinPagos").asBoolean()).isTrue();
        assertThat(h.get("pagos")).isEmpty();
        assertThat(h.get("obligaciones")).hasSize(11);
        assertThat(h.get("deudas")).hasSize(1);
    }

    @Test
    void elHistorialIncluyeLasDeudasDeAniosAnteriores() throws Exception {
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, false);
        datos.deudaDelAnioAnterior(e, s);

        var h = historial(s.alumnoId(), tokenAdministrador()).cuerpo();

        assertThat(h.get("obligaciones")).hasSize(1);
        assertThat(h.get("obligaciones").get(0).get("periodo").asString()).isEqualTo("2026-03");
        assertThat(h.get("deudas")).hasSize(1);
        assertThat(h.get("resumen").get("deudaVencida").asString()).isEqualTo("350.00");
    }

    @Test
    void laConsultaNoModificaObligacionesPagosNiBoletas() throws Exception {
        Familia f = crearFamilia();
        pagar(f.pension(datos, 3), f.apoderado().apoderadoId(), "HF-0003", "B001-90003", "350.00");
        String foto = "SELECT (SELECT count(*) FROM pagos.obligacion_pago) || '/' || (SELECT count(*) FROM pagos.comprobante_pago) "
                + "|| '/' || (SELECT count(*) FROM pagos.registro_pago) || '/' "
                + "|| (SELECT max(updated_at) FROM pagos.obligacion_pago) || '/' "
                + "|| (SELECT max(updated_at) FROM pagos.comprobante_pago) || '/' "
                + "|| (SELECT count(*) FROM auditoria.registro_auditoria)";
        String antes = jdbc.queryForObject(foto, String.class);

        assertThat(historial(f.alumnoId(), tokenAdministrador()).estado()).isEqualTo(200);
        assertThat(historial(f.alumnoId(), tokenAdministrador()).estado()).isEqualTo(200);

        assertThat(jdbc.queryForObject(foto, String.class)).isEqualTo(antes);
    }

    @Test
    void laBusquedaEncuentraEstudiantesPorNombreOCodigo() throws Exception {
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String marca = "Zz" + UUID.randomUUID().toString().substring(0, 6);
        String alumno = datos.alumnoDe(ap.apoderadoId(), marca);
        String admin = tokenAdministrador();

        Respuesta porNombre = get("/api/v1/alumnos/busqueda?q=" + marca.toLowerCase(), admin);
        Respuesta porCodigo = get("/api/v1/alumnos/busqueda?q=" + codigoDe(alumno), admin);
        Respuesta vacia = get("/api/v1/alumnos/busqueda?q=%20", admin);

        assertThat(porNombre.estado()).isEqualTo(200);
        assertThat(porNombre.cuerpo()).hasSize(1);
        assertThat(porNombre.cuerpo().get(0).get("codigo").asString()).isEqualTo(codigoDe(alumno));
        assertThat(porNombre.cuerpo().get(0).get("nombre").asString()).startsWith(marca);
        assertThat(porCodigo.cuerpo().get(0).get("nombre").asString()).startsWith(marca);
        assertThat(vacia.cuerpo()).isEmpty();
    }

    @Test
    void soloQuienTienePermisoConsultaElHistorial() throws Exception {
        Familia f = crearFamilia();
        String codigo = codigoDe(f.alumnoId());

        for (String token : List.of(f.apoderado().token(), tokenDe(crearUsuario("DOCENTE", true)), tokenDe(crearUsuario("ALUMNO", true)))) {
            assertThat(get("/api/v1/alumnos/" + codigo + "/historial-financiero", token).estado()).isEqualTo(403);
            assertThat(get("/api/v1/alumnos/busqueda?q=a", token).estado()).isEqualTo(403);
        }
        assertThat(get("/api/v1/alumnos/" + codigo + "/historial-financiero", null).estado()).isEqualTo(401);
        assertThat(get("/api/v1/alumnos/NO-EXISTE/historial-financiero", tokenAdministrador()).estado()).isEqualTo(404);
    }
}
