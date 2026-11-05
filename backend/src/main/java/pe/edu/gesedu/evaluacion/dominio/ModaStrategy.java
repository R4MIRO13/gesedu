package pe.edu.gesedu.evaluacion.dominio;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import pe.edu.gesedu.compartido.dominio.NivelLogro;

/** Moda: el nivel más frecuente; en un empate gana el nivel cuyo registro es el más reciente (S-04). Los pesos no cuentan. */
@Component
public class ModaStrategy implements EstrategiaConsolidacion {

    @Override
    public TipoRegla tipo() {
        return TipoRegla.MODA;
    }

    @Override
    public NivelLogro consolidar(List<LogroPonderado> logros) {
        ValidacionLogros.exigirNoVacio(logros);
        Map<NivelLogro, Integer> frecuencia = new EnumMap<>(NivelLogro.class);
        Map<NivelLogro, Integer> masReciente = new EnumMap<>(NivelLogro.class);
        for (LogroPonderado logro : logros) {
            frecuencia.merge(logro.nivel(), 1, Integer::sum);
            masReciente.merge(logro.nivel(), logro.orden(), Math::max);
        }
        return frecuencia.keySet().stream()
                .max(Comparator.<NivelLogro>comparingInt(frecuencia::get).thenComparingInt(masReciente::get))
                .orElseThrow();
    }
}
