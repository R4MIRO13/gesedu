package pe.edu.gesedu.horarios.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.compartido.web.Etags;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.AsignacionDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.BorradorDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.OpcionesDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.Perspectiva;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.ResultadoAsignacion;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.SolicitudAsignacion;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.ValidacionDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioService;

/**
 * Elaboración del horario (UC-21, RF-39 a RF-42, RF-47). Solo para quien gestiona horarios: el borrador no lo
 * ve ningún alumno ni apoderado (RF-44). El borrador devuelve {@code ETag} con su versión; las altas, cambios y
 * bajas exigen {@code If-Match} (400 VERSION_REQUERIDA sin la cabecera, 409 CONFLICTO_CONCURRENCIA si cambió).
 */
@Tag(name = "Horarios")
@RestController
@RequestMapping("/api/v1/horarios/{anio}/borrador")
class BorradorHorarioController {

    private final BorradorHorarioService borrador;

    BorradorHorarioController(BorradorHorarioService borrador) {
        this.borrador = borrador;
    }

    @Operation(summary = "El borrador del año para una perspectiva (lo crea si no existe). Devuelve ETag con la versión",
            description = "perspectiva=SECCION con seccionId, o perspectiva=DOCENTE con docenteId; ambas ven el mismo "
                    + "borrador. Sin id se toma la primera sección o el primer docente.")
    @GetMapping
    @PreAuthorize("hasAuthority('HORARIO_GESTIONAR')")
    ResponseEntity<BorradorDto> obtener(@PathVariable int anio,
                                        @RequestParam(defaultValue = "SECCION") Perspectiva perspectiva,
                                        @RequestParam(required = false) String seccionId,
                                        @RequestParam(required = false) String docenteId) {
        BorradorDto dto = borrador.obtener(anio, perspectiva, seccionId, docenteId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etags.de(dto.version())).body(dto);
    }

    @Operation(summary = "Cursos de la sección con sus horas y, si se elige un curso, los docentes asociados con "
            + "su disponibilidad en el bloque y si ya tienen clase en él")
    @GetMapping("/opciones")
    @PreAuthorize("hasAuthority('HORARIO_GESTIONAR')")
    OpcionesDto opciones(@PathVariable int anio, @RequestParam String bloqueId, @RequestParam String seccionId,
                         @RequestParam(required = false) String cursoId,
                         @RequestParam(required = false) String docenteId,
                         @RequestParam(required = false) String asignacionId) {
        return borrador.opciones(anio, bloqueId, seccionId, cursoId, docenteId, asignacionId);
    }

    @Operation(summary = "Valida en vivo una clase (cruces de docente y de sección, asociación y disponibilidad) "
            + "sin guardarla. Responde 200 siempre; los conflictos vienen en la lista")
    @PostMapping("/validaciones")
    @PreAuthorize("hasAuthority('HORARIO_GESTIONAR')")
    ValidacionDto validar(@PathVariable int anio, @Valid @RequestBody AsignacionRequest solicitud) {
        return borrador.validar(anio, aSolicitud(solicitud, solicitud.asignacionId()));
    }

    @Operation(summary = "Agrega una clase al borrador. Exige If-Match. 409 por cruce o por versión; 422 si el "
            + "docente no está asociado al curso o no está disponible")
    @PostMapping("/asignaciones")
    @PreAuthorize("hasAuthority('HORARIO_GESTIONAR')")
    ResponseEntity<AsignacionDto> crear(@PathVariable int anio, @Valid @RequestBody AsignacionRequest solicitud,
                                        @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        ResultadoAsignacion resultado = borrador.crear(anio, aSolicitud(solicitud, null), ifMatch);
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.ETAG, Etags.de(resultado.version()))
                .body(resultado.asignacion());
    }

    @Operation(summary = "Modifica el bloque, el curso o el docente de una clase (FA-1). Exige If-Match")
    @PutMapping("/asignaciones/{id}")
    @PreAuthorize("hasAuthority('HORARIO_GESTIONAR')")
    ResponseEntity<AsignacionDto> modificar(@PathVariable int anio, @PathVariable String id,
                                            @Valid @RequestBody AsignacionRequest solicitud,
                                            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        ResultadoAsignacion resultado = borrador.modificar(anio, id, aSolicitud(solicitud, id), ifMatch);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etags.de(resultado.version()))
                .body(resultado.asignacion());
    }

    @Operation(summary = "Elimina una clase del borrador (FA-2). Exige If-Match")
    @DeleteMapping("/asignaciones/{id}")
    @PreAuthorize("hasAuthority('HORARIO_GESTIONAR')")
    ResponseEntity<Void> eliminar(@PathVariable int anio, @PathVariable String id,
                                  @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        long version = borrador.eliminar(anio, id, ifMatch);
        return ResponseEntity.noContent().header(HttpHeaders.ETAG, Etags.de(version)).build();
    }

    private static SolicitudAsignacion aSolicitud(AsignacionRequest r, String asignacionId) {
        return new SolicitudAsignacion(r.bloqueId(), r.seccionId(), r.cursoId(), r.docenteId(), asignacionId);
    }
}
