package pe.edu.gesedu.pagos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.matricula.BaseMatriculaIT;

/** RF-08, S-11: el número de operación es único entre comprobantes pendientes o aprobados. */
class NumeroOperacionUnicoIT extends BaseMatriculaIT {

    @Test
    void unNumeroYaPresentadoEnOtraObligacionSeRechaza() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        String abril = f.pension(datos, 4);
        presentarComprobante(f.apoderado().token(), marzo, comprobante(f.apoderado().token(), marzo, "00009001", "350.00"));

        Respuesta r = presentarComprobante(f.apoderado().token(), abril, comprobante(f.apoderado().token(), abril, "00009001", "350.00"));

        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("NUMERO_OPERACION_DUPLICADO");
        assertThat(contar("SELECT count(*) FROM pagos.comprobante_pago WHERE numero_operacion = '00009001'")).isEqualTo(1);
    }

    @Test
    void unNumeroAprobadoSigueOcupadoPeroUnoObservadoORechazadoSeLibera() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        String abril = f.pension(datos, 4);
        String mayo = f.pension(datos, 5);
        presentarComprobante(f.apoderado().token(), marzo, comprobante(f.apoderado().token(), marzo, "00009002", "350.00"));
        jdbc.update("UPDATE pagos.comprobante_pago SET estado_revision = 'APROBADO' WHERE obligacion_id = ?", marzo);

        assertThat(presentarComprobante(f.apoderado().token(), abril, comprobante(f.apoderado().token(), abril, "00009002", "350.00"))
                .cuerpo().get("codigo").asString()).isEqualTo("NUMERO_OPERACION_DUPLICADO");

        jdbc.update("UPDATE pagos.comprobante_pago SET estado_revision = 'RECHAZADO' WHERE obligacion_id = ?", marzo);
        assertThat(presentarComprobante(f.apoderado().token(), abril, comprobante(f.apoderado().token(), abril, "00009002", "350.00")).estado())
                .isEqualTo(201);

        jdbc.update("UPDATE pagos.comprobante_pago SET estado_revision = 'OBSERVADO' WHERE obligacion_id = ?", abril);
        assertThat(presentarComprobante(f.apoderado().token(), mayo, comprobante(f.apoderado().token(), mayo, "00009002", "350.00")).estado())
                .isEqualTo(201);
    }

    @Test
    void cuatroEnviosSimultaneosDelMismoNumeroDejanUnSoloComprobante() throws Exception {
        Familia f = crearFamilia();
        List<String> obligaciones = List.of(f.pension(datos, 3), f.pension(datos, 4), f.pension(datos, 5), f.pension(datos, 6));
        List<Map<String, Object>> cuerpos = new ArrayList<>();
        for (String o : obligaciones) {
            cuerpos.add(comprobante(f.apoderado().token(), o, "00009003", "350.00"));
        }

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int n = i;
                futuros.add(pool.submit(() -> presentarComprobante(f.apoderado().token(), obligaciones.get(n), cuerpos.get(n)).estado()));
            }
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> futuro : futuros) {
                estados.add(futuro.get());
            }
            assertThat(estados.stream().filter(s -> s == 201)).hasSize(1);
            assertThat(estados.stream().filter(s -> s == 409)).hasSize(3);
        } finally {
            pool.shutdownNow();
        }
        assertThat(contar("SELECT count(*) FROM pagos.comprobante_pago WHERE numero_operacion = '00009003'")).isEqualTo(1);
    }

    @Test
    void dosEnviosSimultaneosParaLaMismaObligacionDejanUnSoloPendiente() throws Exception {
        Familia f = crearFamilia();
        String marzo = f.pension(datos, 3);
        List<Map<String, Object>> cuerpos = List.of(comprobante(f.apoderado().token(), marzo, "00009011", "350.00"),
                comprobante(f.apoderado().token(), marzo, "00009012", "350.00"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (Map<String, Object> cuerpo : cuerpos) {
                futuros.add(pool.submit(() -> presentarComprobante(f.apoderado().token(), marzo, cuerpo).estado()));
            }
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> futuro : futuros) {
                estados.add(futuro.get());
            }
            assertThat(estados).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(contar("SELECT count(*) FROM pagos.comprobante_pago WHERE obligacion_id = ? AND estado_revision = 'PENDIENTE'",
                marzo)).isEqualTo(1);
    }
}
