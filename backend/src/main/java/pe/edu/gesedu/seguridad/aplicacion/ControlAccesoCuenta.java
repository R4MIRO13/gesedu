package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Clock;
import java.time.Duration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.seguridad.dominio.Usuario;
import pe.edu.gesedu.seguridad.infraestructura.PropiedadesSeguridad;

/**
 * Bloqueo temporal por intentos fallidos (RNF-06, S-03). Es un bean aparte de {@link AutenticacionService}
 * para que el aspecto {@code @Auditable} intercepte la llamada (una llamada interna no pasa por el proxy).
 */
@Service
class ControlAccesoCuenta {

    @PersistenceContext
    private EntityManager em;

    private final PropiedadesSeguridad propiedades;
    private final Clock reloj;

    ControlAccesoCuenta(PropiedadesSeguridad propiedades, Clock reloj) {
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /**
     * Cuenta un intento fallido; al llegar al máximo bloquea la cuenta. Solo se audita cuando bloquea.
     *
     * @return {@code true} si este intento bloqueó la cuenta
     */
    @Transactional
    @Auditable(accion = "BLOQUEAR_CUENTA", modulo = "SEGURIDAD", tipoEntidad = "Usuario", idEntidad = "#usuario.id",
            descripcion = "Bloqueo temporal automático por intentos fallidos de inicio de sesión", condicion = "#result")
    public boolean registrarIntentoFallido(Usuario usuario) {
        // Bloqueo de fila y relectura: dos intentos simultáneos no deben contarse como uno solo
        em.refresh(usuario, LockModeType.PESSIMISTIC_WRITE);
        boolean bloqueada = usuario.registrarIntentoFallido(reloj.instant(), propiedades.maxIntentos(),
                Duration.ofMinutes(propiedades.bloqueoMinutos()));
        if (bloqueada) {
            ContextoAuditoria.registrarCambio("bloqueado_hasta", null, usuario.getBloqueadoHasta());
        }
        return bloqueada;
    }
}
