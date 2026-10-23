package pe.edu.gesedu.seguridad.aplicacion;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.seguridad.dominio.Usuario;
import pe.edu.gesedu.seguridad.infraestructura.UsuarioRepository;

/** Nombres de usuarios para otros módulos (por ejemplo, quién hizo cada acción en la auditoría). */
@Service
public class DirectorioUsuarios {

    private final UsuarioRepository usuarios;

    DirectorioUsuarios(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    /** Id → «Nombres Apellidos». Los ids que no existen no aparecen en el mapa. */
    @Transactional(readOnly = true)
    public Map<String, String> nombresCompletos(Collection<String> ids) {
        return usuarios.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, u -> u.getNombres() + " " + u.getApellidos()));
    }
}
