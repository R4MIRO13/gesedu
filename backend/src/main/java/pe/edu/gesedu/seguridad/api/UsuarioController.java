package pe.edu.gesedu.seguridad.api;

import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.compartido.paginacion.Pagina;
import pe.edu.gesedu.compartido.web.Etags;
import pe.edu.gesedu.compartido.web.ParametrosPaginacion;
import pe.edu.gesedu.seguridad.aplicacion.GestionUsuariosService;
import pe.edu.gesedu.seguridad.aplicacion.GestionUsuariosService.CambiosUsuario;
import pe.edu.gesedu.seguridad.aplicacion.GestionUsuariosService.DetalleUsuario;
import pe.edu.gesedu.seguridad.aplicacion.GestionUsuariosService.NuevoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.UsuariosService;
import pe.edu.gesedu.seguridad.aplicacion.UsuariosService.EstadoCuenta;
import pe.edu.gesedu.seguridad.aplicacion.UsuariosService.UsuarioFila;

/** Cuentas de usuario (UC-24, RF-01): lista, alta con invitación, modificación, activación, desactivación y desbloqueo. */
@Tag(name = "Seguridad")
@RestController
@RequestMapping("/api/v1/usuarios")
class UsuarioController {

    private static final Set<String> ORDENABLES = Set.of("apellidos", "nombres", "ultimoAcceso");

    record UsuarioRequest(
            @Schema(description = "ADMINISTRADOR, DOCENTE o APODERADO (el alumno nace con la solicitud de matrícula)",
                    example = "DOCENTE") @NotBlank @Size(max = 30) String rol,
            @Schema(example = "Luis") @NotBlank @Size(max = 100) String nombres,
            @Schema(example = "Paredes Soto") @NotBlank @Size(max = 100) String apellidos,
            @Schema(description = "DNI de 8 dígitos", example = "45678901")
            @NotBlank @Pattern(regexp = "^\\d{8}$", message = "El documento de identidad debe tener 8 dígitos") String documento,
            @Schema(example = "luis.paredes@colegio.pe") @NotBlank @Email @Size(max = 254) String correo,
            @Schema(description = "Obligatorio para ADMINISTRADOR", example = "Subdirector") @Size(max = 80) String cargo,
            @Schema(description = "Solo DOCENTE", example = "Matemática") @Size(max = 80) String especialidad,
            @Schema(description = "Solo APODERADO", example = "987654321") @Size(max = 20) String telefono) {
    }

    record CambiosRequest(
            @NotBlank @Size(max = 100) String nombres,
            @NotBlank @Size(max = 100) String apellidos,
            @Schema(description = "Un documento nuevo debe ser un DNI de 8 dígitos")
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{4,20}$",
                    message = "El documento de identidad solo admite letras, números y guiones") String documento,
            @NotBlank @Email @Size(max = 254) String correo,
            @Size(max = 80) String cargo,
            @Size(max = 80) String especialidad,
            @Size(max = 20) String telefono) {
    }

    record ActivacionRequest(
            @Schema(description = "Solo para completar una cuenta sin correo (el alumno nace sin él)")
            @Email @Size(max = 254) String correo) {
    }

    private final UsuariosService usuarios;
    private final GestionUsuariosService gestion;

    UsuarioController(UsuariosService usuarios, GestionUsuariosService gestion) {
        this.usuarios = usuarios;
        this.gestion = gestion;
    }

    private static ResponseEntity<DetalleUsuario> conEtag(DetalleUsuario d) {
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etags.de(d.version())).body(d);
    }

    @Operation(summary = "Lista las cuentas, paginada, filtrable por texto (nombre o correo), rol y estado")
    @GetMapping
    @PreAuthorize("hasAuthority('USUARIO_CONSULTAR')")
    Pagina<UsuarioFila> listar(@RequestParam(required = false) String q,
                               @RequestParam(required = false) String rol,
                               @RequestParam(required = false) EstadoCuenta estado,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size,
                               @RequestParam(required = false) String sort) {
        return usuarios.listar(q, rol, estado, ParametrosPaginacion.de(page, size, sort, ORDENABLES,
                Sort.by(Sort.Direction.ASC, "apellidos", "nombres")));
    }

    @Operation(summary = "Ficha de la cuenta con su perfil. Devuelve ETag con la versión de la cuenta")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIO_CONSULTAR')")
    ResponseEntity<DetalleUsuario> obtener(@PathVariable String id) {
        return conEtag(gestion.obtener(id));
    }

    @Operation(summary = "Crea una cuenta pendiente (UC-24) y le envía la invitación de 72 h para definir su contraseña. "
            + "El administrador no define ni ve la contraseña. 409 DOCUMENTO_DUPLICADO o CORREO_DUPLICADO (FA-1)")
    @PostMapping
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    ResponseEntity<DetalleUsuario> crear(@Valid @RequestBody UsuarioRequest s) {
        DetalleUsuario creado = gestion.crear(new NuevoUsuario(s.rol(), s.nombres(), s.apellidos(), s.documento(),
                s.correo(), s.cargo(), s.especialidad(), s.telefono()));
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.ETAG, Etags.de(creado.version())).body(creado);
    }

    @Operation(summary = "Modifica los datos de la cuenta; el rol no cambia. Exige If-Match; 409 DOCUMENTO_DUPLICADO, "
            + "CORREO_DUPLICADO o CONFLICTO_CONCURRENCIA. Con la cuenta pendiente, un correo nuevo reenvía la invitación")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    ResponseEntity<DetalleUsuario> modificar(@PathVariable String id,
                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                             @Valid @RequestBody CambiosRequest s) {
        return conEtag(gestion.modificar(id, new CambiosUsuario(s.nombres(), s.apellidos(), s.documento(), s.correo(),
                s.cargo(), s.especialidad(), s.telefono()), ifMatch));
    }

    @Operation(summary = "Habilita la cuenta. El alumno exige matrícula activa (422 VINCULO_ACADEMICO_INEXISTENTE); "
            + "el apoderado y el personal, no. Sin contraseña todavía, envía la invitación y queda PENDIENTE")
    @PatchMapping("/{id}/activacion")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    ResponseEntity<DetalleUsuario> activar(@PathVariable String id,
                                           @Valid @RequestBody(required = false) ActivacionRequest s) {
        return conEtag(gestion.activar(id, s == null ? null : s.correo()));
    }

    @Operation(summary = "Desactiva la cuenta y revoca sus sesiones de inmediato (el siguiente request da 401). "
            + "No se puede desactivar la cuenta propia")
    @PatchMapping("/{id}/desactivacion")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    ResponseEntity<DetalleUsuario> desactivar(@PathVariable String id) {
        return conEtag(gestion.desactivar(id));
    }

    @Operation(summary = "Reenvía la invitación a una cuenta pendiente (la anterior queda anulada). 202")
    @PostMapping("/{id}/invitacion")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    ResponseEntity<DetalleUsuario> reenviarInvitacion(@PathVariable String id) {
        DetalleUsuario d = gestion.reenviarInvitacion(id);
        return ResponseEntity.accepted().header(HttpHeaders.ETAG, Etags.de(d.version())).body(d);
    }

    @Operation(summary = "Levanta el bloqueo temporal de una cuenta (queda auditado). 400 si la cuenta no está bloqueada")
    @PatchMapping("/{id}/desbloqueo")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    UsuarioFila desbloquear(@PathVariable String id) {
        return usuarios.desbloquear(id);
    }
}
