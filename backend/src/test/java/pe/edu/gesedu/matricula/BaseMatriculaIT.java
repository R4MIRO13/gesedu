package pe.edu.gesedu.matricula;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosMatricula;
import pe.edu.gesedu.pagos.aplicacion.PlanDeCobros;
import pe.edu.gesedu.transversal.notificaciones.Notificador;

/**
 * Base de las pruebas de UC-02: el correo sale por un {@code Notificador} falso y {@code PlanDeCobros} es un espía
 * (para forzar un fallo en la generación de obligaciones). Las subclases comparten el contexto de Spring.
 */
public abstract class BaseMatriculaIT extends BaseIT {

    @MockitoBean
    protected Notificador notificador;

    @MockitoSpyBean
    protected PlanDeCobros planDeCobros;

    protected DatosMatricula datos;

    @Autowired
    void inicializarDatos(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        datos = new DatosMatricula(jdbcTemplate);
    }

    /** Token de un administrador con los permisos del rol (evaluar y registrar matrículas). */
    protected String tokenAdministrador() throws Exception {
        return tokenDe(crearUsuario("ADMINISTRADOR", true));
    }

    protected String ruta(String codigo) {
        return "/api/v1/solicitudes-matricula/" + codigo;
    }

    protected String cuerpoConfirmacion(String seccionId) throws Exception {
        return json.writeValueAsString(java.util.Map.of("seccionId", seccionId, "costosAceptados", true));
    }

    protected String cuerpoRechazo(String motivo, String observacion) throws Exception {
        return json.writeValueAsString(java.util.Map.of("motivo", motivo, "observacion", observacion));
    }

    protected long contar(String sql, Object... parametros) {
        return jdbc.queryForObject(sql, Long.class, parametros);
    }
}
