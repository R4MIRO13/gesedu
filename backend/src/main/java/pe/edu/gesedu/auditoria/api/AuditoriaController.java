package pe.edu.gesedu.auditoria.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.auditoria.aplicacion.ConsultaAuditoriaService;
import pe.edu.gesedu.auditoria.aplicacion.RegistroAuditoriaDto;
import pe.edu.gesedu.compartido.paginacion.Pagina;

@Tag(name = "Auditoría")
@RestController
@RequestMapping("/api/v1/auditoria")
class AuditoriaController {

    private final ConsultaAuditoriaService consulta;

    AuditoriaController(ConsultaAuditoriaService consulta) {
        this.consulta = consulta;
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
}
