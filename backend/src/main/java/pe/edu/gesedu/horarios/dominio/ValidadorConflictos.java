package pe.edu.gesedu.horarios.dominio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Reglas de conflicto de una clase candidata del borrador (RF-41, RF-42, RF-47), sin dependencias de Spring ni
 * de la base de datos. El servicio le entrega los hechos (qué hay ya en el bloque, si el docente está asociado al
 * curso y disponible) y recibe los conflictos; los mensajes con nombres los arma la capa de aplicación.
 */
public final class ValidadorConflictos {

    /** Qué regla se rompe. El orden es el de prioridad al rechazar el guardado. */
    public enum Tipo {
        /** El docente no está asociado al curso (RF-47): 422 DOCENTE_NO_ASOCIADO. */
        DOCENTE_NO_ASOCIADO,
        /** El docente no marcó ese bloque como disponible (UC-21, FE-1): 422 DOCENTE_NO_DISPONIBLE. */
        DOCENTE_NO_DISPONIBLE,
        /** El docente ya dicta otra clase en ese bloque (UC-21, FE-2): 409 CONFLICTO_HORARIO. */
        CRUCE_DOCENTE,
        /** La sección ya tiene otra clase en ese bloque (UC-21, FE-2): 409 CONFLICTO_HORARIO. */
        CRUCE_SECCION
    }

    /** La clase que se quiere guardar; {@code asignacionId} es el de la clase que se está editando (o nulo). */
    public record Candidata(String bloqueId, String seccionId, String cursoId, String docenteId, String asignacionId) {
    }

    /** Una clase que ya está en el borrador. */
    public record Existente(String id, String bloqueId, String seccionId, String cursoId, String docenteId) {
    }

    /** Un conflicto; para los cruces trae la clase existente con la que choca. */
    public record Conflicto(Tipo tipo, Existente con) {

        public boolean esDeCruce() {
            return tipo == Tipo.CRUCE_DOCENTE || tipo == Tipo.CRUCE_SECCION;
        }
    }

    private ValidadorConflictos() {
    }

    /**
     * @param existentes        las clases del borrador (se miran solo las del mismo bloque)
     * @param docenteAsociado   si el docente está asociado al curso y habilitado en el año
     * @param docenteDisponible si el docente marcó ese bloque como disponible
     * @return los conflictos, en orden de prioridad; vacío si la clase se puede guardar
     */
    public static List<Conflicto> validar(Candidata candidata, Collection<Existente> existentes,
                                          boolean docenteAsociado, boolean docenteDisponible) {
        List<Conflicto> conflictos = new ArrayList<>();
        if (!docenteAsociado) {
            conflictos.add(new Conflicto(Tipo.DOCENTE_NO_ASOCIADO, null));
        }
        if (!docenteDisponible) {
            conflictos.add(new Conflicto(Tipo.DOCENTE_NO_DISPONIBLE, null));
        }
        List<Existente> delBloque = existentes.stream()
                .filter(e -> e.bloqueId().equals(candidata.bloqueId()))
                // Al modificar una clase no choca consigo misma
                .filter(e -> !Objects.equals(e.id(), candidata.asignacionId()))
                .toList();
        delBloque.stream().filter(e -> e.docenteId().equals(candidata.docenteId()))
                .forEach(e -> conflictos.add(new Conflicto(Tipo.CRUCE_DOCENTE, e)));
        delBloque.stream().filter(e -> e.seccionId().equals(candidata.seccionId()))
                .forEach(e -> conflictos.add(new Conflicto(Tipo.CRUCE_SECCION, e)));
        return List.copyOf(conflictos);
    }
}
