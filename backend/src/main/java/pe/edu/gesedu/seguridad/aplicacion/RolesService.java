package pe.edu.gesedu.seguridad.aplicacion;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.seguridad.dominio.Permiso;
import pe.edu.gesedu.seguridad.dominio.RolAcceso;
import pe.edu.gesedu.seguridad.infraestructura.PermisoRepository;
import pe.edu.gesedu.seguridad.infraestructura.RolAccesoRepository;

/**
 * Roles y permisos (RF-48, RNF-07): el administrador ve el catálogo y reemplaza el conjunto de permisos de un rol.
 * El cambio se aplica en el siguiente request de cada usuario del rol (se descarta la caché de cuentas al confirmar la
 * transacción) y queda auditado con los permisos agregados y quitados. El rol ADMINISTRADOR conserva siempre los
 * permisos con que se gestionan las cuentas y los roles, para que el sistema no se quede sin quien lo administre.
 */
@Service
public class RolesService {

    /** Permisos que el rol ADMINISTRADOR no puede perder. */
    public static final Set<String> ESENCIALES_DEL_ADMINISTRADOR =
            Set.of("USUARIO_CONSULTAR", "USUARIO_GESTIONAR", "ROL_GESTIONAR");

    public record PermisoDto(String codigo, String modulo, String accion, String descripcion) {
    }

    public record RolDto(String id, String codigo, String nombre, String descripcion, List<String> permisos) {
    }

    public record CatalogoRoles(List<RolDto> roles, List<PermisoDto> permisos) {
    }

    private final RolAccesoRepository roles;
    private final PermisoRepository permisos;
    private final CacheCuentas cacheCuentas;

    RolesService(RolAccesoRepository roles, PermisoRepository permisos, CacheCuentas cacheCuentas) {
        this.roles = roles;
        this.permisos = permisos;
        this.cacheCuentas = cacheCuentas;
    }

    @Transactional(readOnly = true)
    public CatalogoRoles listar() {
        List<RolDto> lista = roles.listarConPermisos().stream().map(RolesService::aDto).toList();
        List<PermisoDto> catalogo = permisos.findAll().stream()
                .sorted(Comparator.comparing(Permiso::getModulo).thenComparing(Permiso::getCodigo))
                .map(p -> new PermisoDto(p.getCodigo(), p.getModulo(), p.getAccion(), p.getDescripcion()))
                .toList();
        return new CatalogoRoles(lista, catalogo);
    }

    /** Reemplaza todos los permisos del rol por {@code codigos}. Un código fuera del catálogo es un error (400). */
    @Transactional
    @Auditable(accion = "MODIFICAR_PERMISOS_ROL", modulo = "SEGURIDAD", tipoEntidad = "RolAcceso", idEntidad = "#id",
            descripcion = "Cambio de los permisos de un rol")
    public RolDto reemplazarPermisos(String id, Set<String> codigos) {
        RolAcceso rol = roles.bloquearPorId(id).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el rol"));
        Map<String, Permiso> encontrados = permisos.findByCodigoIn(codigos).stream()
                .collect(Collectors.toMap(Permiso::getCodigo, Function.identity()));
        Set<String> desconocidos = new TreeSet<>(codigos);
        desconocidos.removeAll(encontrados.keySet());
        if (!desconocidos.isEmpty()) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "Permisos que no existen: " + String.join(", ", desconocidos));
        }
        if ("ADMINISTRADOR".equals(rol.getCodigo()) && !codigos.containsAll(ESENCIALES_DEL_ADMINISTRADOR)) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                    "El rol Administrador debe conservar los permisos " + String.join(", ", new TreeSet<>(ESENCIALES_DEL_ADMINISTRADOR)));
        }

        Set<String> antes = rol.getPermisos().stream().map(Permiso::getCodigo).collect(Collectors.toSet());
        Set<String> agregados = new TreeSet<>(codigos);
        agregados.removeAll(antes);
        Set<String> quitados = new TreeSet<>(antes);
        quitados.removeAll(codigos);
        agregados.forEach(c -> ContextoAuditoria.registrarCambio("permiso " + c, "no", "sí"));
        quitados.forEach(c -> ContextoAuditoria.registrarCambio("permiso " + c, "sí", "no"));

        rol.reemplazarPermisos(new HashSet<>(encontrados.values()));
        roles.saveAndFlush(rol);
        descartarCacheAlConfirmar();
        return aDto(rol);
    }

    /** La caché se descarta después del commit: antes, una petición concurrente la volvería a llenar con lo viejo. */
    private void descartarCacheAlConfirmar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheCuentas.invalidarTodas();
                }
            });
        } else {
            cacheCuentas.invalidarTodas();
        }
    }

    private static RolDto aDto(RolAcceso r) {
        return new RolDto(r.getId(), r.getCodigo(), r.getNombre(), r.getDescripcion(),
                r.getPermisos().stream().map(Permiso::getCodigo).sorted().toList());
    }
}
