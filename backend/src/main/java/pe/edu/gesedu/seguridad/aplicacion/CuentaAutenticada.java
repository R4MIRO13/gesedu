package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Instant;
import java.util.List;

/** Lo que el filtro JWT necesita saber de una cuenta en cada petición; es lo que guarda la caché (RNF-07). */
public record CuentaAutenticada(String id, String nombres, String rol, boolean activo, Instant bloqueadoHasta,
                                List<String> permisos) {

    public boolean estaBloqueada(Instant ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }
}
