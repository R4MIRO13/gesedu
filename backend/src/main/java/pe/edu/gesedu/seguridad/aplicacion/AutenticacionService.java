package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.seguridad.dominio.Administrador;
import pe.edu.gesedu.seguridad.dominio.Permiso;
import pe.edu.gesedu.seguridad.dominio.Usuario;
import pe.edu.gesedu.seguridad.infraestructura.AdministradorRepository;
import pe.edu.gesedu.seguridad.infraestructura.UsuarioRepository;

/** Inicio de sesión (UC-24, RF-52) con bloqueo temporal tras 5 intentos fallidos (RNF-06). */
@Service
public class AutenticacionService {

    private final UsuarioRepository usuarios;
    private final AdministradorRepository administradores;
    private final PasswordEncoder codificador;
    private final EmisorToken emisorToken;
    private final ControlAccesoCuenta control;
    private final Clock reloj;

    // Se calcula una vez; sirve para que un correo inexistente tarde lo mismo que uno existente
    private volatile String hashFicticio;

    AutenticacionService(UsuarioRepository usuarios, AdministradorRepository administradores,
                         PasswordEncoder codificador, EmisorToken emisorToken, ControlAccesoCuenta control,
                         Clock reloj) {
        this.usuarios = usuarios;
        this.administradores = administradores;
        this.codificador = codificador;
        this.emisorToken = emisorToken;
        this.control = control;
        this.reloj = reloj;
    }

    /**
     * Los fallos son {@link ExcepcionNegocio}; no deben revertir la transacción porque el contador de intentos
     * y el bloqueo tienen que quedar guardados.
     */
    @Transactional(noRollbackFor = ExcepcionNegocio.class)
    public SesionIniciada iniciarSesion(String correo, String contrasena) {
        Instant ahora = reloj.instant();
        Usuario usuario = usuarios.buscarPorCorreo(correo.trim()).orElse(null);

        if (usuario == null || usuario.getHashContrasena() == null) {
            codificador.matches(contrasena, hashFicticio());
            throw new ExcepcionNegocio(CodigoError.CREDENCIALES_INVALIDAS);
        }
        if (usuario.estaBloqueada(ahora)) {
            throw cuentaBloqueada(usuario);
        }
        if (!codificador.matches(contrasena, usuario.getHashContrasena())) {
            if (control.registrarIntentoFallido(usuario)) {
                throw cuentaBloqueada(usuario);
            }
            throw new ExcepcionNegocio(CodigoError.CREDENCIALES_INVALIDAS);
        }
        // La inactividad se informa solo con la contraseña correcta: no revela qué correos existen
        if (!usuario.isActivo()) {
            throw new ExcepcionNegocio(CodigoError.CUENTA_INACTIVA);
        }

        usuario.registrarAccesoExitoso(ahora);
        EmisorToken.TokenEmitido token = emisorToken.emitir(usuario.getId());
        List<String> permisos = usuario.getRolAcceso().getPermisos().stream()
                .map(Permiso::getCodigo)
                .sorted(Comparator.naturalOrder())
                .toList();
        String cargo = administradores.findByUsuarioId(usuario.getId()).map(Administrador::getCargo).orElse(null);
        return new SesionIniciada(token.valor(), token.expiraEn().atZone(reloj.getZone()).toOffsetDateTime(),
                new SesionIniciada.DatosUsuario(usuario.getNombres(), usuario.getRolAcceso().getCodigo(), cargo,
                        permisos));
    }

    private ExcepcionNegocio cuentaBloqueada(Usuario usuario) {
        OffsetDateTime hasta = usuario.getBloqueadoHasta().atZone(reloj.getZone()).toOffsetDateTime();
        return new ExcepcionNegocio(CodigoError.CUENTA_BLOQUEADA).con("bloqueadoHasta", hasta);
    }

    private String hashFicticio() {
        String hash = hashFicticio;
        if (hash == null) {
            hash = codificador.encode("contrasena-ficticia");
            hashFicticio = hash;
        }
        return hash;
    }
}
