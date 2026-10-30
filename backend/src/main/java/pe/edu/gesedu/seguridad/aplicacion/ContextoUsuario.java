package pe.edu.gesedu.seguridad.aplicacion;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Usuario de la petición en curso. Otros módulos (auditoría, alcance) lo consultan aquí sin conocer
 * Spring Security. Vacío fuera de una petición autenticada: tareas programadas («Sistema») o login.
 */
@Component
public class ContextoUsuario {

    /** {@code true} si la petición en curso tiene el permiso (el mismo que comprueba {@code @PreAuthorize}). */
    public boolean tienePermiso(String permiso) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getAuthorities().stream().anyMatch(a -> permiso.equals(a.getAuthority()));
    }

    public Optional<String> idUsuarioActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return Optional.of(usuario.id());
        }
        return Optional.empty();
    }
}
