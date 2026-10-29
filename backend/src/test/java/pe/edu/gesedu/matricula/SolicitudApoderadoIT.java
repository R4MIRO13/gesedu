package pe.edu.gesedu.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;

/** UC-01, Solicitar matrícula (RF-02, RF-08): flujo básico, FA-1 y FA-2, alcance y seguimiento. */
class SolicitudApoderadoIT extends BaseMatriculaIT {

    private static final String RUTA = "/api/v1/solicitudes-matricula";
    private static final AtomicInteger DOCUMENTOS = new AtomicInteger(60_000_000);

    private Map<String, Object> alumno(String documento) {
        Map<String, Object> a = new HashMap<>();
        a.put("nombres", "Valeria");
        a.put("apellidos", "Torres Gil");
        a.put("documento", documento);
        a.put("fechaNacimiento", "2012-09-09");
        a.put("parentesco", "PADRE");
        return a;
    }

    private String documentoNuevo() {
        return String.valueOf(DOCUMENTOS.getAndIncrement());
    }

    private Map<String, Object> solicitud(Escenario e, String tipo, String documento, List<Map<String, Object>> documentos) {
        Map<String, Object> s = new HashMap<>();
        s.put("anio", e.anio());
        s.put("tipoEstudiante", tipo);
        s.put("gradoId", e.grado3());
        s.put("alumno", alumno(documento));
        s.put("telefonoApoderado", "977100004");
        s.put("documentos", documentos);
        return s;
    }

    private Respuesta enviarSolicitud(Map<String, Object> cuerpo, String token) throws Exception {
        return post(RUTA, json.writeValueAsString(cuerpo), token);
    }

    private long contarAlumnosConDocumento(String documento) {
        return contar("SELECT count(*) FROM seguridad.alumno al JOIN seguridad.usuario u ON u.id = al.usuario_id "
                + "WHERE u.documento_identidad = ?", documento);
    }

    @Test
    void flujoBasicoDeNuevoIngresoRegistraLaSolicitudPendienteConSuCodigo() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String documento = documentoNuevo();
        var certificado = subirDocumento(ap.token(), "CERTIFICADO_ESTUDIOS", "certificado.pdf");

        Respuesta r = enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documento, List.of(certificado)), ap.token());

        assertThat(r.estado()).isEqualTo(201);
        String codigo = r.cuerpo().get("codigo").asString();
        assertThat(codigo).matches("MAT-" + e.anio() + "-\\d{5}");
        assertThat(r.cuerpo().get("estado").asString()).isEqualTo("PENDIENTE_REVISION");
        assertThat(r.cuerpo().get("estadoVisible").asString()).isEqualTo("Pendiente de revisión");
        assertThat(r.cuerpo().get("alumno").asString()).isEqualTo("Valeria Torres Gil");
        assertThat(r.cuerpo().get("grado").asString()).isEqualTo("3°");
        assertThat(r.cuerpo().get("mensaje").asString()).contains(codigo).contains("revisión administrativa");

        // La solicitud, el alumno sin acceso y el documento quedan guardados
        assertThat(jdbc.queryForObject("SELECT estado || '/' || tipo_estudiante || '/' || estado_documentacion "
                + "FROM matricula.solicitud_matricula WHERE codigo_seguimiento = ?", String.class, codigo))
                .isEqualTo("PENDIENTE_REVISION/NUEVO_INGRESO/COMPLETA");
        assertThat(jdbc.queryForObject("SELECT al.codigo_alumno || '/' || u.activo FROM seguridad.alumno al "
                + "JOIN seguridad.usuario u ON u.id = al.usuario_id WHERE u.documento_identidad = ?", String.class,
                documento)).isEqualTo(e.anio() + "-0001/false");
        assertThat(contar("SELECT count(*) FROM matricula.documento_solicitud d JOIN matricula.solicitud_matricula s "
                + "ON s.id = d.solicitud_id WHERE s.codigo_seguimiento = ? AND d.tipo = 'CERTIFICADO_ESTUDIOS'", codigo))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT numero_telefono FROM seguridad.apoderado WHERE id = ?", String.class,
                ap.apoderadoId())).isEqualTo("977100004");
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = "
                + "'REGISTRAR_SOLICITUD_MATRICULA' AND id_entidad_afectada = ?", codigo)).isEqualTo(1);
        // UC-01 no envía correo (C-17)
        org.mockito.Mockito.verifyNoInteractions(notificador);
    }

    @Test
    void laSolicitudRegistradaApareceEnLaBandejaDelAdministrador() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        var ficha = subirDocumento(ap.token(), "FICHA_MATRICULA_ANTERIOR", "ficha.pdf");
        String codigo = enviarSolicitud(solicitud(e, "RECURRENTE", documentoNuevo(), List.of(ficha)), ap.token())
                .cuerpo().get("codigo").asString();

        Respuesta detalle = get(ruta(codigo), tokenAdministrador());

        assertThat(detalle.estado()).isEqualTo(200);
        assertThat(detalle.cuerpo().get("estado").asString()).isEqualTo("PENDIENTE_REVISION");
        assertThat(detalle.cuerpo().get("documentos")).hasSize(1);
        assertThat(detalle.cuerpo().get("apoderado").get("telefono").asString()).isEqualTo("977100004");
    }

    @Test
    void fa1FaltanDocumentosObligatoriosSeResaltanYNoSeGuardaNada() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String documento = documentoNuevo();

        Respuesta sinDocumentos = enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documento, List.of()), ap.token());
        var ficha = subirDocumento(ap.token(), "FICHA_MATRICULA_ANTERIOR", "ficha.pdf");
        Respuesta documentoQueNoEs = enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documento, List.of(ficha)), ap.token());

        for (Respuesta r : List.of(sinDocumentos, documentoQueNoEs)) {
            assertThat(r.estado()).isEqualTo(400);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("VALIDACION_INVALIDA");
            assertThat(r.cuerpo().get("errores").get(0).get("campo").asString()).isEqualTo("documentos.CERTIFICADO_ESTUDIOS");
            assertThat(r.cuerpo().get("errores").get(0).get("mensaje").asString()).contains("certificado de estudios");
        }
        assertThat(contarAlumnosConDocumento(documento)).isZero();
        assertThat(contar("SELECT count(*) FROM matricula.solicitud_matricula WHERE anio_escolar_id = ?", e.anioId())).isZero();
    }

    @Test
    void fa1FaltanCamposObligatoriosYElErrorNombraCadaCampo() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        Map<String, Object> cuerpo = solicitud(e, "NUEVO_INGRESO", documentoNuevo(), List.of());
        Map<String, Object> a = alumno(documentoNuevo());
        a.put("nombres", "");
        a.remove("fechaNacimiento");
        cuerpo.put("alumno", a);

        Respuesta r = enviarSolicitud(cuerpo, ap.token());

        assertThat(r.estado()).isEqualTo(400);
        List<String> campos = new ArrayList<>();
        r.cuerpo().get("errores").forEach(x -> campos.add(x.get("campo").asString()));
        assertThat(campos).contains("alumno.nombres", "alumno.fechaNacimiento");
    }

    @Test
    void fa2UnDocumentoConFormatoInvalidoSeRechaza() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        var certificado = subirDocumento(ap.token(), "CERTIFICADO_ESTUDIOS", "certificado.pdf");

        Respuesta r = enviarSolicitud(solicitud(e, "NUEVO_INGRESO", "12AB", List.of(certificado)), ap.token());

        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.cuerpo().get("errores").get(0).get("campo").asString()).isEqualTo("alumno.documento");
        assertThat(r.cuerpo().get("errores").get(0).get("mensaje").asString()).contains("8 dígitos");
    }

    @Test
    void fa2YaExisteUnaSolicitudEnTramiteConEseDocumento() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String documento = documentoNuevo();
        var c1 = subirDocumento(ap.token(), "CERTIFICADO_ESTUDIOS", "uno.pdf");
        assertThat(enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documento, List.of(c1)), ap.token()).estado()).isEqualTo(201);

        var c2 = subirDocumento(ap.token(), "CERTIFICADO_ESTUDIOS", "dos.pdf");
        Respuesta r = enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documento, List.of(c2)), ap.token());

        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("SOLICITUD_EN_TRAMITE");
        assertThat(r.cuerpo().get("detail").asString())
                .isEqualTo("Ya existe una solicitud en trámite con este DNI para " + e.anio());
        assertThat(contarAlumnosConDocumento(documento)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM matricula.solicitud_matricula WHERE anio_escolar_id = ?", e.anioId())).isEqualTo(1);
    }

    @Test
    void elDocumentoDeUnEstudianteDeOtroApoderadoSeRechazaYNoCreaNada() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta primero = crearApoderadoConCuenta();
        ApoderadoConCuenta segundo = crearApoderadoConCuenta();
        String documento = documentoNuevo();
        var c1 = subirDocumento(primero.token(), "CERTIFICADO_ESTUDIOS", "uno.pdf");
        enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documento, List.of(c1)), primero.token());

        var c2 = subirDocumento(segundo.token(), "CERTIFICADO_ESTUDIOS", "dos.pdf");
        Respuesta r = enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documento, List.of(c2)), segundo.token());

        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("DOCUMENTO_DUPLICADO");
        assertThat(contarAlumnosConDocumento(documento)).isEqualTo(1);
    }

    @Test
    void unAlumnoRecurrenteYaRegistradoSeReutilizaSinDuplicarlo() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String documento = documentoNuevo();
        var c1 = subirDocumento(ap.token(), "CERTIFICADO_ESTUDIOS", "uno.pdf");
        String primera = enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documento, List.of(c1)), ap.token())
                .cuerpo().get("codigo").asString();
        // El administrador rechaza la primera; el apoderado vuelve a presentar el mismo estudiante
        jdbc.update("UPDATE matricula.solicitud_matricula SET estado = 'RECHAZADA', motivo_rechazo = 'VOLUNTAD_APODERADO' "
                + "WHERE codigo_seguimiento = ?", primera);

        var ficha = subirDocumento(ap.token(), "FICHA_MATRICULA_ANTERIOR", "ficha.pdf");
        Respuesta r = enviarSolicitud(solicitud(e, "RECURRENTE", documento, List.of(ficha)), ap.token());

        assertThat(r.estado()).isEqualTo(201);
        assertThat(contarAlumnosConDocumento(documento)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM matricula.solicitud_matricula s JOIN seguridad.alumno al ON al.id = "
                + "s.alumno_id JOIN seguridad.usuario u ON u.id = al.usuario_id WHERE u.documento_identidad = ?",
                documento)).isEqualTo(2);
    }

    @Test
    void sinCostosConfiguradosOConUnAnioCerradoLaSolicitudNoSeAcepta() throws Exception {
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        var certificado = subirDocumento(ap.token(), "CERTIFICADO_ESTUDIOS", "certificado.pdf");
        Escenario e = datos.crearEscenario(4);
        jdbc.update("DELETE FROM pagos.cronograma_cobro WHERE anio_escolar_id = ?", e.anioId());

        Respuesta sinCostos = enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documentoNuevo(), List.of(certificado)), ap.token());
        Escenario cerrado = datos.crearEscenario(4);
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'CERRADO' WHERE id = ?", cerrado.anioId());
        Respuesta anioCerrado = enviarSolicitud(solicitud(cerrado, "NUEVO_INGRESO", documentoNuevo(), List.of(certificado)), ap.token());

        assertThat(sinCostos.estado()).isEqualTo(400);
        assertThat(sinCostos.cuerpo().get("detail").asString()).contains("costos").contains("configurados");
        assertThat(anioCerrado.estado()).isEqualTo(400);
        assertThat(anioCerrado.cuerpo().get("detail").asString()).contains("ya no admite solicitudes");
    }

    @Test
    void unArchivoQueNoSeSubioOQueEsDeOtroApoderadoSeRechaza() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        ApoderadoConCuenta otro = crearApoderadoConCuenta();
        var ajeno = subirDocumento(otro.token(), "CERTIFICADO_ESTUDIOS", "ajeno.pdf");
        Map<String, Object> sinSubir = new HashMap<>(ajeno);
        sinSubir.put("claveObjeto", "solicitudes/" + ap.apoderadoId() + "/00000000-nunca-subido.pdf");

        Respuesta ajenoR = enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documentoNuevo(), List.of(ajeno)), ap.token());
        Respuesta sinSubirR = enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documentoNuevo(), List.of(sinSubir)), ap.token());

        assertThat(ajenoR.estado()).isEqualTo(422);
        assertThat(ajenoR.cuerpo().get("codigo").asString()).isEqualTo("ARCHIVO_INVALIDO");
        assertThat(sinSubirR.estado()).isEqualTo(422);
        assertThat(sinSubirR.cuerpo().get("codigo").asString()).isEqualTo("ARCHIVO_INVALIDO");
    }

    @Test
    void pedirUnaUrlDeSubidaValidaTipoYTamano() throws Exception {
        ApoderadoConCuenta ap = crearApoderadoConCuenta();

        Respuesta ejecutable = post(RUTA + "/archivos", json.writeValueAsString(Map.of("nombreArchivo", "virus.exe",
                "contentType", "application/x-msdownload", "tamanoBytes", 100)), ap.token());
        Respuesta enorme = post(RUTA + "/archivos", json.writeValueAsString(Map.of("nombreArchivo", "grande.pdf",
                "contentType", "application/pdf", "tamanoBytes", 6 * 1024 * 1024)), ap.token());

        assertThat(ejecutable.estado()).isEqualTo(422);
        assertThat(ejecutable.cuerpo().get("codigo").asString()).isEqualTo("ARCHIVO_INVALIDO");
        assertThat(enorme.estado()).isEqualTo(422);
    }

    @Test
    void lasOpcionesTraenLosGradosDelAnioYLosDocumentosQueSeExigen() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();

        Respuesta r = get(RUTA + "/opciones?anio=" + e.anio(), ap.token());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("apoderado").get("nombre").asString()).isEqualTo("Prueba Usuario");
        assertThat(r.cuerpo().get("apoderado").get("correo").asString()).isEqualTo(ap.correo());
        assertThat(r.cuerpo().get("grados")).hasSize(2);
        assertThat(r.cuerpo().get("grados").get(0).get("nombre").asString()).isEqualTo("2°");
        assertThat(r.cuerpo().get("grados").get(1).get("id").asString()).isEqualTo(e.grado3());
        assertThat(r.cuerpo().get("documentosRequeridos").get("NUEVO_INGRESO").get(0).asString()).isEqualTo("CERTIFICADO_ESTUDIOS");
        assertThat(r.cuerpo().get("documentosRequeridos").get("RECURRENTE").get(0).asString()).isEqualTo("FICHA_MATRICULA_ANTERIOR");
        assertThat(r.cuerpo().get("costosConfigurados").asBoolean()).isTrue();
        assertThat(get(RUTA + "/opciones?anio=1999", ap.token()).estado()).isEqualTo(404);
    }

    @Test
    void misSolicitudesMuestraSoloLasPropiasConSuEstado() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        ApoderadoConCuenta otro = crearApoderadoConCuenta();
        var c1 = subirDocumento(ap.token(), "CERTIFICADO_ESTUDIOS", "uno.pdf");
        String codigo = enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documentoNuevo(), List.of(c1)), ap.token())
                .cuerpo().get("codigo").asString();
        var c2 = subirDocumento(otro.token(), "CERTIFICADO_ESTUDIOS", "dos.pdf");
        enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documentoNuevo(), List.of(c2)), otro.token());
        datos.solicitud(e, false); // una solicitud de un tercero

        Respuesta r = get(RUTA + "/mias", ap.token());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo()).hasSize(1);
        assertThat(r.cuerpo().get(0).get("codigo").asString()).isEqualTo(codigo);
        assertThat(r.cuerpo().get(0).get("estadoVisible").asString()).isEqualTo("Pendiente de revisión");
        assertThat(r.cuerpo().get(0).get("grado").asString()).isEqualTo("3°");
        assertThat(r.cuerpo().get(0).get("anio").asInt()).isEqualTo(e.anio());

        jdbc.update("UPDATE matricula.solicitud_matricula SET estado = 'RECHAZADA', motivo_rechazo = 'DEUDAS', "
                + "observacion = 'Regulariza tu deuda' WHERE codigo_seguimiento = ?", codigo);
        Respuesta rechazada = get(RUTA + "/mias", ap.token());
        assertThat(rechazada.cuerpo().get(0).get("estadoVisible").asString()).isEqualTo("Rechazada por Deudas");
        assertThat(rechazada.cuerpo().get(0).get("observacion").asString()).isEqualTo("Regulariza tu deuda");
    }

    @Test
    void soloElApoderadoPresentaSolicitudes() throws Exception {
        Escenario e = datos.crearEscenario(4);
        for (String rol : List.of("ADMINISTRADOR", "DOCENTE", "ALUMNO")) {
            String token = tokenDe(crearUsuario(rol, true));
            assertThat(get(RUTA + "/mias", token).estado()).as(rol + " mias").isEqualTo(403);
            assertThat(get(RUTA + "/opciones?anio=" + e.anio(), token).estado()).as(rol + " opciones").isEqualTo(403);
            assertThat(enviarSolicitud(solicitud(e, "NUEVO_INGRESO", documentoNuevo(), List.of()), token).estado())
                    .as(rol + " registrar").isEqualTo(403);
        }
        assertThat(get(RUTA + "/mias", null).estado()).isEqualTo(401);
        // Una cuenta con rol de apoderado pero sin perfil de apoderado queda fuera de alcance
        String sinPerfil = tokenDe(crearUsuario("APODERADO", true));
        assertThat(get(RUTA + "/mias", sinPerfil).estado()).isEqualTo(403);
    }

    @Test
    void dosEnviosSimultaneosDelMismoDocumentoCreanUnaSolaSolicitud() throws Exception {
        Escenario e = datos.crearEscenario(4);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String documento = documentoNuevo();
        List<Map<String, Object>> cuerpos = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cuerpos.add(solicitud(e, "NUEVO_INGRESO", documento, List.of(subirDocumento(ap.token(), "CERTIFICADO_ESTUDIOS",
                    "c" + i + ".pdf"))));
        }

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (Map<String, Object> cuerpo : cuerpos) {
                Callable<Integer> envio = () -> enviarSolicitud(cuerpo, ap.token()).estado();
                futuros.add(pool.submit(envio));
            }
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> f : futuros) {
                estados.add(f.get());
            }
            assertThat(estados.stream().filter(s -> s == 201)).hasSize(1);
            assertThat(estados.stream().filter(s -> s == 409)).hasSize(3);
        } finally {
            pool.shutdownNow();
        }
        assertThat(contarAlumnosConDocumento(documento)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM matricula.solicitud_matricula WHERE anio_escolar_id = ?", e.anioId())).isEqualTo(1);
    }
}
