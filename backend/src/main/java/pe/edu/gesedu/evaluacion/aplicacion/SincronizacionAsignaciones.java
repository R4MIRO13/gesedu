package pe.edu.gesedu.evaluacion.aplicacion;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.evaluacion.dominio.AsignacionDocente;
import pe.edu.gesedu.evaluacion.infraestructura.AsignacionDocenteRepository;

/**
 * Mantiene al día los responsables de cada curso en cada sección cuando se publica un horario (S-13). Lo llama el
 * módulo de horarios dentro de su propia transacción: si la publicación se revierte, esta sincronización también.
 * Es idempotente: repetirla con el mismo horario no cambia nada.
 */
@Service
public class SincronizacionAsignaciones {

    /** El docente que responde por un curso en una sección. */
    public record Responsable(String cursoId, String seccionId, String docenteId) {
    }

    /** Cuántas asignaciones se crearon, cambiaron de docente o quedaron igual. */
    public record Resultado(int creadas, int reasignadas, int sinCambios) {
    }

    private final AsignacionDocenteRepository asignaciones;

    SincronizacionAsignaciones(AsignacionDocenteRepository asignaciones) {
        this.asignaciones = asignaciones;
    }

    /** Responsables que ya existen en las secciones indicadas, por {@code cursoId|seccionId}. */
    @Transactional(readOnly = true)
    public Map<String, String> responsablesActuales(Set<String> seccionIds) {
        if (seccionIds.isEmpty()) {
            return Map.of();
        }
        return asignaciones.findBySeccionIdIn(seccionIds).stream()
                .collect(Collectors.toMap(a -> a.getCursoId() + "|" + a.getSeccionId(), AsignacionDocente::getDocenteId));
    }

    /**
     * Crea las asignaciones que faltan y cambia el docente de las que ya existen con otro. Las asignaciones de cursos
     * que el nuevo horario ya no incluye no se borran: pueden tener notas.
     */
    @Transactional
    public Resultado sincronizar(List<Responsable> responsables) {
        Set<String> secciones = new HashSet<>();
        responsables.forEach(r -> secciones.add(r.seccionId()));
        Map<String, AsignacionDocente> existentes = secciones.isEmpty() ? Map.of()
                : asignaciones.findBySeccionIdIn(secciones).stream()
                        .collect(Collectors.toMap(a -> a.getCursoId() + "|" + a.getSeccionId(), a -> a));
        int creadas = 0;
        int reasignadas = 0;
        int sinCambios = 0;
        for (Responsable r : responsables) {
            AsignacionDocente actual = existentes.get(r.cursoId() + "|" + r.seccionId());
            if (actual == null) {
                asignaciones.save(new AsignacionDocente(r.docenteId(), r.cursoId(), r.seccionId()));
                creadas++;
            } else if (!actual.getDocenteId().equals(r.docenteId())) {
                actual.asignarA(r.docenteId());
                reasignadas++;
            } else {
                sinCambios++;
            }
        }
        asignaciones.flush();
        return new Resultado(creadas, reasignadas, sinCambios);
    }
}
