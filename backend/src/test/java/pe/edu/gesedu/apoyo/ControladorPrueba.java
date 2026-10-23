package pe.edu.gesedu.apoyo;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints que solo existen en las pruebas: ejercitan JWT y {@code @PreAuthorize} sin esperar a los reales. */
@RestController
@RequestMapping("/api/v1/prueba")
class ControladorPrueba {

    @GetMapping("/horario")
    @PreAuthorize("hasAuthority('HORARIO_CONSULTAR')")
    Map<String, String> consultarHorario() {
        return Map.of("ok", "horario");
    }

    @GetMapping("/auditoria")
    @PreAuthorize("hasAuthority('AUDITORIA_CONSULTAR')")
    Map<String, String> consultarAuditoria() {
        return Map.of("ok", "auditoria");
    }
}
