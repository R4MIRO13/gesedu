package pe.edu.gesedu.horarios.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.compartido.web.Etags;
import pe.edu.gesedu.horarios.aplicacion.PublicacionDtos.CompletitudDto;
import pe.edu.gesedu.horarios.aplicacion.PublicacionDtos.PublicacionDto;
import pe.edu.gesedu.horarios.aplicacion.PublicacionHorarioService;

/** Completitud y publicación del horario (UC-22, RF-43, RF-44). Solo para quien gestiona horarios. */
@Tag(name = "Horarios")
@RestController
@RequestMapping("/api/v1/horarios/{anio}")
class PublicacionHorarioController {

    private final PublicacionHorarioService publicacion;

    PublicacionHorarioController(PublicacionHorarioService publicacion) {
        this.publicacion = publicacion;
    }

    @Operation(summary = "Compara, sección por sección, los bloques del borrador con la carga semanal de cada curso",
            description = "Devuelve el ETag con la versión del borrador revisado: se manda en `If-Match` al publicar. "
                    + "`publicable` es verdadero solo si todas las secciones están completas; las advertencias (por ejemplo, "
                    + "docentes sin disponibilidad confirmada) no bloquean.")
    @GetMapping("/borrador/completitud")
    @PreAuthorize("hasAuthority('HORARIO_GESTIONAR')")
    ResponseEntity<CompletitudDto> completitud(@PathVariable int anio) {
        CompletitudDto dto = publicacion.completitud(anio);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etags.de(dto.versionBorrador())).body(dto);
    }

    @Operation(summary = "Publica el borrador como nueva versión vigente (la anterior deja de serlo). Exige If-Match",
            description = "422 HORARIO_INCOMPLETO con `secciones[]` si alguna sección no cuadra con su carga (el borrador no "
                    + "cambia); 409 CONFLICTO_CONCURRENCIA si el borrador cambió desde que se revisó. Sincroniza los "
                    + "responsables de cada curso con la evaluación y queda auditado.")
    @PostMapping("/publicaciones")
    @PreAuthorize("hasAuthority('HORARIO_GESTIONAR')")
    ResponseEntity<PublicacionDto> publicar(@PathVariable int anio,
                                            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return ResponseEntity.status(HttpStatus.CREATED).body(publicacion.publicar(anio, ifMatch));
    }

    @Operation(summary = "Versiones publicadas del año, de la más reciente a la más antigua")
    @GetMapping("/publicaciones")
    @PreAuthorize("hasAuthority('HORARIO_GESTIONAR')")
    List<PublicacionDto> versiones(@PathVariable int anio) {
        return publicacion.versiones(anio);
    }
}
