package pe.edu.gesedu.transversal.archivos;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Gestor de Archivos (DA-07): los documentos (certificados de estudios, comprobantes de pago) no pasan por la
 * API; el navegador sube y descarga con URL prefirmadas de 5 minutos. En producción las atiende S3 privado
 * ({@code GestorArchivosS3}); en desarrollo local, el propio backend ({@code GestorArchivosDisco}, S-20).
 */
public interface GestorArchivos {

    /** Vigencia de las URL prefirmadas. */
    Duration VIGENCIA_URL = Duration.ofMinutes(5);

    /**
     * URL para que el navegador suba un objeto con {@code PUT}. Valida tipo y tamaño (S-19) y el objeto
     * resultante queda con esa clave.
     */
    UrlPrefirmada urlSubida(String clave, String contentType, long tamanoBytes);

    /** URL para descargar un objeto con {@code GET}. */
    UrlPrefirmada urlDescarga(String clave, String contentType, String nombreArchivo);

    /** Si el objeto existe (por ejemplo, para comprobar que se subió antes de registrar la solicitud). */
    boolean existe(String clave);

    /**
     * @param url       URL absoluta (S3) o relativa al mismo origen (disco)
     * @param metodo    {@code PUT} o {@code GET}
     * @param expiraEn  instante de vencimiento
     * @param cabeceras cabeceras que el navegador debe enviar tal cual (por ejemplo, {@code Content-Type})
     */
    record UrlPrefirmada(String url, String metodo, Instant expiraEn, Map<String, String> cabeceras) {
    }
}
