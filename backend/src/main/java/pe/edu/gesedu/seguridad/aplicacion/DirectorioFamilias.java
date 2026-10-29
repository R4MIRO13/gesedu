package pe.edu.gesedu.seguridad.aplicacion;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.seguridad.infraestructura.FamiliaRepository;
import pe.edu.gesedu.seguridad.infraestructura.FamiliaRepository.AlumnoFila;
import pe.edu.gesedu.seguridad.infraestructura.FamiliaRepository.ApoderadoFila;

/** Datos de alumnos y apoderados para otros módulos (matrícula, pagos). Devuelve {@code record}, nunca entidades. */
@Service
public class DirectorioFamilias {

    public record AlumnoResumen(String id, String codigo, String nombreCompleto, String documento,
                                LocalDate fechaNacimiento, String parentescoApoderado) {
    }

    public record ApoderadoResumen(String id, String nombreCompleto, String documento, String correo,
                                   String telefono) {
    }

    private final FamiliaRepository familias;

    DirectorioFamilias(FamiliaRepository familias) {
        this.familias = familias;
    }

    /** Id → alumno. Los ids que no existen no aparecen. */
    @Transactional(readOnly = true)
    public Map<String, AlumnoResumen> alumnos(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return familias.alumnos(ids).stream().map(DirectorioFamilias::aResumen)
                .collect(Collectors.toMap(AlumnoResumen::id, Function.identity()));
    }

    /** Id → apoderado. Los ids que no existen no aparecen. */
    @Transactional(readOnly = true)
    public Map<String, ApoderadoResumen> apoderados(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return familias.apoderados(ids).stream().map(DirectorioFamilias::aResumen)
                .collect(Collectors.toMap(ApoderadoResumen::id, Function.identity()));
    }

    /** Ids de los alumnos cuyo nombre, documento o código contiene el texto (búsqueda de la bandeja; se ignoran los comodines). */
    @Transactional(readOnly = true)
    public List<String> buscarAlumnoIds(String texto) {
        String patron = "%" + texto.trim().toLowerCase().replaceAll("[%_]", "") + "%";
        return familias.buscarAlumnoIds(patron);
    }

    private static AlumnoResumen aResumen(AlumnoFila f) {
        return new AlumnoResumen(f.getId(), f.getCodigo(), f.getNombreCompleto(), f.getDocumento(),
                f.getFechaNacimiento(), f.getParentesco());
    }

    private static ApoderadoResumen aResumen(ApoderadoFila f) {
        return new ApoderadoResumen(f.getId(), f.getNombreCompleto(), f.getDocumento(), f.getCorreo(), f.getTelefono());
    }
}
