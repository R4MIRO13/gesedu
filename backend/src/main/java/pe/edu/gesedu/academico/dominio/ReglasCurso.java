package pe.edu.gesedu.academico.dominio;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Reglas puras de los cursos y sus competencias (UC-14, RF-23, RF-24). */
public final class ReglasCurso {

    public static final int HORAS_MINIMAS = 1;
    public static final int HORAS_MAXIMAS = 40;

    private ReglasCurso() {
    }

    /** «Ciencia y Tecnología» → {@code ciencia-y-tecnologia}: sin tildes, en minúsculas y con guiones. */
    public static String slug(String nombre) {
        String sinTildes = Normalizer.normalize(nombre.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "curso" : slug;
    }

    /** Código de la siguiente competencia: «C» y el número siguiente al mayor ya usado (C1, C2, … sin reutilizar). */
    public static String siguienteCodigo(List<String> codigosUsados) {
        int mayor = codigosUsados.stream()
                .filter(c -> c.matches("C\\d+"))
                .mapToInt(c -> Integer.parseInt(c.substring(1)))
                .max().orElse(0);
        return "C" + (mayor + 1);
    }

    public static boolean horasValidas(int horas) {
        return horas >= HORAS_MINIMAS && horas <= HORAS_MAXIMAS;
    }
}
