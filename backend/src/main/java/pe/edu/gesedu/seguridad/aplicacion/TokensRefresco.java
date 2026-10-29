package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.seguridad.dominio.RefreshToken;
import pe.edu.gesedu.seguridad.infraestructura.PropiedadesSeguridad;
import pe.edu.gesedu.seguridad.infraestructura.RefreshTokenRepository;

/**
 * Refresh token rotativo con detección de reutilización (DA-04, S-06). Cada uso entrega un token nuevo de la
 * misma familia y revoca el anterior; presentar uno ya rotado delata un robo y revoca la familia entera.
 */
@Service
class TokensRefresco {

    /** Token entregado y el usuario al que pertenece. */
    record Rotacion(TokenRefresco token, String usuarioId) {
    }

    private final RefreshTokenRepository tokens;
    private final PropiedadesSeguridad propiedades;
    private final Clock reloj;

    TokensRefresco(RefreshTokenRepository tokens, PropiedadesSeguridad propiedades, Clock reloj) {
        this.tokens = tokens;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** Inicia una familia nueva (inicio de sesión). */
    @Transactional
    TokenRefresco emitir(String usuarioId) {
        return nuevo(usuarioId, UUID.randomUUID().toString()).token();
    }

    /**
     * Cambia un refresh token válido por uno nuevo. Los fallos son {@link ExcepcionNegocio}; no revierten la
     * transacción, porque la revocación de la familia tiene que quedar guardada.
     */
    @Transactional(noRollbackFor = ExcepcionNegocio.class)
    Rotacion rotar(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ExcepcionNegocio(CodigoError.SESION_EXPIRADA);
        }
        Instant ahora = reloj.instant();
        RefreshToken actual = tokens.findByTokenHash(TokensOpacos.hash(valor))
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.SESION_EXPIRADA));
        if (actual.estaRevocado()) {
            // Reutilización de un token ya rotado o cerrado: se asume robo y se cierra toda la familia
            tokens.revocarFamilia(actual.getFamilia(), ahora);
            throw new ExcepcionNegocio(CodigoError.SESION_EXPIRADA);
        }
        if (actual.haVencido(ahora)) {
            throw new ExcepcionNegocio(CodigoError.SESION_EXPIRADA);
        }
        Nuevo nuevo = nuevo(actual.getUsuarioId(), actual.getFamilia());
        actual.rotar(nuevo.entidad().getId(), ahora);
        return new Rotacion(nuevo.token(), actual.getUsuarioId());
    }

    /** Cierre de sesión: revoca la familia del token recibido. Un token desconocido no es un error. */
    @Transactional
    void cerrar(String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        tokens.findByTokenHash(TokensOpacos.hash(valor))
                .ifPresent(token -> tokens.revocarFamilia(token.getFamilia(), reloj.instant()));
    }

    /** Revoca todas las sesiones de una cuenta (desactivación, cambio de contraseña). */
    @Transactional
    void revocarTodos(String usuarioId) {
        tokens.revocarTodosDelUsuario(usuarioId, reloj.instant());
    }

    private record Nuevo(TokenRefresco token, RefreshToken entidad) {
    }

    private Nuevo nuevo(String usuarioId, String familia) {
        String valor = TokensOpacos.generar();
        Duration vigencia = Duration.ofDays(propiedades.refreshDias());
        RefreshToken entidad = tokens.saveAndFlush(
                new RefreshToken(usuarioId, TokensOpacos.hash(valor), familia, reloj.instant().plus(vigencia)));
        return new Nuevo(new TokenRefresco(valor, vigencia), entidad);
    }
}
