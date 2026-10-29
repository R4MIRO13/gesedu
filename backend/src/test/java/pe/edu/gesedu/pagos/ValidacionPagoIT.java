package pe.edu.gesedu.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import pe.edu.gesedu.matricula.BaseMatriculaIT;

/** UC-05, Validar y registrar pago (RF-08 a RF-10, RNF-10, RNF-11): flujo básico, FA-1, FA-2, rollback y concurrencia. */
class ValidacionPagoIT extends BaseMatriculaIT {

    private static final String RUTA = "/api/v1/comprobantes";

    @BeforeEach
    void limpiar() {
        reset(notificador, registrosPago);
    }

    /** El apoderado presenta el comprobante de la pensión del mes; devuelve su id. */
    private String presentar(Familia f, int mes, String operacion, String monto) throws Exception {
        String obligacion = f.pension(datos, mes);
        Respuesta r = presentarComprobante(f.apoderado().token(), obligacion,
                comprobante(f.apoderado().token(), obligacion, operacion, monto));
        assertThat(r.estado()).as("presentar " + operacion).isEqualTo(201);
        return r.cuerpo().get("id").asString();
    }

    private Respuesta validar(String comprobanteId, String token) throws Exception {
        return post(RUTA + "/" + comprobanteId + "/validacion", "{}", token);
    }

    private Respuesta revisar(String accion, String comprobanteId, String texto, String token) throws Exception {
        return post(RUTA + "/" + comprobanteId + "/" + accion, json.writeValueAsString(Map.of("observacion", texto)), token);
    }

    private String estadoObligacion(Familia f, int mes) {
        return jdbc.queryForObject("SELECT estado FROM pagos.obligacion_pago WHERE id = ?", String.class, f.pension(datos, mes));
    }

    private String estadoComprobante(String id) {
        return jdbc.queryForObject("SELECT estado_revision FROM pagos.comprobante_pago WHERE id = ?", String.class, id);
    }

    @Test
    void flujoBasicoEmiteLaBoletaApruebaElComprobanteYDejaLaObligacionPagada() throws Exception {
        Familia f = crearFamilia();
        String id = presentar(f, 9, "VP-00458712", "350.00");
        String admin = tokenAdministrador();

        Respuesta r = validar(id, admin);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("numeroBoleta").asString()).matches("B001-\\d{5}");
        assertThat(r.cuerpo().get("montoTotal").asString()).isEqualTo("350.00");
        assertThat(r.cuerpo().get("estadoObligacion").asString()).isEqualTo("PAGADA");
        assertThat(estadoObligacion(f, 9)).isEqualTo("PAGADA");
        assertThat(estadoComprobante(id)).isEqualTo("APROBADO");
        assertThat(jdbc.queryForObject("SELECT numero_boleta || '/' || monto_total FROM pagos.registro_pago "
                + "WHERE comprobante_id = ?", String.class, id)).isEqualTo(r.cuerpo().get("numeroBoleta").asString() + "/350.00");
        assertThat(jdbc.queryForObject("SELECT revisado_por IS NOT NULL AND revisado_en IS NOT NULL "
                + "FROM pagos.comprobante_pago WHERE id = ?", Boolean.class, id)).isTrue();
        // Auditoría en la misma transacción, con el estado de la obligación antes y después
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'VALIDAR_PAGO' "
                + "AND id_entidad_afectada = ?", r.cuerpo().get("numeroBoleta").asString())).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM auditoria.cambio_auditoria c JOIN auditoria.registro_auditoria r "
                + "ON r.id = c.registro_auditoria_id WHERE r.id_entidad_afectada = ? AND c.campo = 'estadoObligacion' "
                + "AND c.valor_anterior = 'PENDIENTE' AND c.valor_nuevo = 'PAGADA'", r.cuerpo().get("numeroBoleta").asString()))
                .isEqualTo(1);
        // El apoderado lo ve en su consulta y recibe el aviso por correo (C-41)
        var fila = get("/api/v1/alumnos/" + f.alumnoId() + "/obligaciones", f.apoderado().token()).cuerpo().get("obligaciones").get(7);
        assertThat(fila.get("estado").asString()).isEqualTo("PAGADA");
        assertThat(fila.get("boleta").get("numero").asString()).isEqualTo(r.cuerpo().get("numeroBoleta").asString());
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(notificador, timeout(3000)).enviar(eq(f.apoderado().correo()), contains("Pago registrado"), cuerpo.capture());
        assertThat(cuerpo.getValue()).contains(r.cuerpo().get("numeroBoleta").asString(), "350.00", "VP-00458712");
    }

    @Test
    void laObligacionVencidaSeValidaPorLaSumaDelImporteBaseYLaPenalidad() throws Exception {
        Familia f = crearFamilia();
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'VENCIDA', monto_penalidad = 17.50 WHERE id = ?", f.pension(datos, 8));
        String id = presentar(f, 8, "VP-00458713", "367.50");

        Respuesta r = validar(id, tokenAdministrador());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("montoTotal").asString()).isEqualTo("367.50");
        assertThat(estadoObligacion(f, 8)).isEqualTo("PAGADA");
        assertThat(jdbc.queryForObject("SELECT monto_total FROM pagos.registro_pago WHERE comprobante_id = ?",
                java.math.BigDecimal.class, id)).isEqualByComparingTo("367.50");
    }

    @Test
    void fa2UnImporteInsuficienteEnUnaObligacionVencidaSeObservaYElApoderadoPresentaUnoNuevo() throws Exception {
        Familia f = crearFamilia();
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'VENCIDA', monto_penalidad = 17.50 WHERE id = ?", f.pension(datos, 8));
        String id = presentar(f, 8, "VP-00458714", "350.00");
        String admin = tokenAdministrador();

        Respuesta validacion = validar(id, admin);
        assertThat(validacion.estado()).isEqualTo(422);
        assertThat(validacion.cuerpo().get("codigo").asString()).isEqualTo("MONTO_INSUFICIENTE");
        assertThat(validacion.cuerpo().get("total").asString()).isEqualTo("367.50");
        assertThat(estadoObligacion(f, 8)).isEqualTo("VENCIDA");

        Respuesta observacion = revisar("observacion", id, "El monto no cubre la penalidad", admin);
        assertThat(observacion.estado()).isEqualTo(200);
        assertThat(observacion.cuerpo().get("estado").asString()).isEqualTo("OBSERVADO");
        assertThat(observacion.cuerpo().get("totalPendiente").asString()).isEqualTo("367.50");
        assertThat(estadoObligacion(f, 8)).as("la obligación no cambia").isEqualTo("VENCIDA");
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(notificador, timeout(3000)).enviar(eq(f.apoderado().correo()), contains("Comprobante observado"), cuerpo.capture());
        assertThat(cuerpo.getValue()).contains("El monto no cubre la penalidad", "S/ 367.50");

        // Nuevo comprobante con el importe correcto, mismo número de operación liberado
        String obligacion = f.pension(datos, 8);
        Respuesta nuevo = presentarComprobante(f.apoderado().token(), obligacion,
                comprobante(f.apoderado().token(), obligacion, "VP-00458714", "367.50"));
        assertThat(nuevo.estado()).isEqualTo(201);
        assertThat(validar(nuevo.cuerpo().get("id").asString(), admin).estado()).isEqualTo(200);
        assertThat(estadoObligacion(f, 8)).isEqualTo("PAGADA");
    }

    @Test
    void unImporteMayorAlTotalNoSeValidaSinObservar() throws Exception {
        Familia f = crearFamilia();
        String id = presentar(f, 3, "VP-00458715", "400.00");

        Respuesta r = validar(id, tokenAdministrador());

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("detail").asString()).contains("excede el importe total");
        assertThat(estadoObligacion(f, 3)).isEqualTo("PENDIENTE");
        assertThat(estadoComprobante(id)).isEqualTo("PENDIENTE");
    }

    @Test
    void fa1UnComprobanteQueNoCorrespondeSeRechazaSinTocarLaObligacion() throws Exception {
        Familia f = crearFamilia();
        String id = presentar(f, 4, "VP-00458716", "350.00");

        Respuesta r = revisar("rechazo", id, "El voucher es de otro estudiante", tokenAdministrador());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("RECHAZADO");
        assertThat(estadoComprobante(id)).isEqualTo("RECHAZADO");
        assertThat(estadoObligacion(f, 4)).isEqualTo("PENDIENTE");
        assertThat(contar("SELECT count(*) FROM pagos.registro_pago WHERE comprobante_id = ?", id)).isZero();
        verify(notificador, timeout(3000)).enviar(eq(f.apoderado().correo()), contains("Comprobante rechazado"), anyString());
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'RECHAZAR_COMPROBANTE' "
                + "AND id_entidad_afectada = ?", id)).isEqualTo(1);
        // Una observación sin texto no se acepta
        String otro = presentar(f, 5, "VP-00458717", "350.00");
        assertThat(revisar("observacion", otro, "  ", tokenAdministrador()).estado()).isEqualTo(400);
    }

    @Test
    void unaObligacionYaPagadaNoSeValidaDeNuevo() throws Exception {
        Familia f = crearFamilia();
        String id = presentar(f, 6, "VP-00458718", "350.00");
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'PAGADA' WHERE id = ?", f.pension(datos, 6));

        Respuesta r = validar(id, tokenAdministrador());

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("OBLIGACION_YA_PAGADA");
        assertThat(estadoComprobante(id)).isEqualTo("PENDIENTE");
    }

    @Test
    void unComprobanteYaRevisadoNoSeVuelveARevisar() throws Exception {
        Familia f = crearFamilia();
        String id = presentar(f, 7, "VP-00458719", "350.00");
        String admin = tokenAdministrador();
        revisar("observacion", id, "Falta la fecha", admin);

        for (Respuesta r : List.of(validar(id, admin), revisar("rechazo", id, "otro motivo", admin))) {
            assertThat(r.estado()).isEqualTo(409);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("CONFLICTO_CONCURRENCIA");
        }
        assertThat(validar("00000000-0000-0000-0000-000000000000", admin).estado()).isEqualTo(404);
    }

    @Test
    void unFalloDespuesDeInsertarLaBoletaRevierteTodo() throws Exception {
        Familia f = crearFamilia();
        String id = presentar(f, 9, "VP-00458720", "350.00");
        long auditoriasAntes = contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'VALIDAR_PAGO'");
        // La boleta se inserta de verdad y justo después falla: la transacción debe deshacerla
        doAnswer(invocacion -> {
            invocacion.callRealMethod();
            throw new IllegalStateException("falla provocada tras insertar la boleta");
        }).when(registrosPago).saveAndFlush(org.mockito.ArgumentMatchers.any());

        Respuesta r = validar(id, tokenAdministrador());

        assertThat(r.estado()).isEqualTo(500);
        assertThat(contar("SELECT count(*) FROM pagos.registro_pago WHERE comprobante_id = ?", id)).as("boleta").isZero();
        assertThat(estadoComprobante(id)).isEqualTo("PENDIENTE");
        assertThat(estadoObligacion(f, 9)).isEqualTo("PENDIENTE");
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'VALIDAR_PAGO'"))
                .as("la auditoría se revierte con la transacción").isEqualTo(auditoriasAntes);
        verify(notificador, never()).enviar(eq(f.apoderado().correo()), contains("Pago registrado"), anyString());

        // Sin la falla, la misma validación funciona
        reset(registrosPago);
        assertThat(validar(id, tokenAdministrador()).estado()).isEqualTo(200);
    }

    @Test
    void dosValidacionesSimultaneasDelMismoComprobanteDejanUnaSolaBoleta() throws Exception {
        Familia f = crearFamilia();
        String id = presentar(f, 3, "VP-00458721", "350.00");
        String admin = tokenAdministrador();
        String otroAdmin = tokenAdministrador();
        CountDownLatch salida = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Respuesta>> futuros = new ArrayList<>();
            for (String token : List.of(admin, otroAdmin)) {
                futuros.add(pool.submit(() -> {
                    salida.await();
                    return validar(id, token);
                }));
            }
            salida.countDown();
            List<Integer> estados = new ArrayList<>();
            String codigoDelSegundo = null;
            for (Future<Respuesta> futuro : futuros) {
                Respuesta r = futuro.get();
                estados.add(r.estado());
                if (r.estado() != 200) {
                    codigoDelSegundo = r.cuerpo().get("codigo").asString();
                }
            }
            assertThat(estados).containsExactlyInAnyOrder(200, 422);
            // La segunda esperó el bloqueo y vio la obligación ya pagada
            assertThat(codigoDelSegundo).isEqualTo("OBLIGACION_YA_PAGADA");
        } finally {
            pool.shutdownNow();
        }
        assertThat(contar("SELECT count(*) FROM pagos.registro_pago WHERE comprobante_id = ?", id)).isEqualTo(1);
        assertThat(estadoObligacion(f, 3)).isEqualTo("PAGADA");
    }

    @Test
    void validarYObservarAlMismoTiempoNoSeContradicen() throws Exception {
        Familia f = crearFamilia();
        String id = presentar(f, 4, "VP-00458722", "350.00");
        String admin = tokenAdministrador();
        CountDownLatch salida = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Respuesta> v = pool.submit(() -> {
                salida.await();
                return validar(id, admin);
            });
            Future<Respuesta> o = pool.submit(() -> {
                salida.await();
                return revisar("observacion", id, "Revisar el voucher", admin);
            });
            salida.countDown();
            List<Integer> estados = List.of(v.get().estado(), o.get().estado());
            assertThat(estados.stream().filter(s -> s == 200)).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        // Un solo desenlace: aprobado con boleta y obligación pagada, u observado con la obligación intacta
        String estado = estadoComprobante(id);
        long boletas = contar("SELECT count(*) FROM pagos.registro_pago WHERE comprobante_id = ?", id);
        if (estado.equals("APROBADO")) {
            assertThat(boletas).isEqualTo(1);
            assertThat(estadoObligacion(f, 4)).isEqualTo("PAGADA");
        } else {
            assertThat(estado).isEqualTo("OBSERVADO");
            assertThat(boletas).isZero();
            assertThat(estadoObligacion(f, 4)).isEqualTo("PENDIENTE");
        }
    }

    @Test
    void laBandejaListaLosPendientesDelMasAntiguoAlMasRecienteConSuEstudianteYConcepto() throws Exception {
        Familia f = crearFamilia();
        String primero = presentar(f, 3, "VP-00458723", "350.00");
        String segundo = presentar(f, 4, "VP-00458724", "350.00");
        String admin = tokenAdministrador();
        revisar("rechazo", segundo, "No corresponde", admin);
        String tercero = presentar(f, 5, "VP-00458725", "350.00");

        Respuesta pendientes = get(RUTA, admin);
        Respuesta rechazados = get(RUTA + "?estado=RECHAZADO", admin);

        assertThat(pendientes.estado()).isEqualTo(200);
        List<String> ids = new ArrayList<>();
        pendientes.cuerpo().get("contenido").forEach(x -> ids.add(x.get("id").asString()));
        assertThat(ids).contains(primero, tercero).doesNotContain(segundo);
        assertThat(ids.indexOf(primero)).isLessThan(ids.indexOf(tercero));
        var fila = pendientes.cuerpo().get("contenido").get(ids.indexOf(primero));
        assertThat(fila.get("estudiante").asString()).startsWith("Lucía");
        assertThat(fila.get("concepto").asString()).isEqualTo("PENSION");
        assertThat(fila.get("periodo").asString()).isEqualTo(f.escenario().anio() + "-03");
        assertThat(fila.get("montoPresentado").asString()).isEqualTo("350.00");
        assertThat(fila.get("numeroOperacion").asString()).isEqualTo("VP-00458723");
        assertThat(fila.get("enviado").asString()).endsWith("-05:00");
        assertThat(rechazados.cuerpo().get("contenido").toString()).contains(segundo);
        assertThat(pendientes.cuerpo().has("totalElementos")).isTrue();
    }

    @Test
    void elDetalleMuestraLaPenalidadLasComprobacionesYUnaUrlDelVoucherQueFunciona() throws Exception {
        Familia f = crearFamilia();
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'VENCIDA', monto_penalidad = 17.50 WHERE id = ?", f.pension(datos, 8));
        String insuficiente = presentar(f, 8, "VP-00458726", "350.00");
        String admin = tokenAdministrador();

        Respuesta r = get(RUTA + "/" + insuficiente, admin);

        assertThat(r.estado()).isEqualTo(200);
        var o = r.cuerpo().get("obligacion");
        assertThat(o.get("montoBase").asString()).isEqualTo("350.00");
        assertThat(o.get("montoPenalidad").asString()).isEqualTo("17.50");
        assertThat(o.get("total").asString()).isEqualTo("367.50");
        assertThat(o.get("estado").asString()).isEqualTo("VENCIDA");
        assertThat(o.get("alumno").asString()).startsWith("Lucía");
        assertThat(r.cuerpo().get("declarado").get("numeroOperacion").asString()).isEqualTo("VP-00458726");
        assertThat(r.cuerpo().get("declarado").get("apoderado").asString()).isEqualTo("Prueba Usuario");
        var v = r.cuerpo().get("verificaciones");
        assertThat(v.get("montoCubreTotal").asBoolean()).isFalse();
        assertThat(v.get("montoCoincide").asBoolean()).isFalse();
        assertThat(v.get("numeroOperacionNuevo").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("puedeRevisar").asBoolean()).isTrue();
        // La URL prefirmada del voucher descarga el archivo subido
        Descarga voucher = descargar(r.cuerpo().get("voucher").get("url").asString(), null);
        assertThat(voucher.estado()).isEqualTo(200);
        assertThat(new String(voucher.contenido())).startsWith("%PDF-1.4 voucher VP-00458726");
    }

    @Test
    void soloElAdministradorRevisaComprobantes() throws Exception {
        Familia f = crearFamilia();
        String id = presentar(f, 3, "VP-00458727", "350.00");

        for (String token : List.of(f.apoderado().token(), tokenDe(crearUsuario("DOCENTE", true)), tokenDe(crearUsuario("ALUMNO", true)))) {
            assertThat(get(RUTA, token).estado()).isEqualTo(403);
            assertThat(get(RUTA + "/" + id, token).estado()).isEqualTo(403);
            assertThat(validar(id, token).estado()).isEqualTo(403);
            assertThat(revisar("observacion", id, "x", token).estado()).isEqualTo(403);
            assertThat(revisar("rechazo", id, "x", token).estado()).isEqualTo(403);
        }
        assertThat(get(RUTA, null).estado()).isEqualTo(401);
        assertThat(estadoComprobante(id)).isEqualTo("PENDIENTE");
    }
}
