package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.seguridad.dominio.TokenRecuperacion;
import pe.edu.gesedu.seguridad.dominio.Usuario;
import pe.edu.gesedu.seguridad.infraestructura.AlumnoRepository;
import pe.edu.gesedu.seguridad.infraestructura.TokenRecuperacionRepository;
import pe.edu.gesedu.seguridad.infraestructura.UsuarioRepository;
import pe.edu.gesedu.transversal.notificaciones.Notificacion;
import pe.edu.gesedu.transversal.notificaciones.Notificaciones;

/**
 * Recuperación de contraseña (UC-24 FA-5, RF-52, S-01) y aceptación de la invitación de una cuenta nueva (S-77): el
 * mismo enlace sirve a ambas, y la cuenta sin contraseña queda activa al definirla. El enlace lleva un token aleatorio de 32 bytes del que solo
 * se guarda el hash; vale 30 minutos y una sola vez. Pedir la recuperación responde siempre igual, exista o no la
 * cuenta, para no revelar qué correos están registrados.
 */
@Service
public class RecuperacionContrasenaService {

    public static final Duration VIGENCIA = Duration.ofMinutes(30);

    /** Política de contraseñas (S-01): al menos 8 caracteres, con una letra y un número; BCrypt llega a 72. */
    public static final String POLITICA = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String MENSAJE_POLITICA = "La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número";
    private static final Pattern PATRON_POLITICA = Pattern.compile(POLITICA);

    private final UsuarioRepository usuarios;
    private final TokenRecuperacionRepository tokens;
    private final TokensRefresco tokensRefresco;
    private final CacheCuentas cacheCuentas;
    private final VerificadorVinculoAcademico vinculo;
    private final AlumnoRepository alumnos;
    private final PasswordEncoder codificador;
    private final Notificaciones notificaciones;
    private final Clock reloj;
    private final String origenWeb;

    RecuperacionContrasenaService(UsuarioRepository usuarios, TokenRecuperacionRepository tokens,
                                  TokensRefresco tokensRefresco, CacheCuentas cacheCuentas,
                                  VerificadorVinculoAcademico vinculo, AlumnoRepository alumnos,
                                  PasswordEncoder codificador, Notificaciones notificaciones, Clock reloj,
                                  @Value("${gesedu.web-origen}") String origenWeb) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.tokensRefresco = tokensRefresco;
        this.cacheCuentas = cacheCuentas;
        this.vinculo = vinculo;
        this.alumnos = alumnos;
        this.codificador = codificador;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
        this.origenWeb = origenWeb;
    }

    /**
     * Si la cuenta existe y está activa, crea el enlace y lo manda por correo (después del commit). Si no, no hace
     * nada: el llamador responde lo mismo en ambos casos.
     */
    @Transactional
    public void solicitar(String correo) {
        Usuario usuario = usuarios.buscarPorCorreo(correo.trim()).orElse(null);
        Instant ahora = reloj.instant();
        if (usuario == null || !usuario.isActivo() || usuario.getHashContrasena() == null) {
            // Mismo trabajo que con una cuenta real (consulta, token y hash) para que el tiempo de respuesta no
            // delate qué correos existen (ISO/IEC 27002:2022, 8.5; S-84)
            tokens.anularPendientes("sin-cuenta", ahora);
            TokensOpacos.hash(TokensOpacos.generar());
            return;
        }
        tokens.anularPendientes(usuario.getId(), ahora);
        String valor = TokensOpacos.generar();
        tokens.save(new TokenRecuperacion(usuario.getId(), TokensOpacos.hash(valor), ahora.plus(VIGENCIA)));
        notificaciones.notificar(new Notificacion(usuario.getCorreo(), "Restablece tu contraseña de GESEDU",
                "recuperacion-contrasena", Map.of(
                        "nombre", usuario.getNombres(),
                        "enlace", origenWeb + "/restablecer-contrasena?token=" + valor,
                        "minutos", VIGENCIA.toMinutes())));
    }

    /**
     * Define la contraseña nueva con el enlace. El enlace queda usado, se revocan todas las sesiones abiertas de
     * la cuenta y el evento queda en la auditoría (RF-49).
     *
     * @return el id de la cuenta
     */
    @Transactional
    @Auditable(accion = "RESTABLECER_CONTRASENA", modulo = "SEGURIDAD", tipoEntidad = "Usuario", idEntidad = "#result",
            descripcion = "Restablecimiento de contraseña con el enlace de recuperación")
    public String confirmar(String token, String nuevaContrasena) {
        if (nuevaContrasena == null || !PATRON_POLITICA.matcher(nuevaContrasena).matches()) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, MENSAJE_POLITICA);
        }
        Instant ahora = reloj.instant();
        TokenRecuperacion enlace = tokens.findByTokenHash(TokensOpacos.hash(token))
                .filter(t -> t.sePuedeUsar(ahora))
                .orElseThrow(RecuperacionContrasenaService::enlaceInvalido);
        // Una cuenta sin contraseña que espera su invitación (S-77) también puede usar el enlace; una desactivada, no
        Usuario usuario = usuarios.findById(enlace.getUsuarioId())
                .filter(u -> u.isActivo() || (u.sinContrasena() && u.getCorreo() != null))
                .orElseThrow(RecuperacionContrasenaService::enlaceInvalido);
        boolean invitacion = !usuario.isActivo();
        if (invitacion && !conVinculo(usuario)) {
            throw enlaceInvalido();
        }

        enlace.marcarUsado(ahora);
        tokens.anularPendientes(usuario.getId(), ahora);
        Instant bloqueoAnterior = usuario.estaBloqueada(ahora) ? usuario.getBloqueadoHasta() : null;
        if (invitacion) {
            usuario.definirContrasenaInicial(codificador.encode(nuevaContrasena));
            ContextoAuditoria.registrarCambio("estado", "PENDIENTE", "ACTIVO");
        } else {
            usuario.cambiarContrasena(codificador.encode(nuevaContrasena));
        }
        if (bloqueoAnterior != null) {
            ContextoAuditoria.registrarCambio("bloqueado_hasta", bloqueoAnterior, null);
        }
        tokensRefresco.revocarTodos(usuario.getId());
        cacheCuentas.invalidar(usuario.getId());
        // La contraseña nunca va a la auditoría, solo el hecho de que cambió
        ContextoAuditoria.registrarCambio("contrasena", null, invitacion ? "definida" : "restablecida");
        return usuario.getId();
    }

    /** Un alumno solo activa su cuenta si todavía tiene una matrícula activa (UC-24 FA-2). */
    private boolean conVinculo(Usuario usuario) {
        if (!"ALUMNO".equals(usuario.getRolAcceso().getCodigo())) {
            return true;
        }
        return alumnos.findByUsuarioId(usuario.getId()).map(a -> vinculo.alumnoConMatriculaActiva(a.getId())).orElse(false);
    }

    private static ExcepcionNegocio enlaceInvalido() {
        return new ExcepcionNegocio(CodigoError.TOKEN_RECUPERACION_INVALIDO);
    }
}
