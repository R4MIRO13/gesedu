package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.gesedu.seguridad.dominio.Permiso;
import pe.edu.gesedu.seguridad.dominio.Usuario;
import pe.edu.gesedu.seguridad.infraestructura.UsuarioRepository;

/**
 * Caché Caffeine de cuentas y permisos (RNF-07, DA-05): evita una consulta a la base de datos en cada petición.
 * Quien cambie el estado de una cuenta o los permisos de un rol debe invalidarla ({@link #invalidar},
 * {@link #invalidarTodas}); el vencimiento a los 5 minutos es solo una red de seguridad.
 */
@Component
public class CacheCuentas {

    private final Cache<String, CuentaAutenticada> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    private final UsuarioRepository usuarios;
    private final TransactionTemplate transaccion;

    CacheCuentas(UsuarioRepository usuarios, PlatformTransactionManager gestorTransacciones) {
        this.usuarios = usuarios;
        this.transaccion = new TransactionTemplate(gestorTransacciones);
        this.transaccion.setReadOnly(true);
    }

    public Optional<CuentaAutenticada> obtener(String usuarioId) {
        CuentaAutenticada enCache = cache.getIfPresent(usuarioId);
        if (enCache != null) {
            return Optional.of(enCache);
        }
        Optional<CuentaAutenticada> cargada = transaccion.execute(
                estado -> usuarios.findConPermisosById(usuarioId).map(CacheCuentas::aCuenta));
        cargada.ifPresent(cuenta -> cache.put(usuarioId, cuenta));
        return cargada;
    }

    public void invalidar(String usuarioId) {
        cache.invalidate(usuarioId);
    }

    /** Para cambios que afectan a muchas cuentas, como los permisos de un rol. */
    public void invalidarTodas() {
        cache.invalidateAll();
    }

    private static CuentaAutenticada aCuenta(Usuario usuario) {
        return new CuentaAutenticada(usuario.getId(), usuario.getNombres(), usuario.getRolAcceso().getCodigo(),
                usuario.isActivo(), usuario.getBloqueadoHasta(),
                usuario.getRolAcceso().getPermisos().stream().map(Permiso::getCodigo)
                        .sorted(Comparator.naturalOrder()).toList());
    }
}
