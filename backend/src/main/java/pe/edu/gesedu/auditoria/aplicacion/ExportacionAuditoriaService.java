package pe.edu.gesedu.auditoria.aplicacion;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.auditoria.aplicacion.ConsultaAuditoriaService.Filtros;
import pe.edu.gesedu.auditoria.dominio.CambioAuditoria;
import pe.edu.gesedu.auditoria.dominio.RegistroAuditoria;
import pe.edu.gesedu.auditoria.infraestructura.CambioAuditoriaRepository;
import pe.edu.gesedu.auditoria.infraestructura.RegistroAuditoriaRepository;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioUsuarios;
import pe.edu.gesedu.transversal.reportes.GeneradorCsv;
import pe.edu.gesedu.transversal.reportes.GeneradorExcel;

/**
 * Exportación del historial de auditoría consultado a Excel o CSV (UC-25, paso 6, RF-51). Exporta exactamente lo
 * que filtra la consulta. La propia exportación queda en la auditoría (quién, cuándo y con qué filtros).
 */
@Service
public class ExportacionAuditoriaService {

    public enum Formato { XLSX, CSV }

    /** Tope de filas de una exportación: más que esto exige acotar los filtros. */
    public static final int MAXIMO_FILAS = 20_000;

    private static final List<String> ENCABEZADOS = List.of("Fecha y hora", "Usuario", "Acción", "Módulo",
            "Entidad", "Id de la entidad", "Descripción", "Cambios");
    private static final DateTimeFormatter MARCA_ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final ConsultaAuditoriaService consulta;
    private final RegistroAuditoriaRepository registros;
    private final CambioAuditoriaRepository cambios;
    private final DirectorioUsuarios directorio;
    private final GeneradorExcel excel;
    private final GeneradorCsv csv;
    private final Clock reloj;

    ExportacionAuditoriaService(ConsultaAuditoriaService consulta, RegistroAuditoriaRepository registros,
                                CambioAuditoriaRepository cambios, DirectorioUsuarios directorio,
                                GeneradorExcel excel, GeneradorCsv csv, Clock reloj) {
        this.consulta = consulta;
        this.registros = registros;
        this.cambios = cambios;
        this.directorio = directorio;
        this.excel = excel;
        this.csv = csv;
        this.reloj = reloj;
    }

    @Transactional
    @Auditable(accion = "EXPORTAR_AUDITORIA", modulo = "AUDITORIA", tipoEntidad = "RegistroAuditoria",
            descripcion = "Exportación del historial de auditoría")
    public ArchivoExportado exportar(Filtros filtros, Formato formato) {
        Specification<RegistroAuditoria> filtro = consulta.especificacion(filtros);
        long total = registros.count(filtro);
        if (total > MAXIMO_FILAS) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "La exportación admite hasta " + MAXIMO_FILAS
                    + " registros y el filtro devuelve " + total + ". Acota las fechas, el módulo o el usuario.");
        }
        List<RegistroAuditoria> lista = registros.findAll(filtro, PageRequest.of(0, MAXIMO_FILAS,
                Sort.by(Sort.Direction.DESC, "fechaHora").and(Sort.by(Sort.Direction.DESC, "id")))).getContent();

        Map<String, String> nombres = directorio.nombresCompletos(lista.stream().map(RegistroAuditoria::getUsuarioId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        Map<String, List<CambioAuditoria>> cambiosPorRegistro = lista.isEmpty() ? Map.of()
                : cambios.findByRegistroAuditoriaIdIn(lista.stream().map(RegistroAuditoria::getId).toList()).stream()
                        .collect(Collectors.groupingBy(CambioAuditoria::getRegistroAuditoriaId));

        List<List<Object>> filas = new ArrayList<>();
        for (RegistroAuditoria r : lista) {
            filas.add(java.util.Arrays.asList(r.getFechaHora(),
                    r.getUsuarioId() == null ? "Sistema" : nombres.getOrDefault(r.getUsuarioId(), r.getUsuarioId()),
                    r.getAccion(), r.getModulo(), r.getTipoEntidad(), r.getIdEntidadAfectada(), r.getDescripcion(),
                    resumenDeCambios(cambiosPorRegistro.getOrDefault(r.getId(), List.of()))));
        }

        ContextoAuditoria.registrarCambio("formato", null, formato.name());
        ContextoAuditoria.registrarCambio("registros", null, lista.size());
        registrarFiltro("usuarioId", filtros.usuarioId());
        registrarFiltro("modulo", filtros.modulo());
        registrarFiltro("accion", filtros.accion());
        registrarFiltro("desde", filtros.desde());
        registrarFiltro("hasta", filtros.hasta());

        String marca = MARCA_ARCHIVO.format(reloj.instant().atZone(reloj.getZone()));
        return switch (formato) {
            case XLSX -> new ArchivoExportado("auditoria-" + marca + ".xlsx", GeneradorExcel.TIPO_CONTENIDO,
                    excel.generar("Auditoría", ENCABEZADOS, filas));
            case CSV -> new ArchivoExportado("auditoria-" + marca + ".csv", GeneradorCsv.TIPO_CONTENIDO,
                    csv.generar(ENCABEZADOS, filas));
        };
    }

    private static void registrarFiltro(String nombre, Object valor) {
        if (valor != null && !valor.toString().isBlank()) {
            ContextoAuditoria.registrarCambio("filtro." + nombre, null, valor);
        }
    }

    /** «campo: antes → después; …» */
    private static String resumenDeCambios(List<CambioAuditoria> lista) {
        return lista.stream()
                .sorted(java.util.Comparator.comparing(CambioAuditoria::getCampo))
                .map(c -> c.getCampo() + ": " + valor(c.getValorAnterior()) + " → " + valor(c.getValorNuevo()))
                .collect(Collectors.joining("; "));
    }

    private static String valor(String valor) {
        return valor == null ? "∅" : valor;
    }
}
