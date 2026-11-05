package pe.edu.gesedu.evaluacion.aplicacion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import pe.edu.gesedu.compartido.dominio.NivelLogro;
import pe.edu.gesedu.evaluacion.dominio.LogroPonderado;
import pe.edu.gesedu.evaluacion.dominio.PesoBimestre;
import pe.edu.gesedu.evaluacion.dominio.RegistroLogro;
import pe.edu.gesedu.evaluacion.dominio.TipoRegla;

/**
 * Nota anual por competencia (RF-28, S-66): la regla de consolidación aplicada a los logros de los bimestres del alumno
 * en esa competencia, con el peso de cada bimestre (RF-38; 1 si no se ajustó). Es una función de los datos: calcularla
 * de nuevo con los mismos datos da el mismo resultado, que es lo que vuelve idempotente el cálculo (DA-09).
 */
@Component
class CalculadorNotaAnual {

    private final ConsolidacionService consolidacion;

    CalculadorNotaAnual(ConsolidacionService consolidacion) {
        this.consolidacion = consolidacion;
    }

    /**
     * @param logros          los logros de la asignación en todos los bimestres del año
     * @param numeroDeBimestre número de cada bimestre por su id (el orden cronológico que decide moda y último registro)
     * @param pesos           peso por id de bimestre; los ausentes pesan 1
     * @return nivel anual por {@code alumnoId|competenciaId}; las celdas sin ningún logro no aparecen (no hay nota)
     */
    Map<String, NivelLogro> calcular(TipoRegla regla, List<RegistroLogro> logros, Map<String, Integer> numeroDeBimestre,
                                     Map<String, BigDecimal> pesos) {
        Map<String, List<LogroPonderado>> porCelda = new HashMap<>();
        for (RegistroLogro logro : logros) {
            Integer orden = numeroDeBimestre.get(logro.getBimestreId());
            if (orden == null) {
                continue;
            }
            BigDecimal peso = pesos.getOrDefault(logro.getBimestreId(), PesoBimestre.PESO_POR_DEFECTO);
            porCelda.computeIfAbsent(clave(logro.getAlumnoId(), logro.getCompetenciaId()), k -> new ArrayList<>())
                    .add(new LogroPonderado(logro.getNivelLogro(), peso, orden));
        }
        Map<String, NivelLogro> resultado = new HashMap<>();
        porCelda.forEach((celda, lista) -> consolidacion.consolidar(regla, lista).ifPresent(n -> resultado.put(celda, n)));
        return resultado;
    }

    static String clave(String alumnoId, String competenciaId) {
        return alumnoId + "|" + competenciaId;
    }
}
