package pe.edu.gesedu.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;

import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;
import pe.edu.gesedu.apoyo.DatosMatricula.Solicitud;

/**
 * RNF-09: varias confirmaciones simultáneas sobre la última vacante; solo una tiene éxito. Las demás reciben 409
 * SIN_VACANTE, la vacante nunca queda negativa y las que fallan no dejan matrícula ni compromisos.
 */
class VacantesConcurrentesIT extends BaseMatriculaIT {

    private static final int HILOS = 20;

    @RepeatedTest(3)
    void veinteHilosSobreUnaVacanteUnoGanaYDiecinueveSinVacante() throws Exception {
        String token = tokenAdministrador();
        Escenario e = datos.crearEscenario(1);
        List<Solicitud> solicitudes = new ArrayList<>();
        List<String> versiones = new ArrayList<>();
        for (int i = 0; i < HILOS; i++) {
            Solicitud s = datos.solicitud(e, false);
            solicitudes.add(s);
            versiones.add(etag(get(ruta(s.codigo()), token)));
        }

        ExecutorService hilos = Executors.newFixedThreadPool(HILOS);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Respuesta>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < HILOS; i++) {
                Solicitud s = solicitudes.get(i);
                String version = versiones.get(i);
                Callable<Respuesta> tarea = () -> {
                    salida.await();
                    return enviar("POST", ruta(s.codigo()) + "/confirmacion", cuerpoConfirmacion(e.seccion3A()), token, version);
                };
                futuros.add(hilos.submit(tarea));
            }
            salida.countDown();
            List<Respuesta> respuestas = new ArrayList<>();
            for (Future<Respuesta> futuro : futuros) {
                respuestas.add(futuro.get(60, TimeUnit.SECONDS));
            }

            assertThat(respuestas.stream().filter(r -> r.estado() == 201)).hasSize(1);
            assertThat(respuestas.stream().filter(r -> r.estado() == 409)
                    .map(r -> r.cuerpo().get("codigo").asString())).hasSize(HILOS - 1).containsOnly("SIN_VACANTE");
        } finally {
            hilos.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT vacantes_disponibles FROM academico.seccion WHERE id = ?", Integer.class,
                e.seccion3A())).isZero();
        assertThat(contar("SELECT count(*) FROM matricula.matricula WHERE seccion_id = ?", e.seccion3A())).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM matricula.solicitud_matricula WHERE anio_escolar_id = ? AND estado = 'APROBADA'",
                e.anioId())).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM pagos.obligacion_pago WHERE anio_escolar_id = ?", e.anioId())).isEqualTo(11);
    }
}
