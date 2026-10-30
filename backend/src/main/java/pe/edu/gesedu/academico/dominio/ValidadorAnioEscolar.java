package pe.edu.gesedu.academico.dominio;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reglas del año escolar y de sus cuatro bimestres (UC-13, RF-03, RF-25), sin Spring ni base de datos.
 * <ul>
 *   <li>El año dura entre 10 y 12 meses (FA-1). Es la misma regla que el {@code CHECK} de la base.</li>
 *   <li>Cada bimestre tiene inicio anterior al fin, cae dentro del año y no se cruza con otro (FA-2); el bimestre
 *   {@code n + 1} empieza después de que termina el {@code n}. Los extremos cuentan como parte del bimestre,
 *   igual que en la restricción {@code EXCLUDE} de la base.</li>
 * </ul>
 */
public final class ValidadorAnioEscolar {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] ROMANOS = {"", "I", "II", "III", "IV"};

    private ValidadorAnioEscolar() {
    }

    /** Rango de un bimestre tal como lo ingresó el directivo. */
    public record RangoBimestre(int numero, LocalDate inicio, LocalDate fin) {
    }

    public enum Tipo {
        /** El año escolar no dura entre 10 y 12 meses, o su fin no es posterior a su inicio (FA-1). */
        ANIO_FUERA_DE_DURACION,
        /** El inicio del bimestre no es anterior a su fin (FA-2). */
        BIMESTRE_FECHAS_INVALIDAS,
        /** El bimestre no cabe dentro del año escolar (FA-2). */
        BIMESTRE_FUERA_DEL_ANIO,
        /** Dos bimestres comparten días (FA-2). */
        BIMESTRES_SUPERPUESTOS,
        /** Un bimestre posterior empieza antes que el anterior (FA-2). */
        BIMESTRES_DESORDENADOS
    }

    /**
     * Un problema encontrado. {@code bimestre} es el afectado y {@code otroBimestre} el bimestre con el que choca
     * (solo en cruces y desorden); {@code desde} y {@code hasta} delimitan el tramo en conflicto, si aplica.
     */
    public record Problema(Tipo tipo, Integer bimestre, Integer otroBimestre, LocalDate desde, LocalDate hasta,
                           String mensaje) {

        /** {@code true} si el problema es del año y no de un bimestre. */
        public boolean esDelAnio() {
            return tipo == Tipo.ANIO_FUERA_DE_DURACION;
        }
    }

    /** Duración del año en meses y días completos, tal como se muestra en pantalla («10 meses y 5 días»). */
    public record Duracion(int meses, int dias) {
    }

    public static Duracion duracion(LocalDate inicio, LocalDate fin) {
        Period periodo = Period.between(inicio, fin);
        return new Duracion(periodo.getYears() * 12 + periodo.getMonths(), periodo.getDays());
    }

    /** FA-1: el año escolar debe durar entre 10 y 12 meses. */
    public static List<Problema> validarAnio(LocalDate inicio, LocalDate fin) {
        boolean valido = fin.isAfter(inicio) && !fin.isBefore(inicio.plusMonths(10)) && !fin.isAfter(inicio.plusMonths(12));
        if (valido) {
            return List.of();
        }
        return List.of(new Problema(Tipo.ANIO_FUERA_DE_DURACION, null, null, inicio, fin,
                "El año escolar debe durar entre 10 y 12 meses"));
    }

    /** FA-2: los cuatro bimestres dentro del año y sin cruzarse. */
    public static List<Problema> validarBimestres(LocalDate inicioAnio, LocalDate finAnio,
                                                  List<RangoBimestre> bimestres) {
        List<Problema> problemas = new ArrayList<>();
        List<RangoBimestre> ordenados = bimestres.stream().sorted(Comparator.comparingInt(RangoBimestre::numero)).toList();

        for (RangoBimestre b : ordenados) {
            if (!b.fin().isAfter(b.inicio())) {
                problemas.add(new Problema(Tipo.BIMESTRE_FECHAS_INVALIDAS, b.numero(), null, b.inicio(), b.fin(),
                        "El Bimestre " + romano(b.numero()) + " debe terminar después de la fecha en que empieza"));
            } else if (b.inicio().isBefore(inicioAnio) || b.fin().isAfter(finAnio)) {
                problemas.add(new Problema(Tipo.BIMESTRE_FUERA_DEL_ANIO, b.numero(), null, b.inicio(), b.fin(),
                        "El Bimestre " + romano(b.numero()) + " debe estar dentro del año escolar (del "
                                + FECHA.format(inicioAnio) + " al " + FECHA.format(finAnio) + ")"));
            }
        }

        for (int i = 0; i < ordenados.size(); i++) {
            for (int j = i + 1; j < ordenados.size(); j++) {
                RangoBimestre anterior = ordenados.get(i);
                RangoBimestre posterior = ordenados.get(j);
                LocalDate desde = anterior.inicio().isAfter(posterior.inicio()) ? anterior.inicio() : posterior.inicio();
                LocalDate hasta = anterior.fin().isBefore(posterior.fin()) ? anterior.fin() : posterior.fin();
                if (!desde.isAfter(hasta)) {
                    problemas.add(new Problema(Tipo.BIMESTRES_SUPERPUESTOS, posterior.numero(), anterior.numero(),
                            desde, hasta, "El Bimestre " + romano(posterior.numero()) + " se cruza con el Bimestre "
                            + romano(anterior.numero()) + ". Corrige las fechas. (del " + FECHA.format(desde)
                            + " al " + FECHA.format(hasta) + ")"));
                } else if (posterior.inicio().isBefore(anterior.inicio())) {
                    problemas.add(new Problema(Tipo.BIMESTRES_DESORDENADOS, posterior.numero(), anterior.numero(),
                            posterior.inicio(), posterior.fin(), "El Bimestre " + romano(posterior.numero())
                            + " debe empezar después de que termine el Bimestre " + romano(anterior.numero())));
                }
            }
        }
        return problemas;
    }

    public static String romano(int numero) {
        return numero >= 1 && numero < ROMANOS.length ? ROMANOS[numero] : String.valueOf(numero);
    }
}
