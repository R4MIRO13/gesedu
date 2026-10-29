package pe.edu.gesedu.matricula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;
import pe.edu.gesedu.apoyo.DatosMatricula.Solicitud;

/** UC-02, Registrar matrícula (RF-04, RF-05, RF-07, RF-12, RNF-09, RNF-10): flujo básico y FA-1 a FA-5. */
class ConfirmacionMatriculaIT extends BaseMatriculaIT {

    @BeforeEach
    void limpiar() {
        reset(notificador, planDeCobros);
    }

    private String versionDe(String codigo, String token) throws Exception {
        return etag(get(ruta(codigo), token));
    }

    private Respuesta confirmar(Solicitud s, String seccionId, String token) throws Exception {
        return enviar("POST", ruta(s.codigo()) + "/confirmacion", cuerpoConfirmacion(seccionId), token,
                versionDe(s.codigo(), token));
    }

    private Respuesta rechazar(Solicitud s, String motivo, String token) throws Exception {
        return enviar("POST", ruta(s.codigo()) + "/rechazo", cuerpoRechazo(motivo, "Observación de prueba"), token,
                versionDe(s.codigo(), token));
    }

    private String estado(Solicitud s) {
        return jdbc.queryForObject("SELECT estado FROM matricula.solicitud_matricula WHERE id = ?", String.class, s.id());
    }

    /** Matrículas confirmadas del alumno en el año de la solicitud (las de años anteriores no cuentan). */
    private long matriculas(Solicitud s) {
        return contar("SELECT count(*) FROM matricula.matricula m JOIN matricula.solicitud_matricula sm ON sm.id = "
                + "?  WHERE m.alumno_id = ? AND m.estado = 'CONFIRMADA' AND m.anio_escolar_id = sm.anio_escolar_id",
                s.id(), s.alumnoId());
    }

    private int vacantes(String seccionId) {
        return jdbc.queryForObject("SELECT vacantes_disponibles FROM academico.seccion WHERE id = ?", Integer.class, seccionId);
    }

    @Test
    void elDetalleMuestraLasValidacionesLasVacantesYElResumenDeCostos() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, false);

        Respuesta r = get(ruta(s.codigo()), token);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(etag(r)).isNotNull();
        var detalle = r.cuerpo();
        assertThat(detalle.get("estado").asString()).isEqualTo("PENDIENTE_REVISION");
        assertThat(detalle.get("grado").asString()).isEqualTo("3°");
        assertThat(detalle.get("puedeConfirmar").asBoolean()).isTrue();
        assertThat(detalle.get("validaciones")).hasSize(4).allMatch(v -> v.get("aprobada").asBoolean());
        assertThat(detalle.get("secciones")).extracting(sec -> sec.get("nombre").asString() + ":" + sec.get("vacantes").asInt())
                .containsExactly("3° A:4", "3° B:30");
        assertThat(detalle.get("costos").get("lineas")).hasSize(11);
        assertThat(detalle.get("costos").get("total").asString()).isEqualTo("3850.00");
        assertThat(detalle.get("documentos")).hasSize(1);
    }

    @Test
    void confirmarCreaLaMatriculaDescuentaLaVacanteGeneraLos11CompromisosAuditaYAvisaPorCorreo() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, false);

        Respuesta r = confirmar(s, e.seccion3A(), token);

        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("seccion").asString()).isEqualTo("3° A");
        assertThat(r.cuerpo().get("vacantesRestantes").asInt()).isEqualTo(3);
        assertThat(r.cuerpo().get("compromisosGenerados").asInt()).isEqualTo(11);
        assertThat(r.cuerpo().get("total").asString()).isEqualTo("3850.00");
        assertThat(estado(s)).isEqualTo("APROBADA");
        assertThat(vacantes(e.seccion3A())).isEqualTo(3);
        assertThat(matriculas(s)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM pagos.obligacion_pago WHERE alumno_id = ? AND estado = 'PENDIENTE'",
                s.alumnoId())).isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT sum(monto_base) FROM pagos.obligacion_pago WHERE alumno_id = ?",
                java.math.BigDecimal.class, s.alumnoId())).isEqualByComparingTo("3850.00");

        // Auditoría en la misma transacción, con los cambios
        Map<String, Object> registro = jdbc.queryForMap("SELECT * FROM auditoria.registro_auditoria WHERE accion = "
                + "'CONFIRMAR_MATRICULA' AND id_entidad_afectada = ?", r.cuerpo().get("matriculaId").asString());
        assertThat(registro.get("modulo")).isEqualTo("MATRICULA");
        assertThat(jdbc.queryForList("SELECT campo FROM auditoria.cambio_auditoria WHERE registro_auditoria_id = ?",
                String.class, registro.get("id"))).contains("estado", "seccion", "vacantes", "compromisosGenerados");

        // El correo con el cronograma sale después del commit (RF-12)
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(notificador, timeout(3000)).enviar(eq(s.correoApoderado()), contains("Matrícula confirmada"), cuerpo.capture());
        assertThat(cuerpo.getValue()).contains("3850.00", "3° A", "Matrícula " + e.anio()).contains("Pensión " + e.anio() + "-12");
    }

    @Test
    void sinIfMatchSinAceptarLosCostosOConVersionVencidaNoSeConfirma() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, false);

        Respuesta sinVersion = enviar("POST", ruta(s.codigo()) + "/confirmacion", cuerpoConfirmacion(e.seccion3A()), token, null);
        assertThat(sinVersion.estado()).isEqualTo(400);
        assertThat(sinVersion.cuerpo().get("codigo").asString()).isEqualTo("VERSION_REQUERIDA");

        Respuesta vencida = enviar("POST", ruta(s.codigo()) + "/confirmacion", cuerpoConfirmacion(e.seccion3A()), token, "\"99\"");
        assertThat(vencida.estado()).isEqualTo(409);
        assertThat(vencida.cuerpo().get("codigo").asString()).isEqualTo("CONFLICTO_CONCURRENCIA");

        Respuesta sinAceptar = enviar("POST", ruta(s.codigo()) + "/confirmacion",
                json.writeValueAsString(Map.of("seccionId", e.seccion3A(), "costosAceptados", false)), token,
                versionDe(s.codigo(), token));
        assertThat(sinAceptar.estado()).isEqualTo(400);
        assertThat(sinAceptar.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");

        assertThat(estado(s)).isEqualTo("PENDIENTE_REVISION");
        assertThat(matriculas(s)).isZero();
        assertThat(vacantes(e.seccion3A())).isEqualTo(4);
    }

    @Test
    void unaSeccionDeOtroGradoNoSeAcepta() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, false);

        Respuesta r = confirmar(s, e.seccion2A(), token);

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("SOLICITUD_INVALIDA");
        assertThat(vacantes(e.seccion2A())).isEqualTo(30);
    }

    @Test
    void unaSolicitudYaEvaluadaNoSeConfirmaDeNuevo() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, false);
        String version = versionDe(s.codigo(), token);
        assertThat(enviar("POST", ruta(s.codigo()) + "/confirmacion", cuerpoConfirmacion(e.seccion3A()), token, version)
                .estado()).isEqualTo(201);

        Respuesta repetida = enviar("POST", ruta(s.codigo()) + "/confirmacion", cuerpoConfirmacion(e.seccion3A()), token, version);

        assertThat(repetida.estado()).isEqualTo(409);
        assertThat(vacantes(e.seccion3A())).as("la vacante se descuenta una sola vez").isEqualTo(3);
        assertThat(matriculas(s)).isEqualTo(1);
    }

    // ---- FA-1 a FA-5 ---------------------------------------------------------------------------------------

    @Test
    void fa1NoAdmitidoBloqueaYSeRechazaPorNoAdmitido() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, e.grado3(), "NUEVO_INGRESO", "NO_ADMITIDO", "COMPLETA");

        Respuesta bloqueo = confirmar(s, e.seccion3A(), token);
        assertThat(bloqueo.estado()).isEqualTo(422);
        assertThat(bloqueo.cuerpo().get("codigo").asString()).isEqualTo("NO_ADMITIDO");
        assertThat(bloqueo.cuerpo().get("motivoSugerido").asString()).isEqualTo("NO_ADMITIDO");

        Respuesta r = rechazar(s, "NO_ADMITIDO", token);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("estadoVisible").asString()).isEqualTo("Rechazada por No Admitido");
        assertThat(estado(s)).isEqualTo("RECHAZADA");
        assertThat(matriculas(s)).isZero();
        assertThat(vacantes(e.seccion3A())).isEqualTo(4);
        verify(notificador, timeout(3000)).enviar(eq(s.correoApoderado()), contains("Rechazada por No Admitido"), anyString());
    }

    @Test
    void fa2MatriculaActivaBloqueaYSeRechazaPorDuplicidad() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, false);
        datos.matricularYa(e, s, e.seccion3B());

        Respuesta bloqueo = confirmar(s, e.seccion3A(), token);
        assertThat(bloqueo.estado()).isEqualTo(409);
        assertThat(bloqueo.cuerpo().get("codigo").asString()).isEqualTo("MATRICULA_DUPLICADA");

        Respuesta r = rechazar(s, "DUPLICIDAD", token);

        assertThat(r.cuerpo().get("estadoVisible").asString()).isEqualTo("Rechazada por Duplicidad");
        assertThat(estado(s)).isEqualTo("RECHAZADA");
        assertThat(vacantes(e.seccion3A())).isEqualTo(4);
    }

    @Test
    void fa3DeudaVencidaDeUnAnioAnteriorBloqueaYSeRechazaPorDeudasConElImporte() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, false);
        String deuda = datos.deudaDelAnioAnterior(e, s);

        var detalle = get(ruta(s.codigo()), token).cuerpo();
        assertThat(detalle.get("puedeConfirmar").asBoolean()).isFalse();
        assertThat(detalle.get("validaciones").toString()).contains("S/ " + deuda);

        Respuesta bloqueo = confirmar(s, e.seccion3A(), token);
        assertThat(bloqueo.estado()).isEqualTo(422);
        assertThat(bloqueo.cuerpo().get("codigo").asString()).isEqualTo("DEUDA_VENCIDA");

        Respuesta r = rechazar(s, "DEUDAS", token);

        assertThat(r.cuerpo().get("estadoVisible").asString()).isEqualTo("Rechazada por Deudas");
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(notificador, timeout(3000)).enviar(eq(s.correoApoderado()), contains("Rechazada por Deudas"), cuerpo.capture());
        assertThat(cuerpo.getValue()).contains("S/ " + deuda);
        assertThat(matriculas(s)).isZero();
    }

    @Test
    void laDeudaNoBloqueaAUnNuevoIngreso() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, true);
        datos.deudaDelAnioAnterior(e, s);

        assertThat(confirmar(s, e.seccion3A(), token).estado()).isEqualTo(201);
    }

    @Test
    void fa4SinVacantesLaConfirmacionDaSinVacanteYSePuedeRechazarSinVacante() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(0);
        jdbc.update("UPDATE academico.seccion SET vacantes_disponibles = 0 WHERE id = ?", e.seccion3B());
        Solicitud s = datos.solicitud(e, false);

        Respuesta bloqueo = confirmar(s, e.seccion3A(), token);
        assertThat(bloqueo.estado()).isEqualTo(409);
        assertThat(bloqueo.cuerpo().get("codigo").asString()).isEqualTo("SIN_VACANTE");
        assertThat(estado(s)).isEqualTo("PENDIENTE_REVISION");

        Respuesta r = rechazar(s, "SIN_VACANTE", token);

        assertThat(r.cuerpo().get("estadoVisible").asString()).isEqualTo("Rechazada sin Vacante");
        assertThat(matriculas(s)).isZero();
    }

    @Test
    void fa5ElDesistimientoSeRechazaSinTocarVacantesNiDeudas() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, false);

        Respuesta r = rechazar(s, "VOLUNTAD_APODERADO", token);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("estadoVisible").asString()).isEqualTo("Rechazada por Voluntad del Apoderado");
        assertThat(estado(s)).isEqualTo("RECHAZADA");
        assertThat(vacantes(e.seccion3A())).isEqualTo(4);
        assertThat(contar("SELECT count(*) FROM pagos.obligacion_pago WHERE alumno_id = ?", s.alumnoId())).isZero();
        assertThat(jdbc.queryForObject("SELECT observacion FROM matricula.solicitud_matricula WHERE id = ?",
                String.class, s.id())).isEqualTo("Observación de prueba");
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = "
                + "'RECHAZAR_SOLICITUD_MATRICULA' AND id_entidad_afectada = ?", s.id())).isEqualTo(1);
    }

    @Test
    void unMotivoQueLasValidacionesNoRespaldanSeRechaza() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, false);

        for (String motivo : new String[] {"NO_ADMITIDO", "DUPLICIDAD", "DEUDAS", "SIN_VACANTE"}) {
            Respuesta r = rechazar(s, motivo, token);
            assertThat(r.estado()).as(motivo).isEqualTo(400);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("SOLICITUD_INVALIDA");
        }
        assertThat(estado(s)).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    void conLaDocumentacionIncompletaNoSePuedeConfirmar() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, e.grado3(), "RECURRENTE", "NO_APLICA", "INCOMPLETA");

        Respuesta r = confirmar(s, e.seccion3A(), token);

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("DOCUMENTACION_INCOMPLETA");
    }

    @Test
    void elAdministradorRegistraLaAdmisionDeUnNuevoIngreso() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, e.grado3(), "NUEVO_INGRESO", "NO_APLICA", "COMPLETA");
        assertThat(get(ruta(s.codigo()), token).cuerpo().get("puedeConfirmar").asBoolean()).isFalse();

        Respuesta r = enviar("PUT", ruta(s.codigo()) + "/evaluacion",
                json.writeValueAsString(Map.of("tipoEstudiante", "NUEVO_INGRESO", "estadoAdmision", "ADMITIDO")), token,
                versionDe(s.codigo(), token));

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("puedeConfirmar").asBoolean()).isTrue();
        assertThat(r.cuerpo().get("estadoAdmision").asString()).isEqualTo("ADMITIDO");
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = "
                + "'REGISTRAR_EVALUACION_MATRICULA' AND id_entidad_afectada = ?", s.id())).isEqualTo(1);
    }

    // ---- Transacción única ---------------------------------------------------------------------------------

    @Test
    void siFallaLaGeneracionDeObligacionesSeRevierteTodoYNoSaleElCorreo() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, false);
        long auditadas = contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'CONFIRMAR_MATRICULA'");
        doThrow(new IllegalStateException("fallo simulado")).when(planDeCobros)
                .generarObligaciones(anyString(), anyString(), anyString(), anyString(), anyBoolean());

        Respuesta r = confirmar(s, e.seccion3A(), token);

        assertThat(r.estado()).isEqualTo(500);
        assertThat(vacantes(e.seccion3A())).as("la vacante vuelve").isEqualTo(4);
        assertThat(matriculas(s)).isZero();
        assertThat(contar("SELECT count(*) FROM pagos.obligacion_pago WHERE alumno_id = ?", s.alumnoId())).isZero();
        assertThat(estado(s)).isEqualTo("PENDIENTE_REVISION");
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'CONFIRMAR_MATRICULA'"))
                .as("no se audita lo que se revirtió").isEqualTo(auditadas);
        Thread.sleep(300);
        verify(notificador, never()).enviar(eq(s.correoApoderado()), anyString(), anyString());
    }

    // ---- Bandeja, permisos ---------------------------------------------------------------------------------

    @Test
    void laBandejaFiltraPorEstadoYPorTextoYPagina() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(4);
        Solicitud a = datos.solicitud(e, false);
        Solicitud b = datos.solicitud(e, true);
        assertThat(rechazar(b, "VOLUNTAD_APODERADO", token).estado()).isEqualTo(200);

        var pendientes = get("/api/v1/solicitudes-matricula?estado=PENDIENTE_REVISION&q=" + a.codigo(), token).cuerpo();
        assertThat(pendientes.get("contenido")).hasSize(1);
        assertThat(pendientes.get("contenido").get(0).get("codigo").asString()).isEqualTo(a.codigo());
        assertThat(pendientes.get("totalElementos").asInt()).isEqualTo(1);

        var rechazadas = get("/api/v1/solicitudes-matricula?estado=RECHAZADA&q=" + b.codigo(), token).cuerpo();
        assertThat(rechazadas.get("contenido").get(0).get("estadoVisible").asString())
                .isEqualTo("Rechazada por Voluntad del Apoderado");

        String sufijo = a.correoApoderado().substring(3, 11);
        var porNombre = get("/api/v1/solicitudes-matricula?q=Alumno%20Prueba%20" + sufijo, token).cuerpo();
        assertThat(porNombre.get("contenido")).hasSize(1);
        assertThat(porNombre.get("contenido").get(0).get("codigo").asString()).isEqualTo(a.codigo());

        var paginada = get("/api/v1/solicitudes-matricula?size=1&page=0", token).cuerpo();
        assertThat(paginada.get("contenido")).hasSize(1);
        assertThat(paginada.get("totalPaginas").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void soloQuienEvaluaYRegistraMatriculasPuedeUsarLosEndpoints() throws Exception {
        Escenario e = datos.crearEscenario(4);
        Solicitud s = datos.solicitud(e, false);
        assertThat(get("/api/v1/solicitudes-matricula", null).estado()).isEqualTo(401);
        for (String rol : new String[] {"ALUMNO", "APODERADO", "DOCENTE"}) {
            String token = tokenDe(crearUsuario(rol, true));
            assertThat(get("/api/v1/solicitudes-matricula", token).estado()).as(rol).isEqualTo(403);
            assertThat(get(ruta(s.codigo()), token).estado()).as(rol).isEqualTo(403);
            assertThat(enviar("POST", ruta(s.codigo()) + "/confirmacion", cuerpoConfirmacion(e.seccion3A()), token, "\"0\"")
                    .estado()).as(rol).isEqualTo(403);
        }
        assertThat(get(ruta("MAT-0000-00000"), tokenAdministrador()).estado()).isEqualTo(404);
    }
}
