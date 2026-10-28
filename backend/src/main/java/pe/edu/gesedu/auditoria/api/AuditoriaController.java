package pe.edu.gesedu.auditoria.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.auditoria.aplicacion.ArchivoExportado;
import pe.edu.gesedu.auditoria.aplicacion.CatalogosAuditoriaDto;
import pe.edu.gesedu.auditoria.aplicacion.ConsultaAuditoriaService;
import pe.edu.gesedu.auditoria.aplicacion.DetalleRegistroAuditoriaDto;
import pe.edu.gesedu.auditoria.aplicacion.ExportacionAuditoriaService;
import pe.edu.gesedu.auditoria.aplicacion.RegistroAuditoriaDto;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.compartido.paginacion.Pagina;

/** Consulta y exportación de la auditoría (UC-25, RF-49, RF-51). Los registros no se pueden editar ni borrar. */
@Tag(name = "Auditoría")
@RestController
@RequestMapping("/api/v1/auditoria")
class AuditoriaController {

    private final ConsultaAuditoriaService consulta;
    private final ExportacionAuditoriaService exportacion;

    AuditoriaController(ConsultaAuditoriaService consulta, ExportacionAuditoriaService exportacion) {
        this.consulta = consulta;
        this.exportacion = exportacion;
    }

    @Operation(summary = "Consulta los registros de auditoría (más recientes primero). "
            + "desde y hasta son fechas yyyy-MM-dd, inclusive; sort: fechaHora, accion o modulo, con ,asc o ,desc")
    @GetMapping("/registros")
    @PreAuthorize("hasAuthority('AUDITORIA_CONSULTAR')")
    Pagina<RegistroAuditoriaDto> listar(
            @RequestParam(required = false) String usuarioId,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return consulta.consultar(new ConsultaAuditoriaService.Filtros(usuarioId, modulo, accion, desde, hasta),
                page, size, sort);
    }

    @Operation(summary = "Exporta a Excel (xlsx) o CSV los registros que devuelve el mismo filtro de la consulta. "
            + "Admite hasta 20 000 registros; la exportación queda auditada")
    @GetMapping("/registros/exportacion")
    @PreAuthorize("hasAuthority('AUDITORIA_EXPORTAR')")
    ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) String usuarioId,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "xlsx") String formato) {
        ExportacionAuditoriaService.Formato tipo = switch (formato.toLowerCase()) {
            case "xlsx" -> ExportacionAuditoriaService.Formato.XLSX;
            case "csv" -> ExportacionAuditoriaService.Formato.CSV;
            default -> throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "formato debe ser xlsx o csv");
        };
        ArchivoExportado archivo = exportacion.exportar(
                new ConsultaAuditoriaService.Filtros(usuarioId, modulo, accion, desde, hasta), tipo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
    }

    @Operation(summary = "Módulos con sus acciones y usuarios que figuran en la auditoría (para los filtros)")
    @GetMapping("/catalogos")
    @PreAuthorize("hasAuthority('AUDITORIA_CONSULTAR')")
    CatalogosAuditoriaDto catalogos() {
        return consulta.catalogos();
    }

    @Operation(summary = "Un registro con los campos que cambiaron (valor anterior y nuevo)")
    @GetMapping("/registros/{id}")
    @PreAuthorize("hasAuthority('AUDITORIA_CONSULTAR')")
    DetalleRegistroAuditoriaDto detalle(@PathVariable String id) {
        return consulta.detalle(id);
    }
}
