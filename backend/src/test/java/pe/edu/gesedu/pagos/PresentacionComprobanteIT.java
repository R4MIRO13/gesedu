package pe.edu.gesedu.pagos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.matricula.BaseMatriculaIT;

/** UC-04, Presentar comprobante de pago (RF-08): flujo básico, FA-1, FA-2, alcance y validaciones. */
class PresentacionComprobanteIT extends BaseMatriculaIT {

    private String estadoDe(String obligacionId) {
        return jdbc.queryForObject("SELECT estado FROM pagos.obligacion_pago WHERE id = ?", String.class, obligacionId);
    }

    @Test
    void flujoBasicoRegistraElComprobantePendienteSinCambiarLaObligacion() throws Exception {
        Familia f = crearFamilia();
        String septiembre = f.pension(datos, 9);

        Respuesta r = presentarComprobante(f.apoderado().token(), septiembre, comprobante(f.apoderado().token(), septiembre, "00458712", "350.00"));

        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("PENDIENTE");
        assertThat(r.cuerpo().get("numeroOperacion").asString()).isEqualTo("00458712");
        assertThat(r.cuerpo().get("montoPresentado").asString()).isEqualTo("350.00");
        // Postcondición: la obligación no cambia hasta que el administrador valide
        assertThat(estadoDe(septiembre)).isEqualTo("PENDIENTE");
        assertThat(contar("SELECT count(*) FROM pagos.comprobante_pago WHERE obligacion_id = ? "
                + "AND estado_revision = 'PENDIENTE' AND apoderado_id = ?", septiembre, f.apoderado().apoderadoId())).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'PRESENTAR_COMPROBANTE' "
                + "AND id_entidad_afectada = ?", r.cuerpo().get("id").asString())).isEqualTo(1);
        // La consulta del apoderado ya lo muestra en revisión y no deja presentar otro
        var fila = get("/api/v1/alumnos/" + f.alumnoId() + "/obligaciones", f.apoderado().token()).cuerpo()
                .get("obligaciones").get(7);
        assertThat(fila.get("periodo").asString()).isEqualTo(f.escenario().anio() + "-09");
        assertThat(fila.get("comprobante").get("estado").asString()).isEqualTo("PENDIENTE");
        assertThat(fila.get("puedePresentarComprobante").asBoolean()).isFalse();
    }

    @Test
    void unaObligacionVencidaTambienAceptaComprobante() throws Exception {
        Familia f = crearFamilia();
        String agosto = f.pension(datos, 8);
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'VENCIDA', monto_penalidad = 17.50 WHERE id = ?", agosto);

        Respuesta r = presentarComprobante(f.apoderado().token(), agosto, comprobante(f.apoderado().token(), agosto, "00458799", "350.00"));

        assertThat(r.estado()).isEqualTo(201);
        assertThat(estadoDe(agosto)).isEqualTo("VENCIDA");
    }

    @Test
    void fa1FaltanDatosDelComprobanteYElErrorNombraCadaCampo() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        Map<String, Object> cuerpo = comprobante(f.apoderado().token(), marzo, "00000111", "350.00");
        cuerpo.put("numeroOperacion", "");
        cuerpo.remove("fechaPago");
        cuerpo.put("montoPagado", "abc");
        cuerpo.put("medioPago", " ");

        Respuesta r = presentarComprobante(f.apoderado().token(), marzo, cuerpo);

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
        List<String> campos = new ArrayList<>();
        r.cuerpo().get("errores").forEach(x -> campos.add(x.get("campo").asString()));
        assertThat(campos).contains("numeroOperacion", "fechaPago", "montoPagado", "medioPago");
        assertThat(contar("SELECT count(*) FROM pagos.comprobante_pago WHERE obligacion_id = ?", marzo)).isZero();
    }

    @Test
    void laFechaDePagoNoPuedeSerFuturaNiElMontoCero() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        Map<String, Object> futura = comprobante(f.apoderado().token(), marzo, "00000121", "350.00");
        futura.put("fechaPago", java.time.LocalDate.now().plusDays(1).toString());
        Map<String, Object> cero = comprobante(f.apoderado().token(), marzo, "00000122", "0.00");

        assertThat(presentarComprobante(f.apoderado().token(), marzo, futura).cuerpo().get("detail").asString())
                .contains("fecha de pago no puede ser futura");
        assertThat(presentarComprobante(f.apoderado().token(), marzo, cero).cuerpo().get("detail").asString())
                .contains("mayor que cero");
    }

    @Test
    void fa2UnaObligacionYaPagadaNoRecibeOtroComprobante() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        Map<String, Object> cuerpo = comprobante(f.apoderado().token(), marzo, "00000131", "350.00");
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'PAGADA' WHERE id = ?", marzo);

        Respuesta r = presentarComprobante(f.apoderado().token(), marzo, cuerpo);

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("OBLIGACION_YA_PAGADA");
        assertThat(contar("SELECT count(*) FROM pagos.comprobante_pago WHERE obligacion_id = ?", marzo)).isZero();
    }

    @Test
    void conUnComprobantePendienteNoSePuedePresentarOtro() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        presentarComprobante(f.apoderado().token(), marzo, comprobante(f.apoderado().token(), marzo, "00000141", "350.00"));

        Respuesta r = presentarComprobante(f.apoderado().token(), marzo, comprobante(f.apoderado().token(), marzo, "00000142", "350.00"));

        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("COMPROBANTE_PENDIENTE");
    }

    @Test
    void unComprobanteObservadoSePuedeReemplazarConUnoNuevo() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        presentarComprobante(f.apoderado().token(), marzo, comprobante(f.apoderado().token(), marzo, "00000151", "300.00"));
        jdbc.update("UPDATE pagos.comprobante_pago SET estado_revision = 'OBSERVADO', observacion = 'Monto insuficiente' "
                + "WHERE obligacion_id = ?", marzo);

        Respuesta r = presentarComprobante(f.apoderado().token(), marzo, comprobante(f.apoderado().token(), marzo, "00000152", "350.00"));

        assertThat(r.estado()).isEqualTo(201);
        assertThat(contar("SELECT count(*) FROM pagos.comprobante_pago WHERE obligacion_id = ?", marzo)).isEqualTo(2);
    }

    @Test
    void unApoderadoNoPresentaComprobantesDeLasObligacionesDeOtro() throws Exception {
        Familia f = crearFamilia();
        ApoderadoConCuenta otro = crearApoderadoConCuenta();
        String marzo = f.pension(datos, 3);
        Map<String, Object> cuerpo = comprobante(f.apoderado().token(), marzo, "00000161", "350.00");

        Respuesta r = presentarComprobante(otro.token(), marzo, cuerpo);
        Respuesta subida = post("/api/v1/obligaciones/" + marzo + "/comprobantes/archivo", json.writeValueAsString(
                Map.of("nombreArchivo", "v.pdf", "contentType", "application/pdf", "tamanoBytes", 10)), otro.token());
        Respuesta inexistente = presentarComprobante(otro.token(), "00000000-0000-0000-0000-000000000000", cuerpo);

        assertThat(r.estado()).isEqualTo(403);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
        assertThat(subida.estado()).isEqualTo(403);
        assertThat(inexistente.estado()).isEqualTo(403);
        assertThat(contar("SELECT count(*) FROM pagos.comprobante_pago WHERE obligacion_id = ?", marzo)).isZero();
    }

    @Test
    void soloElApoderadoPresentaComprobantes() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        Map<String, Object> cuerpo = comprobante(f.apoderado().token(), marzo, "00000171", "350.00");

        for (String rol : List.of("ADMINISTRADOR", "DOCENTE", "ALUMNO")) {
            String token = tokenDe(crearUsuario(rol, true));
            assertThat(presentarComprobante(token, marzo, cuerpo).estado()).as(rol).isEqualTo(403);
        }
        assertThat(presentarComprobante(null, marzo, cuerpo).estado()).isEqualTo(401);
    }

    @Test
    void elVoucherDebeHaberSidoSubidoPorElMismoApoderadoYSerUnArchivoValido() throws Exception {
        Familia f = crearFamilia();
        ApoderadoConCuenta otro = crearApoderadoConCuenta();
        String marzo = f.pension(datos, 3);
        Map<String, Object> sinSubir = comprobante(f.apoderado().token(), marzo, "00000181", "350.00");
        sinSubir.put("claveObjeto", "comprobantes/" + f.apoderado().apoderadoId() + "/00000000-nunca-subido.pdf");
        Map<String, Object> ajeno = comprobante(f.apoderado().token(), marzo, "00000182", "350.00");
        ajeno.put("claveObjeto", "comprobantes/" + otro.apoderadoId() + "/de-otro.pdf");
        Map<String, Object> noEsPdf = new HashMap<>(comprobante(f.apoderado().token(), marzo, "00000183", "350.00"));
        noEsPdf.put("contentType", "application/x-msdownload");

        for (Map<String, Object> cuerpo : List.of(sinSubir, ajeno, noEsPdf)) {
            Respuesta r = presentarComprobante(f.apoderado().token(), marzo, cuerpo);
            assertThat(r.estado()).isEqualTo(422);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("ARCHIVO_INVALIDO");
        }
        Respuesta ejecutable = post("/api/v1/obligaciones/" + marzo + "/comprobantes/archivo", json.writeValueAsString(
                Map.of("nombreArchivo", "virus.exe", "contentType", "application/x-msdownload", "tamanoBytes", 100)),
                f.apoderado().token());
        assertThat(ejecutable.estado()).isEqualTo(422);
    }
}
