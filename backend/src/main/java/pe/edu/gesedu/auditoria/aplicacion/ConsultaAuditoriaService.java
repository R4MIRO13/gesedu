package pe.edu.gesedu.auditoria.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.auditoria.dominio.RegistroAuditoria;
import pe.edu.gesedu.auditoria.infraestructura.CambioAuditoriaRepository;
import pe.edu.gesedu.auditoria.infraestructura.RegistroAuditoriaFiltros;
import pe.edu.gesedu.auditoria.infraestructura.RegistroAuditoriaRepository;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.compartido.paginacion.Pagina;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioUsuarios;

/** Consulta de los registros de auditoría con filtros y paginación (UC-25, RF-51). */
@Service
public class ConsultaAuditoriaService {

    public static final int TAMANO_MAXIMO = 100;

    /** Lista blanca de campos ordenables (docs/PLAN.md § 5.1): nunca se ordena por un nombre que llegue sin revisar. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("fechaHora", "accion", "modulo");

    /** Filtros de la consulta; todos opcionales. {@code desde} y {@code hasta} son días completos, inclusive. */
    public record Filtros(String usuarioId, String modulo, String accion, LocalDate desde, LocalDate hasta) {
    }

    private final RegistroAuditoriaRepository registros;
    private final CambioAuditoriaRepository cambios;
    private final DirectorioUsuarios directorio;
    private final Clock reloj;

    ConsultaAuditoriaService(RegistroAuditoriaRepository registros, CambioAuditoriaRepository cambios,
                             DirectorioUsuarios directorio, Clock reloj) {
        this.registros = registros;
        this.cambios = cambios;
        this.directorio = directorio;
        this.reloj = reloj;
    }

    /**
     * @param orden {@code campo} o {@code campo,asc|desc}; por defecto los más recientes primero
     */
    @Transactional(readOnly = true)
    public Pagina<RegistroAuditoriaDto> consultar(Filtros filtros, int pagina, int tamano, String orden) {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                    "page debe ser >= 0 y size debe estar entre 1 y " + TAMANO_MAXIMO);
        }
        Page<RegistroAuditoria> resultado = registros.findAll(especificacion(filtros),
                PageRequest.of(pagina, tamano, ordenamiento(orden)));
        Set<String> ids = resultado.getContent().stream().map(RegistroAuditoria::getUsuarioId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> nombres = ids.isEmpty() ? Map.of() : directorio.nombresCompletos(ids);
        return Pagina.de(resultado, r -> aDto(r, nombres));
    }

    /** Un registro con los cambios de campo que lo acompañan (valor anterior y nuevo, RF-49). */
    @Transactional(readOnly = true)
    public DetalleRegistroAuditoriaDto detalle(String id) {
        RegistroAuditoria registro = registros.findById(id).orElseThrow(() -> new ExcepcionNegocio(
                CodigoError.RECURSO_NO_ENCONTRADO, "No existe el registro de auditoría indicado"));
        Map<String, String> nombres = registro.getUsuarioId() == null ? Map.of()
                : directorio.nombresCompletos(Set.of(registro.getUsuarioId()));
        List<CambioAuditoriaDto> lista = cambios.findByRegistroAuditoriaIdOrderByCampo(id).stream()
                .map(c -> new CambioAuditoriaDto(c.getCampo(), c.getValorAnterior(), c.getValorNuevo())).toList();
        return new DetalleRegistroAuditoriaDto(aDto(registro, nombres), lista);
    }

    /** Módulos con sus acciones y usuarios que aparecen en la auditoría, para armar los filtros (RF-51). */
    @Transactional(readOnly = true)
    public CatalogosAuditoriaDto catalogos() {
        Map<String, List<String>> accionesPorModulo = new java.util.TreeMap<>();
        for (var par : registros.modulosYAcciones()) {
            accionesPorModulo.computeIfAbsent(par.getModulo(), m -> new java.util.ArrayList<>()).add(par.getAccion());
        }
        List<String> ids = registros.usuariosConRegistros();
        Map<String, String> nombres = ids.isEmpty() ? Map.of() : directorio.nombresCompletos(ids);
        return new CatalogosAuditoriaDto(
                accionesPorModulo.entrySet().stream()
                        .map(e -> new ModuloAuditoriaDto(e.getKey(), e.getValue())).toList(),
                ids.stream().filter(nombres::containsKey)
                        .map(id -> new UsuarioAuditoriaDto(id, nombres.get(id)))
                        .sorted(java.util.Comparator.comparing(UsuarioAuditoriaDto::nombre)).toList());
    }

    /** Valida el rango de fechas y arma el filtro de la consulta; lo comparten la lista y la exportación. */
    Specification<RegistroAuditoria> especificacion(Filtros filtros) {
        if (filtros.desde() != null && filtros.hasta() != null && filtros.desde().isAfter(filtros.hasta())) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "desde no puede ser posterior a hasta");
        }
        return RegistroAuditoriaFiltros.con(blancoANulo(filtros.usuarioId()), blancoANulo(filtros.modulo()),
                blancoANulo(filtros.accion()),
                filtros.desde() == null ? null : filtros.desde().atStartOfDay(reloj.getZone()).toInstant(),
                // hasta es inclusive: el límite excluido es el inicio del día siguiente
                filtros.hasta() == null ? null
                        : filtros.hasta().plusDays(1).atStartOfDay(reloj.getZone()).toInstant());
    }

    private static Sort ordenamiento(String orden) {
        String campo = "fechaHora";
        Sort.Direction direccion = Sort.Direction.DESC;
        if (orden != null && !orden.isBlank()) {
            List<String> partes = List.of(orden.split(",", -1));
            campo = partes.getFirst().trim();
            if (partes.size() > 2 || !CAMPOS_ORDENABLES.contains(campo)) {
                throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                        "sort debe ser uno de " + CAMPOS_ORDENABLES + ", opcionalmente seguido de ,asc o ,desc");
            }
            if (partes.size() == 2) {
                direccion = Sort.Direction.fromOptionalString(partes.get(1).trim()).orElseThrow(
                        () -> new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "El sentido debe ser asc o desc"));
            }
        }
        // El id (UUIDv7, ordenado en el tiempo) desempata para que las páginas sean estables
        return Sort.by(direccion, campo).and(Sort.by(direccion, "id"));
    }

    RegistroAuditoriaDto aDto(RegistroAuditoria r, Map<String, String> nombres) {
        return new RegistroAuditoriaDto(r.getId(), r.getFechaHora().atZone(reloj.getZone()).toOffsetDateTime(),
                r.getUsuarioId(), r.getUsuarioId() == null ? null : nombres.get(r.getUsuarioId()), r.getAccion(),
                r.getModulo(), r.getTipoEntidad(), r.getIdEntidadAfectada(), r.getDescripcion());
    }

    private static String blancoANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
