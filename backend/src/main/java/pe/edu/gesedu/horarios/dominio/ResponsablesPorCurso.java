package pe.edu.gesedu.horarios.dominio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Docente responsable de cada curso en cada sección (S-13): al publicar el horario, la evaluación necesita un solo
 * docente por (curso, sección) aunque varios dicten bloques de él. Responde el que más bloques tiene; si hay empate,
 * el que ya era responsable (la asignación no cambia sin motivo) y, si no, el de id menor, para que el resultado
 * no dependa del orden de las filas.
 */
public final class ResponsablesPorCurso {

    private ResponsablesPorCurso() {
    }

    public record Clase(String cursoId, String seccionId, String docenteId) {
    }

    public record Responsable(String cursoId, String seccionId, String docenteId) {
    }

    /**
     * @param clases      las clases del horario que se publica
     * @param anteriores  responsables actuales por {@code cursoId|seccionId} (vacío si no hay)
     */
    public static List<Responsable> elegir(List<Clase> clases, Map<String, String> anteriores) {
        Map<String, Map<String, Integer>> bloquesPorDocente = new HashMap<>();
        for (Clase c : clases) {
            bloquesPorDocente.computeIfAbsent(clave(c.cursoId(), c.seccionId()), k -> new HashMap<>())
                    .merge(c.docenteId(), 1, Integer::sum);
        }
        List<Responsable> resultado = new ArrayList<>();
        bloquesPorDocente.forEach((clave, docentes) -> {
            int maximo = docentes.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            Set<String> empatados = new java.util.TreeSet<>();
            docentes.forEach((docente, n) -> {
                if (n == maximo) {
                    empatados.add(docente);
                }
            });
            String anterior = anteriores.get(clave);
            String elegido = anterior != null && empatados.contains(anterior) ? anterior : empatados.iterator().next();
            String[] partes = clave.split("\\|", 2);
            resultado.add(new Responsable(partes[0], partes[1], elegido));
        });
        resultado.sort(Comparator.comparing(Responsable::seccionId).thenComparing(Responsable::cursoId));
        return resultado;
    }

    public static String clave(String cursoId, String seccionId) {
        return cursoId + "|" + seccionId;
    }
}
