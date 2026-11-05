package pe.edu.gesedu.evaluacion.dominio;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import pe.edu.gesedu.compartido.dominio.NivelLogro;

/** Último registro: el nivel del bimestre de mayor número (el más reciente), como en el wireframe de la regla. */
@Component
public class UltimoRegistroStrategy implements EstrategiaConsolidacion {

    @Override
    public TipoRegla tipo() {
        return TipoRegla.ULTIMO_REGISTRO;
    }

    @Override
    public NivelLogro consolidar(List<LogroPonderado> logros) {
        ValidacionLogros.exigirNoVacio(logros);
        return logros.stream().max(Comparator.comparingInt(LogroPonderado::orden)).orElseThrow().nivel();
    }
}
