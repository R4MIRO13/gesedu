package pe.edu.gesedu.evaluacion.aplicacion;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import pe.edu.gesedu.compartido.dominio.NivelLogro;
import pe.edu.gesedu.evaluacion.dominio.EstrategiaConsolidacion;
import pe.edu.gesedu.evaluacion.dominio.LogroPonderado;
import pe.edu.gesedu.evaluacion.dominio.TipoRegla;

/**
 * Motor de evaluación (DA-09): mapa {@link TipoRegla} → {@link EstrategiaConsolidacion} armado con todas las
 * implementaciones que Spring inyecta. Una regla nueva solo necesita su estrategia; este servicio no cambia.
 */
@Service
public class ConsolidacionService {

    private final Map<TipoRegla, EstrategiaConsolidacion> estrategias = new EnumMap<>(TipoRegla.class);

    public ConsolidacionService(List<EstrategiaConsolidacion> implementaciones) {
        for (EstrategiaConsolidacion estrategia : implementaciones) {
            if (estrategias.putIfAbsent(estrategia.tipo(), estrategia) != null) {
                throw new IllegalStateException("Hay dos estrategias para la regla " + estrategia.tipo());
            }
        }
        for (TipoRegla tipo : TipoRegla.values()) {
            if (!estrategias.containsKey(tipo)) {
                throw new IllegalStateException("Falta la estrategia de la regla " + tipo);
            }
        }
    }

    /** Consolida los logros con la regla indicada; sin logros no hay nota (vacío), no se inventa un nivel. */
    public Optional<NivelLogro> consolidar(TipoRegla regla, List<LogroPonderado> logros) {
        if (logros.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(estrategias.get(regla).consolidar(logros));
    }
}
