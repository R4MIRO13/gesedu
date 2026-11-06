package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pe.edu.gesedu.seguridad.dominio.TokenRecuperacion;
import pe.edu.gesedu.seguridad.dominio.Usuario;
import pe.edu.gesedu.seguridad.infraestructura.TokenRecuperacionRepository;
import pe.edu.gesedu.transversal.notificaciones.Notificacion;
import pe.edu.gesedu.transversal.notificaciones.Notificaciones;

/**
 * Invitación para definir la contraseña de una cuenta nueva (UC-24, S-77; ISO/IEC 27002:2022, control 5.17). Usa el
 * mismo mecanismo que la recuperación (FA-5): token aleatorio de 32 bytes del que solo se guarda el hash, de un solo
 * uso, pero con 72 horas de vigencia. El administrador nunca define ni ve la contraseña. Enviar otra invitación
 * anula las anteriores. Debe llamarse dentro de una transacción; el correo sale después del commit.
 */
@Component
class Invitaciones {

    static final Duration VIGENCIA = Duration.ofHours(72);

    private final TokenRecuperacionRepository tokens;
    private final Notificaciones notificaciones;
    private final Clock reloj;
    private final String origenWeb;

    Invitaciones(TokenRecuperacionRepository tokens, Notificaciones notificaciones, Clock reloj,
                 @Value("${gesedu.web-origen}") String origenWeb) {
        this.tokens = tokens;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
        this.origenWeb = origenWeb;
    }

    void enviar(Usuario usuario) {
        Instant ahora = reloj.instant();
        tokens.anularPendientes(usuario.getId(), ahora);
        String valor = TokensOpacos.generar();
        tokens.save(new TokenRecuperacion(usuario.getId(), TokensOpacos.hash(valor), ahora.plus(VIGENCIA)));
        notificaciones.notificar(new Notificacion(usuario.getCorreo(), "Activa tu cuenta de GESEDU",
                "invitacion-cuenta", Map.of(
                        "nombre", usuario.getNombres(),
                        "rol", usuario.getRolAcceso().getNombre(),
                        "enlace", origenWeb + "/definir-contrasena?token=" + valor,
                        "horas", VIGENCIA.toHours())));
    }
}
