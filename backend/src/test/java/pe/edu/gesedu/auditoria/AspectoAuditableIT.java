package pe.edu.gesedu.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.IllegalTransactionStateException;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.ServiciosPrueba;
import pe.edu.gesedu.seguridad.aplicacion.UsuarioAutenticado;

/** El aspecto {@code @Auditable} escribe en la misma transacción de la operación (RF-49, DA-06). */
class AspectoAuditableIT extends BaseIT {

    @Autowired
    private ServiciosPrueba servicios;

    @Autowired
    private ServiciosPrueba.Revierte revierte;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registraLaOperacionConSusCambiosYElIdDeLaEntidad() {
        String nombre = UUID.randomUUID().toString();

        servicios.crear(nombre);

        List<Map<String, Object>> registros = registros("cosa-" + nombre);
        assertThat(registros).hasSize(1);
        Map<String, Object> registro = registros.getFirst();
        assertThat(registro.get("accion")).isEqualTo("PRUEBA_CREAR");
        assertThat(registro.get("modulo")).isEqualTo("PRUEBAS");
        assertThat(registro.get("tipo_entidad")).isEqualTo("Cosa");
        assertThat(registro.get("descripcion")).isEqualTo("PRUEBA_CREAR Cosa cosa-" + nombre);
        assertThat(registro.get("usuario_id")).as("sin sesión el actor es «Sistema»").isNull();

        List<Map<String, Object>> cambios = jdbc.queryForList(
                "SELECT campo, valor_anterior, valor_nuevo FROM auditoria.cambio_auditoria "
                        + "WHERE registro_auditoria_id = ? ORDER BY campo", registro.get("id"));
        assertThat(cambios).hasSize(2);
        assertThat(cambios.get(0)).containsEntry("campo", "cantidad").containsEntry("valor_anterior", null)
                .containsEntry("valor_nuevo", "2");
        assertThat(cambios.get(1)).containsEntry("campo", "nombre").containsEntry("valor_nuevo", nombre);
    }

    @Test
    void atribuyeLaOperacionAlUsuarioAutenticado() {
        String usuarioId = idDe(crearUsuario("ADMINISTRADOR", true));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new UsuarioAutenticado(usuarioId, "Prueba", "ADMINISTRADOR"), null, List.of()));
        String nombre = UUID.randomUUID().toString();

        servicios.crear(nombre);

        assertThat(registros("cosa-" + nombre).getFirst().get("usuario_id")).isEqualTo(usuarioId);
    }

    @Test
    void siLaOperacionFallaNoSeAudita() {
        long antes = total("PRUEBA_FALLA");

        assertThatThrownBy(() -> servicios.falla()).isInstanceOf(IllegalStateException.class);

        assertThat(total("PRUEBA_FALLA")).isEqualTo(antes);
    }

    @Test
    void siSeRevierteLaTransaccionTambienSeRevierteElRegistro() {
        String nombre = UUID.randomUUID().toString();

        assertThatThrownBy(() -> revierte.auditarYRevertir(nombre)).isInstanceOf(IllegalStateException.class);

        assertThat(registros("cosa-" + nombre)).isEmpty();
    }

    @Test
    void laCondicionDecideSiSeAudita() {
        String id = UUID.randomUUID().toString();

        servicios.condicional(id, false);
        assertThat(registros(id)).isEmpty();

        servicios.condicional(id, true);
        assertThat(registros(id)).hasSize(1);
    }

    @Test
    void sinTransaccionElRegistroSeRechazaEnVezDePerderse() {
        assertThatThrownBy(() -> servicios.sinTransaccion()).isInstanceOf(IllegalTransactionStateException.class);
    }

    private List<Map<String, Object>> registros(String idEntidad) {
        return jdbc.queryForList("SELECT * FROM auditoria.registro_auditoria WHERE id_entidad_afectada = ?",
                idEntidad);
    }

    private long total(String accion) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = ?",
                Long.class, accion);
    }
}
