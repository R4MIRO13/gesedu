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
import pe.edu.gesedu.seguridad.infraestructura.PropiedadesSeguridad;
import pe.edu.gesedu.seguridad.infraestructura.UsuarioRepository;

/** Inicio de sesión (UC-24, RF-52) con bloqueo temporal tras 5 intentos fallidos (RNF-06). */
@Service
public class AutenticacionService {

    private final UsuarioRepository usuarios;
    private final AdministradorRepository administradores;
    private final PasswordEncoder codificador;
    private final EmisorToken emisorToken;
    private final ControlAccesoCuenta control;
    private final TokensRefresco tokensRefresco;
    private final CacheCuentas cacheCuentas;
    private final PropiedadesSeguridad propiedades;
    private final Clock reloj;

    // Se calcula una vez; sirve para que un correo inexistente tarde lo mismo que uno existente
    private volatile String hashFicticio;

    AutenticacionService(UsuarioRepository usuarios, AdministradorRepository administradores,
                         PasswordEncoder codificador, EmisorToken emisorToken, ControlAccesoCuenta control,
                         TokensRefresco tokensRefresco, CacheCuentas cacheCuentas,
                         PropiedadesSeguridad propiedades, Clock reloj) {
        this.usuarios = usuarios;
        this.administradores = administradores;
        this.codificador = codificador;
        this.emisorToken = emisorToken;
        this.control = control;
        this.tokensRefresco = tokensRefresco;
        this.cacheCuentas = cacheCuentas;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /**
     * Los fallos son {@link ExcepcionNegocio}; no deben revertir la transacción porque el contador de intentos
     * y el bloqueo tienen que quedar guardados.
     */
    @Transactional(noRollbackFor = ExcepcionNegocio.class)
    public SesionConRefresco iniciarSesion(String correo, String contrasena) {
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
        cacheCuentas.invalidar(usuario.getId());
        return new SesionConRefresco(sesionDe(usuario), tokensRefresco.emitir(usuario.getId()));
    }

    /**
     * Renueva la sesión con el refresh token de la cookie: entrega un token de acceso nuevo y rota el refresh
     * (DA-04). Una cuenta que ya no puede entrar pierde también su sesión.
     */
    @Transactional(noRollbackFor = ExcepcionNegocio.class)
    public SesionConRefresco refrescar(String valorRefresco) {
        TokensRefresco.Rotacion rotacion = tokensRefresco.rotar(valorRefresco);
        Usuario usuario = usuarios.findConPermisosById(rotacion.usuarioId())
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.SESION_EXPIRADA));
        if (!usuario.isActivo() || usuario.estaBloqueada(reloj.instant())) {
            tokensRefresco.revocarTodos(usuario.getId());
            throw usuario.isActivo() ? cuentaBloqueada(usuario) : new ExcepcionNegocio(CodigoError.CUENTA_INACTIVA);
        }
        return new SesionConRefresco(sesionDe(usuario), rotacion.token());
    }

    /** Cierre de sesión (RF-52, UC-24 FA-4): el refresh token queda revocado y el siguiente refresco da 401. */
    @Transactional
    public void cerrarSesion(String valorRefresco) {
        tokensRefresco.cerrar(valorRefresco);
    }

    /** La cookie de refresh lleva {@code Secure} salvo que se configure lo contrario. */
    public boolean cookieRefrescoSegura() {
        return propiedades.cookieSecure();
    }

    private SesionIniciada sesionDe(Usuario usuario) {
        EmisorToken.TokenEmitido token = emisorToken.emitir(usuario.getId());
        return new SesionIniciada(token.valor(), token.expiraEn().atZone(reloj.getZone()).toOffsetDateTime(),
                datosDe(usuario));
    }

    /** Datos del usuario de la sesión actual; la SPA arma el menú con sus permisos (RF-50). */
    @Transactional(readOnly = true)
    public SesionIniciada.DatosUsuario datosUsuarioActual(String usuarioId) {
        return usuarios.findConPermisosById(usuarioId)
                .map(this::datosDe)
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO));
    }

    private SesionIniciada.DatosUsuario datosDe(Usuario usuario) {
        List<String> permisos = usuario.getRolAcceso().getPermisos().stream()
                .map(Permiso::getCodigo)
                .sorted(Comparator.naturalOrder())
                .toList();
        String cargo = administradores.findByUsuarioId(usuario.getId()).map(Administrador::getCargo).orElse(null);
        return new SesionIniciada.DatosUsuario(usuario.getNombres(), usuario.getRolAcceso().getCodigo(), cargo,
                permisos);
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
