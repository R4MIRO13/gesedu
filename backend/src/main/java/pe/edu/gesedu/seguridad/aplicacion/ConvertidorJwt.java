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

/**
 * Convierte un JWT válido en la autenticación de la petición. Consulta la cuenta en {@link CacheCuentas}: una
 * cuenta desactivada o bloqueada pierde el acceso en cuanto se invalida su entrada, aunque su token no haya
 * vencido, y los cambios de rol se aplican sin esperar a un nuevo token (RNF-07).
 */
@Component
public class ConvertidorJwt implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    private final CacheCuentas cuentas;
    private final Clock reloj;

    ConvertidorJwt(CacheCuentas cuentas, Clock reloj) {
        this.cuentas = cuentas;
        this.reloj = reloj;
    }

    @Override
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        CuentaAutenticada cuenta = cuentas.obtener(jwt.getSubject())
                .orElseThrow(() -> new BadCredentialsException("La cuenta del token no existe"));
        AuthenticationException rechazo = null;
        if (!cuenta.activo()) {
            rechazo = new DisabledException("La cuenta está desactivada");
        } else if (cuenta.estaBloqueada(reloj.instant())) {
            rechazo = new LockedException("La cuenta está bloqueada");
        }
        if (rechazo != null) {
            throw rechazo;
        }
        List<GrantedAuthority> permisos = cuenta.permisos().stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
        UsuarioAutenticado principal = new UsuarioAutenticado(cuenta.id(), cuenta.nombres(), cuenta.rol());
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, permisos);
    }
}
