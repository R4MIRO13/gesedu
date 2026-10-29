package pe.edu.gesedu.pagos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.DatosMatricula.AnioNuevo;
import pe.edu.gesedu.matricula.BaseMatriculaIT;

/** Configuración de cobros del año (RF-06): lectura, guardado completo, validaciones, permisos y auditoría. */
class ConfiguracionCobrosIT extends BaseMatriculaIT {

    private String ruta(AnioNuevo a) {
        return "/api/v1/anios-escolares/" + a.anio() + "/cobros";
    }

    private Map<String, Object> linea(String concepto, String periodo, String monto, String vence) {
        return Map.of("concepto", concepto, "periodo", periodo, "monto", monto, "fechaVencimiento", vence);
    }

    /** Matrícula y pensiones de marzo a diciembre. */
    private List<Map<String, Object>> cronograma(int anio, String monto) {
        List<Map<String, Object>> lineas = new ArrayList<>();
        lineas.add(linea("MATRICULA", String.valueOf(anio), monto, anio + "-02-15"));
        for (int mes = 3; mes <= 12; mes++) {
            lineas.add(linea("PENSION", "%d-%02d".formatted(anio, mes), monto, "%d-%02d-28".formatted(anio, mes)));
        }
        return lineas;
    }

    private String cuerpo(String penalidad, List<Map<String, Object>> lineas) throws Exception {
        return json.writeValueAsString(Map.of("porcentajePenalidad", penalidad, "lineas", lineas));
    }

    @Test
    void unAnioSinCobrosSeMuestraSinConfigurar() throws Exception {
        AnioNuevo a = datos.crearAnioSinCobros();

        Respuesta r = get(ruta(a), tokenAdministrador());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("configurado").asBoolean()).isFalse();
        assertThat(r.cuerpo().get("lineas")).isEmpty();
    }

    @Test
    void guardaElCronogramaYLaPoliticaYLosMontosViajanComoTexto() throws Exception {
        AnioNuevo a = datos.crearAnioSinCobros();
        String token = tokenAdministrador();

        Respuesta guardado = enviar("PUT", ruta(a), cuerpo("5.00", cronograma(a.anio(), "350.00")), token, null);

        assertThat(guardado.estado()).isEqualTo(200);
        assertThat(guardado.cuerpo().get("configurado").asBoolean()).isTrue();
        assertThat(guardado.cuerpo().get("lineas")).hasSize(11);
        assertThat(guardado.cuerpo().get("lineas").get(0).get("monto").asString()).isEqualTo("350.00");
        assertThat(guardado.cuerpo().get("lineas").get(0).get("concepto").asString()).isEqualTo("MATRICULA");
        assertThat(contar("SELECT count(*) FROM pagos.cronograma_cobro WHERE anio_escolar_id = ?", a.id())).isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT porcentaje_penalidad FROM pagos.politica_cobro WHERE anio_escolar_id = ?",
                java.math.BigDecimal.class, a.id())).isEqualByComparingTo("5.00");
    }

    @Test
    void guardarDeNuevoReemplazaLoAnteriorYEliminaLasLineasAusentes() throws Exception {
        AnioNuevo a = datos.crearAnioSinCobros();
        String token = tokenAdministrador();
        enviar("PUT", ruta(a), cuerpo("5.00", cronograma(a.anio(), "350.00")), token, null);

        List<Map<String, Object>> menos = new ArrayList<>(cronograma(a.anio(), "400.50"));
        menos.removeLast();
        Respuesta r = enviar("PUT", ruta(a), cuerpo("7.50", menos), token, null);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("lineas")).hasSize(10);
        assertThat(r.cuerpo().get("lineas").get(1).get("monto").asString()).isEqualTo("400.50");
        assertThat(r.cuerpo().get("porcentajePenalidad").decimalValue()).isEqualByComparingTo("7.50");
        assertThat(contar("SELECT count(*) FROM pagos.cronograma_cobro WHERE anio_escolar_id = ?", a.id())).isEqualTo(10);
    }

    @Test
    void rechazaDatosInvalidos() throws Exception {
        AnioNuevo a = datos.crearAnioSinCobros();
        String token = tokenAdministrador();

        assertThat(enviar("PUT", ruta(a), cuerpo("101", cronograma(a.anio(), "350.00")), token, null).estado())
                .as("penalidad mayor a 100").isEqualTo(400);
        assertThat(enviar("PUT", ruta(a), cuerpo("5", List.of()), token, null).estado()).as("sin líneas").isEqualTo(400);
        assertThat(enviar("PUT", ruta(a), cuerpo("5", List.of(linea("MATRICULA", String.valueOf(a.anio()), "-1", a.anio() + "-02-15"))),
                token, null).estado()).as("monto negativo").isEqualTo(400);
        assertThat(enviar("PUT", ruta(a), cuerpo("5", List.of(linea("PENSION", "2001-03", "10.00", a.anio() + "-03-28"))),
                token, null).cuerpo().get("codigo").asString()).as("periodo de otro año").isEqualTo("VALIDACION_INVALIDA");
        Respuesta repetida = enviar("PUT", ruta(a), cuerpo("5", List.of(
                linea("MATRICULA", String.valueOf(a.anio()), "10.00", a.anio() + "-02-15"),
                linea("MATRICULA", String.valueOf(a.anio()), "20.00", a.anio() + "-02-16"))), token, null);
        assertThat(repetida.estado()).as("línea repetida").isEqualTo(400);
        assertThat(contar("SELECT count(*) FROM pagos.cronograma_cobro WHERE anio_escolar_id = ?", a.id())).isZero();
    }

    @Test
    void unAnioInexistenteEs404YSoloElAdministradorConfigura() throws Exception {
        AnioNuevo a = datos.crearAnioSinCobros();

        assertThat(get("/api/v1/anios-escolares/1999/cobros", tokenAdministrador()).estado()).isEqualTo(404);
        for (String rol : List.of("APODERADO", "DOCENTE", "ALUMNO")) {
            String token = tokenDe(crearUsuario(rol, true));
            assertThat(get(ruta(a), token).estado()).as(rol + " consulta").isEqualTo(403);
            assertThat(enviar("PUT", ruta(a), cuerpo("5", cronograma(a.anio(), "1.00")), token, null).estado())
                    .as(rol + " guarda").isEqualTo(403);
        }
        assertThat(get(ruta(a), null).estado()).isEqualTo(401);
    }

    @Test
    void cadaGuardadoQuedaAuditadoConLosCambios() throws Exception {
        AnioNuevo a = datos.crearAnioSinCobros();
        String token = tokenAdministrador();
        enviar("PUT", ruta(a), cuerpo("5.00", cronograma(a.anio(), "350.00")), token, null);
        enviar("PUT", ruta(a), cuerpo("6.00", cronograma(a.anio(), "360.00")), token, null);

        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'CONFIGURAR_COBROS' "
                + "AND id_entidad_afectada = ?", String.valueOf(a.anio()))).isEqualTo(2);
        assertThat(contar("SELECT count(*) FROM auditoria.cambio_auditoria c JOIN auditoria.registro_auditoria r "
                + "ON r.id = c.registro_auditoria_id WHERE r.accion = 'CONFIGURAR_COBROS' AND r.id_entidad_afectada = ? "
                + "AND c.campo = 'porcentajePenalidad' AND c.valor_anterior = '5.00' AND c.valor_nuevo = '6.00'",
                String.valueOf(a.anio()))).isEqualTo(1);
    }
}
