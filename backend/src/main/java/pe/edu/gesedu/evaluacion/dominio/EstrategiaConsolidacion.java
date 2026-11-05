package pe.edu.gesedu.evaluacion.dominio;

import java.util.List;

import pe.edu.gesedu.compartido.dominio.NivelLogro;

/**
 * Estrategia de consolidación (DA-09, patrón Strategy). Cada regla de RF-26 es una implementación; agregar una regla
 * nueva no modifica a {@code ConsolidacionService}, que recibe todas las implementaciones inyectadas por Spring.
 */
public interface EstrategiaConsolidacion {

    TipoRegla tipo();

    /** Consolida los logros (al menos uno) en un único nivel. */
    NivelLogro consolidar(List<LogroPonderado> logros);
}
