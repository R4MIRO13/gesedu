package pe.edu.gesedu.compartido.web;

import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;

/**
 * Concurrencia optimista por HTTP (docs/PLAN.md § 5.1): los recursos con {@code @Version} devuelven
 * {@code ETag: "<version>"} y las modificaciones exigen {@code If-Match}.
 */
public final class Etags {

    private Etags() {
    }

    public static String de(long version) {
        return "\"" + version + "\"";
    }

    /** Lee la versión de la cabecera If-Match; sin cabecera o ilegible → 400 VERSION_REQUERIDA. */
    public static long leerVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ExcepcionNegocio(CodigoError.VERSION_REQUERIDA);
        }
        String limpio = ifMatch.trim();
        if (limpio.startsWith("W/")) {
            limpio = limpio.substring(2);
        }
        limpio = limpio.replace("\"", "");
        try {
            return Long.parseLong(limpio);
        } catch (NumberFormatException e) {
            throw new ExcepcionNegocio(CodigoError.VERSION_REQUERIDA);
        }
    }

    /** Versión desactualizada → 409 CONFLICTO_CONCURRENCIA. */
    public static void verificar(long versionActual, String ifMatch) {
        if (leerVersion(ifMatch) != versionActual) {
            throw new ExcepcionNegocio(CodigoError.CONFLICTO_CONCURRENCIA);
        }
    }
}
