package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.compartido.paginacion.Pagina;
import pe.edu.gesedu.seguridad.dominio.Usuario;
import pe.edu.gesedu.seguridad.infraestructura.UsuarioRepository;

/**
 * Cuentas de usuario (UC-24, RF-01): consulta con filtros y desbloqueo por el administrador. El resto del CRUD
 * llega en la fase 6.
 */
@Service
public class UsuariosService {

    /** Estado de la cuenta tal como se filtra y se muestra: bloqueado gana sobre activo. */
    public enum EstadoCuenta {
        ACTIVO, INACTIVO, BLOQUEADO
    }

    public record UsuarioFila(String id, String nombre, String correo, String rol, boolean activo, EstadoCuenta estado,
                              OffsetDateTime bloqueadoHasta, OffsetDateTime ultimoAcceso) {
    }

    private final UsuarioRepository usuarios;
    private final CacheCuentas cacheCuentas;
    private final Clock reloj;

    UsuariosService(UsuarioRepository usuarios, CacheCuentas cacheCuentas, Clock reloj) {
        this.usuarios = usuarios;
        this.cacheCuentas = cacheCuentas;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Pagina<UsuarioFila> listar(String q, String rol, EstadoCuenta estado, Pageable paginacion) {
        Instant ahora = reloj.instant();
        Specification<Usuario> filtro = (raiz, consulta, cb) -> {
            if (consulta != null && consulta.getResultType() != Long.class && consulta.getResultType() != long.class) {
                raiz.fetch("rolAcceso");
            }
            List<Predicate> condiciones = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String patron = "%" + q.trim().toLowerCase().replaceAll("[%_]", "") + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(cb.concat(cb.concat(raiz.get("nombres"), " "), raiz.get("apellidos"))), patron),
                        cb.like(cb.lower(raiz.get("correo").as(String.class)), patron)));
            }
            if (rol != null && !rol.isBlank()) {
                condiciones.add(cb.equal(raiz.get("rolAcceso").get("codigo"), rol));
            }
            if (estado != null) {
                Predicate bloqueada = cb.greaterThan(raiz.<Instant>get("bloqueadoHasta"), ahora);
                Predicate sinBloqueo = cb.or(cb.isNull(raiz.get("bloqueadoHasta")),
                        cb.lessThanOrEqualTo(raiz.<Instant>get("bloqueadoHasta"), ahora));
                condiciones.add(switch (estado) {
                    case BLOQUEADO -> bloqueada;
                    case INACTIVO -> cb.isFalse(raiz.get("activo"));
                    case ACTIVO -> cb.and(cb.isTrue(raiz.get("activo")), sinBloqueo);
                });
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        return Pagina.de(usuarios.findAll(filtro, paginacion), u -> aFila(u, ahora, reloj));
    }

    /**
     * El administrador levanta el bloqueo temporal de una cuenta y reinicia sus intentos fallidos. La acción queda
     * en la auditoría con el bloqueo que se levantó.
     */
    @Transactional
    @Auditable(accion = "DESBLOQUEAR_CUENTA", modulo = "SEGURIDAD", tipoEntidad = "Usuario", idEntidad = "#id",
            descripcion = "Desbloqueo de cuenta por el administrador")
    public UsuarioFila desbloquear(String id) {
        Usuario usuario = usuarios.findById(id).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el usuario"));
        Instant ahora = reloj.instant();
        if (!usuario.estaBloqueada(ahora)) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA, "La cuenta no está bloqueada");
        }
        ContextoAuditoria.registrarCambio("bloqueadoHasta", usuario.getBloqueadoHasta(), null);
        usuario.desbloquear();
        cacheCuentas.invalidar(id);
        return aFila(usuario, ahora, reloj);
    }

    private static UsuarioFila aFila(Usuario u, Instant ahora, Clock reloj) {
        EstadoCuenta estado = u.estaBloqueada(ahora) ? EstadoCuenta.BLOQUEADO
                : u.isActivo() ? EstadoCuenta.ACTIVO : EstadoCuenta.INACTIVO;
        return new UsuarioFila(u.getId(), u.getNombres() + " " + u.getApellidos(), u.getCorreo(),
                u.getRolAcceso().getCodigo(), u.isActivo(), estado, local(u.getBloqueadoHasta(), reloj),
                local(u.getUltimoAcceso(), reloj));
    }

    /** Fechas en la zona institucional (-05:00), como el resto de la API. */
    private static OffsetDateTime local(Instant instante, Clock reloj) {
        return instante == null ? null : instante.atZone(reloj.getZone()).toOffsetDateTime();
    }
}
