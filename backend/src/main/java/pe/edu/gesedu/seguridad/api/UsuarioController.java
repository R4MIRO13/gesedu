package pe.edu.gesedu.seguridad.api;

import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.compartido.paginacion.Pagina;
import pe.edu.gesedu.compartido.web.ParametrosPaginacion;
import pe.edu.gesedu.seguridad.aplicacion.UsuariosService;
import pe.edu.gesedu.seguridad.aplicacion.UsuariosService.EstadoCuenta;
import pe.edu.gesedu.seguridad.aplicacion.UsuariosService.UsuarioFila;

/** Cuentas de usuario (UC-24, RF-01): lista con filtros y desbloqueo. */
@Tag(name = "Seguridad")
@RestController
@RequestMapping("/api/v1/usuarios")
class UsuarioController {

    private static final Set<String> ORDENABLES = Set.of("apellidos", "nombres", "ultimoAcceso");

    private final UsuariosService usuarios;

    UsuarioController(UsuariosService usuarios) {
        this.usuarios = usuarios;
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

    @Operation(summary = "Levanta el bloqueo temporal de una cuenta (queda auditado). 400 si la cuenta no está bloqueada")
    @PatchMapping("/{id}/desbloqueo")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    UsuarioFila desbloquear(@PathVariable String id) {
        return usuarios.desbloquear(id);
    }
}
