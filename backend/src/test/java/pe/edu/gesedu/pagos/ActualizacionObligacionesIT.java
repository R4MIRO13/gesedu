package pe.edu.gesedu.pagos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;
import pe.edu.gesedu.matricula.BaseMatriculaIT;
import pe.edu.gesedu.pagos.aplicacion.VencimientosService;

/** RF-07 (C-43, S-28): al guardar el cronograma se actualizan las obligaciones PENDIENTES del año y solo ellas. */
class ActualizacionObligacionesIT extends BaseMatriculaIT {

    @Autowired
    private PlatformTransactionManager gestorTransacciones;

    @Autowired
    private VencimientosService vencimientos;

    private String ruta(Escenario e) {
        return "/api/v1/anios-escolares/" + e.anio() + "/cobros";
    }

    /** Lee el cronograma vigente del año, lo modifica con {@code cambio} y lo guarda con una PUT. */
    private Respuesta guardarCronograma(Escenario e, String token, java.util.function.Consumer<List<Map<String, Object>>> cambio)
            throws Exception {
        Respuesta actual = get(ruta(e), token);
        List<Map<String, Object>> lineas = new ArrayList<>();
        actual.cuerpo().get("lineas").forEach(l -> lineas.add(new java.util.HashMap<>(Map.of(
                "concepto", l.get("concepto").asString(), "periodo", l.get("periodo").asString(),
                "monto", l.get("monto").asString(), "fechaVencimiento", l.get("fechaVencimiento").asString()))));
        cambio.accept(lineas);
        return enviar("PUT", ruta(e), json.writeValueAsString(Map.of(
                "porcentajePenalidad", actual.cuerpo().get("porcentajePenalidad").asString(), "lineas", lineas)), token, null);
    }

    private static void poner(List<Map<String, Object>> lineas, String concepto, String periodo, String monto, String vence) {
        for (Map<String, Object> l : lineas) {
            if (l.get("concepto").equals(concepto) && l.get("periodo").equals(periodo)) {
                l.put("monto", monto);
                l.put("fechaVencimiento", vence);
            }
        }
    }

    private String monto(String obligacionId) {
        return jdbc.queryForObject("SELECT monto_base::text FROM pagos.obligacion_pago WHERE id = ?", String.class, obligacionId);
    }

    private String vence(String obligacionId) {
        return jdbc.queryForObject("SELECT fecha_vencimiento::text FROM pagos.obligacion_pago WHERE id = ?", String.class, obligacionId);
    }

    @Test
    void lasPendientesTomanElMontoYElVencimientoNuevosYLasPagadasYVencidasNoCambian() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();
        String marzo = f.pension(datos, 3);
        String abril = f.pension(datos, 4);
        String mayo = f.pension(datos, 5);
        String junio = f.pension(datos, 6);
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'PAGADA' WHERE id = ?", marzo);
        jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'VENCIDA', monto_penalidad = 17.50 WHERE id = ?", abril);
        String admin = tokenAdministrador();

        Respuesta r = guardarCronograma(e, admin, lineas -> {
            poner(lineas, "PENSION", e.anio() + "-03", "400.00", e.anio() + "-03-10");
            poner(lineas, "PENSION", e.anio() + "-04", "400.00", e.anio() + "-04-10");
            poner(lineas, "PENSION", e.anio() + "-05", "400.00", e.anio() + "-05-10");
            poner(lineas, "PENSION", e.anio() + "-06", "425.50", e.anio() + "-06-12");
        });

        assertThat(r.estado()).isEqualTo(200);
        // Solo mayo y junio estaban PENDIENTES (y el resto del cronograma no cambió)
        assertThat(r.cuerpo().get("obligacionesActualizadas").asInt()).isEqualTo(2);
        assertThat(monto(mayo)).isEqualTo("400.00");
        assertThat(vence(mayo)).isEqualTo(e.anio() + "-05-10");
        assertThat(monto(junio)).isEqualTo("425.50");
        assertThat(vence(junio)).isEqualTo(e.anio() + "-06-12");
        assertThat(monto(marzo)).as("pagada").isEqualTo("350.00");
        assertThat(vence(marzo)).isEqualTo(e.anio() + "-03-05");
        assertThat(monto(abril)).as("vencida").isEqualTo("350.00");
        assertThat(vence(abril)).isEqualTo(e.anio() + "-04-05");
        assertThat(jdbc.queryForObject("SELECT monto_penalidad::text FROM pagos.obligacion_pago WHERE id = ?", String.class, abril))
                .isEqualTo("17.50");
        // El cronograma, en cambio, sí guarda los valores nuevos para las próximas matrículas
        assertThat(jdbc.queryForObject("SELECT monto::text FROM pagos.cronograma_cobro WHERE anio_escolar_id = ? "
                + "AND periodo = ?", String.class, e.anioId(), e.anio() + "-03")).isEqualTo("400.00");
    }

    @Test
    void guardarDeNuevoElMismoCronogramaNoCambiaNada() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();
        String admin = tokenAdministrador();
        guardarCronograma(e, admin, lineas -> poner(lineas, "PENSION", e.anio() + "-07", "380.00", e.anio() + "-07-08"));
        String julio = f.pension(datos, 7);
        long version = jdbc.queryForObject("SELECT version FROM pagos.obligacion_pago WHERE id = ?", Long.class, julio);
        String actualizadaEn = jdbc.queryForObject("SELECT updated_at::text FROM pagos.obligacion_pago WHERE id = ?", String.class, julio);

        Respuesta repetida = guardarCronograma(e, admin, lineas -> { });

        assertThat(repetida.estado()).isEqualTo(200);
        assertThat(repetida.cuerpo().get("obligacionesActualizadas").asInt()).isZero();
        assertThat(monto(julio)).isEqualTo("380.00");
        assertThat(jdbc.queryForObject("SELECT version FROM pagos.obligacion_pago WHERE id = ?", Long.class, julio)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT updated_at::text FROM pagos.obligacion_pago WHERE id = ?", String.class, julio))
                .isEqualTo(actualizadaEn);
    }

    @Test
    void todasLasMatriculasDelAnioSeActualizanYLasDeOtroAnioNo() throws Exception {
        Familia f1 = crearFamilia();
        Escenario e = f1.escenario();
        String alumno2 = datos.alumnoDe(f1.apoderado().apoderadoId(), "Segundo");
        String matricula2 = datos.matriculaConObligaciones(e, f1.apoderado().apoderadoId(), alumno2, e.seccion3B());
        Familia otroAnio = crearFamilia();

        Respuesta r = guardarCronograma(e, tokenAdministrador(), lineas ->
                poner(lineas, "MATRICULA", String.valueOf(e.anio()), "500.00", e.anio() + "-02-20"));

        assertThat(r.cuerpo().get("obligacionesActualizadas").asInt()).isEqualTo(2);
        assertThat(monto(datos.obligacion(f1.matriculaId(), String.valueOf(e.anio())))).isEqualTo("500.00");
        assertThat(monto(datos.obligacion(matricula2, String.valueOf(e.anio())))).isEqualTo("500.00");
        assertThat(monto(datos.obligacion(otroAnio.matriculaId(), String.valueOf(otroAnio.escenario().anio())))).isEqualTo("350.00");
    }

    @Test
    void unaLineaNuevaNoCreaObligacionesRetroactivasNiUnaLineaEliminadaBorraLasGeneradas() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();
        long antes = contar("SELECT count(*) FROM pagos.obligacion_pago WHERE matricula_id = ?", f.matriculaId());
        String diciembre = f.pension(datos, 12);

        Respuesta r = guardarCronograma(e, tokenAdministrador(), lineas -> {
            lineas.add(Map.of("concepto", "CUOTA_INGRESO", "periodo", String.valueOf(e.anio()), "monto", "200.00",
                    "fechaVencimiento", e.anio() + "-02-15"));
            lineas.removeIf(l -> l.get("periodo").equals(e.anio() + "-12"));
        });

        assertThat(r.estado()).isEqualTo(200);
        assertThat(contar("SELECT count(*) FROM pagos.obligacion_pago WHERE matricula_id = ?", f.matriculaId())).isEqualTo(antes);
        assertThat(contar("SELECT count(*) FROM pagos.obligacion_pago WHERE matricula_id = ? AND concepto = 'CUOTA_INGRESO'",
                f.matriculaId())).isZero();
        assertThat(monto(diciembre)).as("la obligación de una línea eliminada se conserva").isEqualTo("350.00");
        assertThat(r.cuerpo().get("obligacionesActualizadas").asInt()).isZero();
    }

    @Test
    void laPenalidadSeCalculaSobreElNuevoMontoBase() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();
        String marzo = f.pension(datos, 3);
        guardarCronograma(e, tokenAdministrador(), lineas -> poner(lineas, "PENSION", e.anio() + "-03", "400.00", e.anio() + "-03-05"));

        vencimientos.procesar(LocalDate.of(e.anio(), 4, 20));

        assertThat(monto(marzo)).isEqualTo("400.00");
        assertThat(jdbc.queryForObject("SELECT monto_penalidad::text FROM pagos.obligacion_pago WHERE id = ?", String.class, marzo))
                .as("5 % de 400.00").isEqualTo("20.00");
    }

    @Test
    void elGuardadoQuedaAuditadoConLasObligacionesActualizadas() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();

        guardarCronograma(e, tokenAdministrador(), lineas -> poner(lineas, "PENSION", e.anio() + "-08", "410.00", e.anio() + "-08-09"));

        assertThat(contar("SELECT count(*) FROM auditoria.cambio_auditoria c JOIN auditoria.registro_auditoria r "
                + "ON r.id = c.registro_auditoria_id WHERE r.accion = 'CONFIGURAR_COBROS' AND r.id_entidad_afectada = ? "
                + "AND c.campo = 'obligacionesPendientesActualizadas' AND c.valor_nuevo = '1'", String.valueOf(e.anio())))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM auditoria.cambio_auditoria c JOIN auditoria.registro_auditoria r "
                + "ON r.id = c.registro_auditoria_id WHERE r.accion = 'CONFIGURAR_COBROS' AND r.id_entidad_afectada = ? "
                + "AND c.campo = ?", String.valueOf(e.anio()), "obligaciones PENSION " + e.anio() + "-08")).isEqualTo(1);
    }

    @Test
    void unaValidacionDePagoQueTieneBloqueadaLaObligacionNoLaPisaElCambioDeCronograma() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();
        String marzo = f.pension(datos, 3);
        String admin = tokenAdministrador();
        CountDownLatch bloqueada = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // «Validación de pago» en curso: tiene la obligación bloqueada y la dejará PAGADA al confirmar
            Future<?> validacion = pool.submit(() -> new TransactionTemplate(gestorTransacciones).executeWithoutResult(t -> {
                jdbc.queryForObject("SELECT id FROM pagos.obligacion_pago WHERE id = ? FOR UPDATE", String.class, marzo);
                bloqueada.countDown();
                try {
                    liberar.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                jdbc.update("UPDATE pagos.obligacion_pago SET estado = 'PAGADA' WHERE id = ?", marzo);
            }));
            assertThat(bloqueada.await(10, TimeUnit.SECONDS)).isTrue();

            // El cambio de cronograma espera la fila bloqueada
            Future<Respuesta> cambio = pool.submit(() -> guardarCronograma(e, admin, lineas ->
                    poner(lineas, "PENSION", e.anio() + "-03", "999.00", e.anio() + "-03-30")));
            Thread.sleep(500);
            assertThat(cambio.isDone()).as("espera el bloqueo").isFalse();

            liberar.countDown();
            validacion.get(10, TimeUnit.SECONDS);
            assertThat(cambio.get(10, TimeUnit.SECONDS).estado()).isEqualTo(200);
        } finally {
            liberar.countDown();
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT estado FROM pagos.obligacion_pago WHERE id = ?", String.class, marzo)).isEqualTo("PAGADA");
        assertThat(monto(marzo)).as("lo ya pagado conserva su importe").isEqualTo("350.00");
    }

    @Test
    void soloQuienConfiguraCobrosLosCambia() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();

        for (String token : List.of(f.apoderado().token(), tokenDe(crearUsuario("DOCENTE", true)))) {
            assertThat(enviar("PUT", ruta(e), json.writeValueAsString(Map.of("porcentajePenalidad", "5", "lineas", List.of(Map.of(
                    "concepto", "MATRICULA", "periodo", String.valueOf(e.anio()), "monto", "1.00", "fechaVencimiento", e.anio() + "-02-01")))),
                    token, null).estado()).isEqualTo(403);
        }
        assertThat(monto(datos.obligacion(f.matriculaId(), String.valueOf(e.anio())))).isEqualTo("350.00");
    }
}
