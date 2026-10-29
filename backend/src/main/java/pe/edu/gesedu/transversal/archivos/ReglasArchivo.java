package pe.edu.gesedu.transversal.archivos;

import java.util.Set;
import java.util.UUID;

import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;

/** Reglas de los archivos subidos (S-19): PDF, JPG o PNG de hasta 5 MB; claves de objeto seguras. */
public final class ReglasArchivo {

    public static final long TAMANO_MAXIMO_BYTES = 5L * 1024 * 1024;
    public static final Set<String> TIPOS_PERMITIDOS = Set.of("application/pdf", "image/jpeg", "image/png");

    private ReglasArchivo() {
    }

    /** Lanza {@code ARCHIVO_INVALIDO} (422) si el tipo o el tamaño no están permitidos. */
    public static void validar(String contentType, long tamanoBytes) {
        if (contentType == null || !TIPOS_PERMITIDOS.contains(contentType.toLowerCase())) {
            throw new ExcepcionNegocio(CodigoError.ARCHIVO_INVALIDO, "Solo se permiten archivos PDF, JPG o PNG");
        }
        if (tamanoBytes < 1 || tamanoBytes > TAMANO_MAXIMO_BYTES) {
            throw new ExcepcionNegocio(CodigoError.ARCHIVO_INVALIDO, "El archivo debe pesar entre 1 byte y 5 MB");
        }
    }

    /** Clave única {@code prefijo/uuid-nombre} con el nombre saneado: nada de rutas ni caracteres raros. */
    public static String nuevaClave(String prefijo, String nombreOriginal) {
        String nombre = nombreOriginal == null ? "archivo"
                : nombreOriginal.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("\\.{2,}", ".");
        if (nombre.length() > 100) {
            nombre = nombre.substring(nombre.length() - 100);
        }
        return prefijo + "/" + UUID.randomUUID() + "-" + nombre;
    }

    /** Rechaza claves que intenten salir del directorio o del prefijo (por ejemplo {@code ../}). */
    static void validarClave(String clave) {
        if (clave == null || clave.isBlank() || clave.startsWith("/") || clave.contains("..")
                || clave.contains("\\") || !clave.matches("[A-Za-z0-9._/-]+")) {
            throw new ExcepcionNegocio(CodigoError.ARCHIVO_INVALIDO, "La clave del archivo no es válida");
        }
    }
}
