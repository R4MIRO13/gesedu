package pe.edu.gesedu.horarios.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.horarios.aplicacion.BloqueHorarioDto;
import pe.edu.gesedu.horarios.aplicacion.BloqueHorarioService;

@Tag(name = "Horarios")
@RestController
@RequestMapping("/api/v1/anios-escolares/{anio}/bloques")
class BloqueHorarioController {

    private final BloqueHorarioService bloques;

    BloqueHorarioController(BloqueHorarioService bloques) {
        this.bloques = bloques;
    }

    // Desviación temporal de la demo (docs/PLAN.md § 7.0): lo consultan todos los roles
    @Operation(summary = "Lista los bloques horarios del año, ordenados por día y hora")
    @GetMapping
    @PreAuthorize("hasAuthority('HORARIO_CONSULTAR')")
    List<BloqueHorarioDto> listar(@PathVariable int anio) {
        return bloques.listar(anio);
    }

    @Operation(summary = "Elimina un bloque que aún no se usa. 409 BLOQUE_EN_USO si hay disponibilidades o clases que lo referencian")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('HORARIO_CONFIGURAR')")
    void eliminar(@PathVariable int anio, @PathVariable String id) {
        bloques.eliminar(anio, id);
    }

    @Operation(summary = "Crea un bloque horario (solo Administrador). 409 si se superpone con otro del mismo día")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('HORARIO_CONFIGURAR')")
    BloqueHorarioDto crear(@PathVariable int anio, @Valid @RequestBody CrearBloqueRequest solicitud) {
        return bloques.crear(anio, solicitud.diaSemana(), solicitud.horaInicio(), solicitud.horaFin());
    }
}
