package pe.edu.gesedu.seguridad.aplicacion;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Respuesta del inicio de sesión (docs/PLAN.md § 5.4). {@code cargo} solo existe para administrativos.
 */
public record SesionIniciada(String tokenAcceso, OffsetDateTime expiraEn, DatosUsuario usuario) {

    public record DatosUsuario(String nombres, String rol, String cargo, List<String> permisos) {
    }
}
