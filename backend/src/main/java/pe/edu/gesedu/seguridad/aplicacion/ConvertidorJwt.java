package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Clock;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.seguridad.dominio.Permiso;
import pe.edu.gesedu.seguridad.dominio.Usuario;
import pe.edu.gesedu.seguridad.infraestructura.UsuarioRepository;

/**
 * Convierte un JWT válido en la autenticación de la petición. Lee la cuenta en cada petición: una cuenta
 * desactivada o bloqueada pierde el acceso de inmediato aunque su token no haya vencido, y los cambios de
 * rol se aplican sin esperar a un nuevo token. (La caché de permisos de RNF-07 se agrega en la fase 0 completa.)
 */
@Component
public class ConvertidorJwt implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    private final UsuarioRepository usuarios;
    private final Clock reloj;

    ConvertidorJwt(UsuarioRepository usuarios, Clock reloj) {
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        Usuario usuario = usuarios.findConPermisosById(jwt.getSubject())
                .orElseThrow(() -> new BadCredentialsException("La cuenta del token no existe"));
        AuthenticationException rechazo = null;
        if (!usuario.isActivo()) {
            rechazo = new DisabledException("La cuenta está desactivada");
        } else if (usuario.estaBloqueada(reloj.instant())) {
            rechazo = new LockedException("La cuenta está bloqueada");
        }
        if (rechazo != null) {
            throw rechazo;
        }
        List<GrantedAuthority> permisos = usuario.getRolAcceso().getPermisos().stream()
                .map(Permiso::getCodigo)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
        UsuarioAutenticado principal = new UsuarioAutenticado(usuario.getId(), usuario.getNombres(),
                usuario.getRolAcceso().getCodigo());
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, permisos);
    }
}
