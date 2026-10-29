package pe.edu.gesedu.seguridad.aplicacion;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                                LocalDate fechaNacimiento, String parentescoApoderado, String apoderadoId) {
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

    /** El perfil de apoderado de la cuenta, si la cuenta es de un apoderado. */
    @Transactional(readOnly = true)
    public Optional<ApoderadoResumen> apoderadoDeUsuario(String usuarioId) {
        return familias.apoderadosDelUsuario(usuarioId).stream().findFirst().map(DirectorioFamilias::aResumen);
    }

    /** El perfil de alumno de la cuenta, si la cuenta es de un alumno. */
    @Transactional(readOnly = true)
    public Optional<AlumnoResumen> alumnoDeUsuario(String usuarioId) {
        return familias.alumnosDelUsuario(usuarioId).stream().findFirst().map(DirectorioFamilias::aResumen);
    }

    /** Los hijos de un apoderado, ordenados por apellidos (alcance «mis hijos»). */
    @Transactional(readOnly = true)
    public List<AlumnoResumen> hijosDe(String apoderadoId) {
        return familias.alumnosDelApoderado(apoderadoId).stream().map(DirectorioFamilias::aResumen).toList();
    }

    /** El alumno con ese código (por ejemplo, {@code 2026-0315}), si existe. */
    @Transactional(readOnly = true)
    public Optional<AlumnoResumen> alumnoPorCodigo(String codigo) {
        return familias.alumnosPorCodigo(codigo).stream().findFirst().map(DirectorioFamilias::aResumen);
    }

    /** El alumno con ese documento de identidad, si existe. */
    @Transactional(readOnly = true)
    public Optional<AlumnoResumen> alumnoPorDocumento(String documento) {
        return familias.alumnosPorDocumento(documento).stream().findFirst().map(DirectorioFamilias::aResumen);
    }

    private static AlumnoResumen aResumen(AlumnoFila f) {
        return new AlumnoResumen(f.getId(), f.getCodigo(), f.getNombreCompleto(), f.getDocumento(),
                f.getFechaNacimiento(), f.getParentesco(), f.getApoderadoId());
    }

    private static ApoderadoResumen aResumen(ApoderadoFila f) {
        return new ApoderadoResumen(f.getId(), f.getNombreCompleto(), f.getDocumento(), f.getCorreo(), f.getTelefono());
    }
}
