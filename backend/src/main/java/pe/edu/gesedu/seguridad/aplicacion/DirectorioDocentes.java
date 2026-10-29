package pe.edu.gesedu.seguridad.aplicacion;

import java.util.Collection;
import java.util.function.Function;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.seguridad.infraestructura.DocenteRepository;

/** Nombres de docentes para otros módulos (horarios, asistencia, evaluación). */
@Service
public class DirectorioDocentes {

    /**
     * Docente con su nombre. {@code nombreCorto} es «R. Quispe» (inicial y primer apellido), el que usan los
     * mensajes de conflicto de horario.
     */
    public record DocenteResumen(String id, String nombres, String apellidos) {

        public String nombreCompleto() {
            return nombres + " " + apellidos;
        }

        public String nombreCorto() {
            String inicial = nombres.isBlank() ? "" : nombres.trim().charAt(0) + ". ";
            String primerApellido = apellidos.isBlank() ? "" : apellidos.trim().split("\\s+")[0];
            return inicial + primerApellido;
        }
    }

    private final DocenteRepository docentes;

    DirectorioDocentes(DocenteRepository docentes) {
        this.docentes = docentes;
    }

    /** Id → docente. Los ids que no existen no aparecen en el mapa. */
    @Transactional(readOnly = true)
    public Map<String, DocenteResumen> porIds(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return docentes.buscarConNombres(ids).stream()
                .map(d -> new DocenteResumen(d.getId(), d.getNombres(), d.getApellidos()))
                .collect(Collectors.toMap(DocenteResumen::id, Function.identity()));
    }
}
