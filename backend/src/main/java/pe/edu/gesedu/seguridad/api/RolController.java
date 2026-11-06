package pe.edu.gesedu.seguridad.api;

import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.seguridad.aplicacion.RolesService;
import pe.edu.gesedu.seguridad.aplicacion.RolesService.CatalogoRoles;
import pe.edu.gesedu.seguridad.aplicacion.RolesService.RolDto;

/** Roles y permisos (RF-48): el administrador ve el catálogo y cambia los permisos de cada rol. */
@Tag(name = "Seguridad")
@RestController
@RequestMapping("/api/v1/roles")
class RolController {

    record PermisosRequest(
            @Schema(description = "Conjunto completo de permisos del rol (reemplaza al actual)",
                    example = "[\"HORARIO_CONSULTAR\", \"NOTA_REGISTRAR\"]") @NotNull Set<String> permisos) {
    }

    private final RolesService roles;

    RolController(RolesService roles) {
        this.roles = roles;
    }

    @Operation(summary = "Roles con sus permisos y el catálogo completo de permisos")
    @GetMapping
    @PreAuthorize("hasAuthority('ROL_GESTIONAR')")
    CatalogoRoles listar() {
        return roles.listar();
    }

    @Operation(summary = "Reemplaza los permisos del rol; se aplica en el siguiente request de sus usuarios y queda auditado. "
            + "400 si un permiso no existe o si al Administrador le faltan USUARIO_CONSULTAR, USUARIO_GESTIONAR o ROL_GESTIONAR")
    @PutMapping("/{id}/permisos")
    @PreAuthorize("hasAuthority('ROL_GESTIONAR')")
    RolDto reemplazarPermisos(@PathVariable String id, @Valid @RequestBody PermisosRequest s) {
        return roles.reemplazarPermisos(id, s.permisos());
    }
}
