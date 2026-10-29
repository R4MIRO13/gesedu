package pe.edu.gesedu.pagos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;
import pe.edu.gesedu.matricula.BaseMatriculaIT;

/** UC-03, Consultar matrícula y obligaciones de pago (RF-07, RF-09, RF-11): flujo básico, FA-1, FA-2 y alcance. */
class ConsultaObligacionesIT extends BaseMatriculaIT {

    private Respuesta matriculas(String token) throws Exception {
        return get("/api/v1/matriculas/mias", token);
    }

    private Respuesta obligaciones(String alumnoId, String token) throws Exception {
        return get("/api/v1/alumnos/" + alumnoId + "/obligaciones", token);
    }

    @Test
    void flujoBasicoMuestraLaMatriculaVigenteYLasObligacionesDelHijo() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String lucia = datos.alumnoDe(ap.apoderadoId(), "Lucía");
        datos.matriculaConObligaciones(e, ap.apoderadoId(), lucia, e.seccion3A());

        Respuesta m = matriculas(ap.token());
        Respuesta o = obligaciones(lucia, ap.token());

        assertThat(m.estado()).isEqualTo(200);
        assertThat(m.cuerpo()).hasSize(1);
        var matricula = m.cuerpo().get(0).get("matricula");
        assertThat(m.cuerpo().get(0).get("alumno").asString()).startsWith("Lucía");
        assertThat(matricula.get("anio").asInt()).isEqualTo(e.anio());
        assertThat(matricula.get("grado").asString()).isEqualTo("3° de secundaria");
        assertThat(matricula.get("seccion").asString()).isEqualTo("3° A");
        assertThat(matricula.get("estado").asString()).isEqualTo("Confirmada");

        assertThat(o.estado()).isEqualTo(200);
        var lista = o.cuerpo().get("obligaciones");
        assertThat(lista).hasSize(11);
        assertThat(lista.get(0).get("concepto").asString()).isEqualTo("MATRICULA");
        assertThat(lista.get(0).get("periodo").asString()).isEqualTo(String.valueOf(e.anio()));
        assertThat(lista.get(0).get("montoBase").asString()).isEqualTo("350.00");
        assertThat(lista.get(0).get("montoPenalidad").asString()).isEqualTo("0.00");
        assertThat(lista.get(0).get("total").asString()).isEqualTo("350.00");
        assertThat(lista.get(0).get("estado").asString()).isEqualTo("PENDIENTE");
        assertThat(lista.get(0).get("fechaVencimiento").asString()).isEqualTo(e.anio() + "-02-28");
        assertThat(lista.get(0).get("puedePresentarComprobante").asBoolean()).isTrue();
        assertThat(lista.get(10).get("periodo").asString()).isEqualTo(e.anio() + "-12");
        assertThat(o.cuerpo().get("resumen").get("obligaciones").asInt()).isEqualTo(11);
        assertThat(o.cuerpo().get("resumen").get("totalPendiente").asString()).isEqualTo("3850.00");
        assertThat(o.cuerpo().get("resumen").get("proximoVencimiento").get("concepto").asString()).isEqualTo("MATRICULA");
    }

    @Test
    void laObligacionVencidaMuestraLaPenalidadYElTotalActualizado() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String alumno = datos.alumnoDe(ap.apoderadoId(), "Lucía");
        String matricula = datos.matriculaConObligaciones(e, ap.apoderadoId(), alumno, e.seccion3A());
        String agosto = datos.obligacion(matricula, e.anio() + "-08");
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'VENCIDA', monto_penalidad = 17.50, "
                + "penalidad_aplicada_en = now() WHERE id = ?", agosto);

        var o = obligaciones(alumno, ap.token()).cuerpo();

        var vencida = o.get("obligaciones").get(6);
        assertThat(vencida.get("periodo").asString()).isEqualTo(e.anio() + "-08");
        assertThat(vencida.get("estado").asString()).isEqualTo("VENCIDA");
        assertThat(vencida.get("montoBase").asString()).isEqualTo("350.00");
        assertThat(vencida.get("montoPenalidad").asString()).isEqualTo("17.50");
        assertThat(vencida.get("total").asString()).isEqualTo("367.50");
        assertThat(vencida.get("penalidadAplicadaEn").asString()).endsWith("-05:00");
        var resumen = o.get("resumen");
        assertThat(resumen.get("deudaVencida").asString()).isEqualTo("367.50");
        assertThat(resumen.get("vencidas").asInt()).isEqualTo(1);
        assertThat(resumen.get("totalPendiente").asString()).isEqualTo("3867.50");
        // El próximo vencimiento es la pendiente más cercana; lo vencido ya está en la deuda
        assertThat(resumen.get("proximoVencimiento").get("concepto").asString()).isEqualTo("MATRICULA");
        assertThat(resumen.get("proximoVencimiento").get("total").asString()).isEqualTo("350.00");
    }

    @Test
    void lasPagadasMuestranSuBoletaYUnComprobantePendienteBloqueaOtroNuevo() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String alumno = datos.alumnoDe(ap.apoderadoId(), "Lucía");
        String matricula = datos.matriculaConObligaciones(e, ap.apoderadoId(), alumno, e.seccion3A());
        String marzo = datos.obligacion(matricula, e.anio() + "-03");
        String abril = datos.obligacion(matricula, e.anio() + "-04");
        String mayo = datos.obligacion(matricula, e.anio() + "-05");
        pagar(marzo, ap.apoderadoId(), "00000001", "B001-00741");
        comprobante(abril, ap.apoderadoId(), "00000002", "PENDIENTE", null);
        comprobante(mayo, ap.apoderadoId(), "00000003", "OBSERVADO", "El monto no cubre la penalidad");

        var lista = obligaciones(alumno, ap.token()).cuerpo().get("obligaciones");

        var pagada = lista.get(1);
        assertThat(pagada.get("estado").asString()).isEqualTo("PAGADA");
        assertThat(pagada.get("boleta").get("numero").asString()).isEqualTo("B001-00741");
        assertThat(pagada.get("boleta").get("monto").asString()).isEqualTo("350.00");
        assertThat(pagada.get("puedePresentarComprobante").asBoolean()).isFalse();
        var enRevision = lista.get(2);
        assertThat(enRevision.get("comprobante").get("estado").asString()).isEqualTo("PENDIENTE");
        assertThat(enRevision.get("puedePresentarComprobante").asBoolean()).isFalse();
        var observada = lista.get(3);
        assertThat(observada.get("comprobante").get("estado").asString()).isEqualTo("OBSERVADO");
        assertThat(observada.get("comprobante").get("observacion").asString()).isEqualTo("El monto no cubre la penalidad");
        assertThat(observada.get("puedePresentarComprobante").asBoolean()).isTrue();
        assertThat(lista.get(4).get("boleta").isNull()).isTrue();
        var resumen = obligaciones(alumno, ap.token()).cuerpo().get("resumen");
        assertThat(resumen.get("pagadas").asInt()).isEqualTo(1);
        assertThat(resumen.get("totalPagado").asString()).isEqualTo("350.00");
    }

    @Test
    void fa1SinObligacionesGeneradasLaListaVieneVacia() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String alumno = datos.alumnoDe(ap.apoderadoId(), "Lucía");
        String matricula = datos.matriculaConObligaciones(e, ap.apoderadoId(), alumno, e.seccion3A());
        jdbc.update("DELETE FROM pagos.obligacion_pago WHERE matricula_id = ?", matricula);

        Respuesta m = matriculas(ap.token());
        var o = obligaciones(alumno, ap.token()).cuerpo();

        assertThat(m.cuerpo().get(0).get("matricula").isNull()).isFalse();
        assertThat(o.get("obligaciones")).isEmpty();
        assertThat(o.get("resumen").get("obligaciones").asInt()).isZero();
        assertThat(o.get("resumen").get("proximoVencimiento").isNull()).isTrue();
    }

    @Test
    void fa2SinMatriculaVigenteNoHayMatriculaNiObligacionesDeUnAnioActivo() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String alumno = datos.alumnoDe(ap.apoderadoId(), "Lucía");
        String sinMatricula = datos.alumnoDe(ap.apoderadoId(), "Andrés");
        datos.matriculaConObligaciones(e, ap.apoderadoId(), alumno, e.seccion3A());
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'CERRADO' WHERE id = ?", e.anioId());

        Respuesta m = matriculas(ap.token());

        assertThat(m.cuerpo()).hasSize(2);
        assertThat(m.cuerpo().get(0).get("alumno").asString()).startsWith("Andrés");
        for (var hijo : m.cuerpo()) {
            assertThat(hijo.get("matricula").isNull()).as(hijo.get("alumno").asString()).isTrue();
        }
        // Las obligaciones de un año cerrado no se muestran como las de un año activo
        assertThat(obligaciones(alumno, ap.token()).cuerpo().get("obligaciones")).isEmpty();
        assertThat(obligaciones(sinMatricula, ap.token()).cuerpo().get("obligaciones")).isEmpty();
    }

    @Test
    void unApoderadoNoVeLosHijosDeOtro() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        ApoderadoConCuenta otro = crearApoderadoConCuenta();
        String ajeno = datos.alumnoDe(otro.apoderadoId(), "Ajeno");
        datos.matriculaConObligaciones(e, otro.apoderadoId(), ajeno, e.seccion3A());

        assertThat(obligaciones(ajeno, ap.token()).estado()).isEqualTo(403);
        assertThat(obligaciones(ajeno, ap.token()).cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
        assertThat(get("/api/v1/matriculas/mias?alumnoId=" + ajeno, ap.token()).estado()).isEqualTo(403);
        assertThat(matriculas(ap.token()).cuerpo()).isEmpty();
        assertThat(obligaciones(UUID.randomUUID().toString(), ap.token()).estado()).isEqualTo(403);
    }

    @Test
    void soloElApoderadoVeObligacionesYElAlumnoVeSuPropiaMatricula() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String alumno = datos.alumnoDe(ap.apoderadoId(), "Lucía");
        datos.matriculaConObligaciones(e, ap.apoderadoId(), alumno, e.seccion3A());

        for (String rol : List.of("ADMINISTRADOR", "DOCENTE", "ALUMNO")) {
            String token = tokenDe(crearUsuario(rol, true));
            assertThat(obligaciones(alumno, token).estado()).as(rol + " obligaciones").isEqualTo(403);
        }
        assertThat(matriculas(tokenDe(crearUsuario("ADMINISTRADOR", true))).estado()).isEqualTo(403);
        assertThat(matriculas(null).estado()).isEqualTo(401);
        assertThat(obligaciones(alumno, null).estado()).isEqualTo(401);

        // Un alumno con cuenta ve únicamente su propia matrícula
        String usuarioAlumno = jdbc.queryForObject("SELECT usuario_id FROM seguridad.alumno WHERE id = ?", String.class, alumno);
        jdbc.update("UPDATE seguridad.usuario SET correo = 'alumno-' || substr(id, 1, 8) || '@test.gesedu.pe', "
                + "hash_contrasena = ?, activo = TRUE WHERE id = ?", codificador.encode(CONTRASENA), usuarioAlumno);
        String correo = jdbc.queryForObject("SELECT correo FROM seguridad.usuario WHERE id = ?", String.class, usuarioAlumno);
        Respuesta propia = matriculas(tokenDe(correo));
        assertThat(propia.estado()).isEqualTo(200);
        assertThat(propia.cuerpo()).hasSize(1);
        assertThat(propia.cuerpo().get(0).get("matricula").get("seccion").asString()).isEqualTo("3° A");
    }

    private void comprobante(String obligacionId, String apoderadoId, String operacion, String estado, String observacion) {
        jdbc.update("INSERT INTO pagos.comprobante_pago (id, obligacion_id, apoderado_id, numero_operacion, fecha_pago, "
                + "monto_presentado, medio_pago, referencia_comprobante, estado_revision, observacion) "
                + "VALUES (?, ?, ?, ?, CURRENT_DATE, 350.00, 'Transferencia', 'comprobantes/prueba.pdf', ?, ?)",
                UUID.randomUUID().toString(), obligacionId, apoderadoId, operacion, estado, observacion);
    }

    private void pagar(String obligacionId, String apoderadoId, String operacion, String boleta) {
        String comprobanteId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO pagos.comprobante_pago (id, obligacion_id, apoderado_id, numero_operacion, fecha_pago, "
                + "monto_presentado, medio_pago, referencia_comprobante, estado_revision) "
                + "VALUES (?, ?, ?, ?, CURRENT_DATE, 350.00, 'Transferencia', 'comprobantes/prueba.pdf', 'APROBADO')",
                comprobanteId, obligacionId, apoderadoId, operacion);
        jdbc.update("INSERT INTO pagos.registro_pago (id, comprobante_id, obligacion_id, numero_boleta, fecha_registro, "
                + "monto_total) VALUES (?, ?, ?, ?, now(), 350.00)", UUID.randomUUID().toString(), comprobanteId,
                obligacionId, boleta);
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'PAGADA' WHERE id = ?", obligacionId);
    }
}
