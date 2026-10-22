package pe.edu.gesedu.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;

/** Las tablas de auditoría son de solo inserción para la aplicación y para el dueño (RNF-08, DA-06). */
class AuditoriaInmutableIT extends BaseIT {

    @BeforeEach
    void sembrarUnRegistro() {
        jdbc.update("INSERT INTO auditoria.registro_auditoria (id, fecha_hora, accion, modulo, tipo_entidad, descripcion) "
                + "VALUES (?, now(), 'PRUEBA', 'PRUEBAS', 'Cosa', 'registro de prueba')",
                java.util.UUID.randomUUID().toString());
    }

    @Test
    void laAplicacionNoPuedeModificarNiBorrar() throws Exception {
        try (Connection aplicacion = conectar("gesedu_app", "gesedu_app"); Statement sql = aplicacion.createStatement()) {
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE auditoria.registro_auditoria SET accion = 'X'"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM auditoria.registro_auditoria"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE auditoria.cambio_auditoria SET campo = 'X'"))
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void elDuenoTampocoPuedeGraciasAlTrigger() throws Exception {
        try (Connection dueno = conectar("gesedu_migrador", "gesedu_migrador"); Statement sql = dueno.createStatement()) {
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE auditoria.registro_auditoria SET accion = 'X'"))
                    .hasMessageContaining("solo inserción");
            assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM auditoria.registro_auditoria"))
                    .hasMessageContaining("solo inserción");
            assertThatThrownBy(() -> sql.execute("TRUNCATE auditoria.registro_auditoria CASCADE"))
                    .hasMessageContaining("solo inserción");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria.registro_auditoria", Long.class))
                .isPositive();
    }
}
