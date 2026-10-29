package pe.edu.gesedu.transversal.archivos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;

/**
 * Gestor de Archivos para desarrollo local (S-20): guarda los objetos en una carpeta e imita las URL
 * prefirmadas con un token HMAC de 5 minutos en el mismo origen ({@code /api/v1/archivos-locales/{token}}).
 */
@Component
@ConditionalOnProperty(name = "gesedu.archivos.tipo", havingValue = "disco")
public class GestorArchivosDisco implements GestorArchivos {

    static final String RUTA = "/api/v1/archivos-locales/";

    /** Lo que firma un token: qué operación, sobre qué objeto y con qué límites. */
    record Permiso(String metodo, String clave, Instant expiraEn, String contentType, long tamanoMaximo,
                   String nombreArchivo) {
    }

    private final Path raiz;
    private final byte[] secreto;
    private final Clock reloj;

    GestorArchivosDisco(PropiedadesArchivos propiedades, Clock reloj) {
        if (propiedades.secretoUrl() == null || propiedades.secretoUrl().length() < 32) {
            throw new IllegalStateException(
                    "gesedu.archivos.secreto-url debe tener al menos 32 caracteres (GESEDU_ARCHIVOS_SECRETO)");
        }
        this.raiz = Path.of(propiedades.directorio()).toAbsolutePath().normalize();
        this.secreto = propiedades.secretoUrl().getBytes(StandardCharsets.UTF_8);
        this.reloj = reloj;
    }

    @Override
    public UrlPrefirmada urlSubida(String clave, String contentType, long tamanoBytes) {
        ReglasArchivo.validarClave(clave);
        ReglasArchivo.validar(contentType, tamanoBytes);
        Instant expira = reloj.instant().plus(VIGENCIA_URL);
        String token = firmar(new Permiso("PUT", clave, expira, contentType, tamanoBytes, ""));
        return new UrlPrefirmada(RUTA + token, "PUT", expira, Map.of("Content-Type", contentType));
    }

    @Override
    public UrlPrefirmada urlDescarga(String clave, String contentType, String nombreArchivo) {
        ReglasArchivo.validarClave(clave);
        Instant expira = reloj.instant().plus(VIGENCIA_URL);
        String token = firmar(new Permiso("GET", clave, expira, contentType, 0, nombreArchivo));
        return new UrlPrefirmada(RUTA + token, "GET", expira, Map.of());
    }

    @Override
    public boolean existe(String clave) {
        ReglasArchivo.validarClave(clave);
        return Files.isRegularFile(resolver(clave));
    }

    /** Verifica firma y vigencia de un token y que sea para la operación indicada. */
    Permiso verificar(String token, String metodoEsperado) {
        int punto = token.indexOf('.');
        if (punto < 1) {
            throw denegado();
        }
        byte[] carga;
        byte[] firma;
        try {
            carga = Base64.getUrlDecoder().decode(token.substring(0, punto));
            firma = Base64.getUrlDecoder().decode(token.substring(punto + 1));
        } catch (IllegalArgumentException e) {
            throw denegado();
        }
        if (!MessageDigest.isEqual(hmac(carga), firma)) {
            throw denegado();
        }
        String[] partes = new String(carga, StandardCharsets.UTF_8).split("\\|", -1);
        if (partes.length != 6) {
            throw denegado();
        }
        Permiso permiso;
        try {
            permiso = new Permiso(partes[0], partes[1], Instant.ofEpochSecond(Long.parseLong(partes[2])), partes[3],
                    Long.parseLong(partes[4]), partes[5]);
        } catch (NumberFormatException e) {
            throw denegado();
        }
        if (!permiso.metodo().equals(metodoEsperado) || !permiso.expiraEn().isAfter(reloj.instant())) {
            throw denegado();
        }
        return permiso;
    }

    /** Guarda el contenido de forma atómica (archivo temporal y renombrado). */
    void guardar(String clave, InputStream contenido) throws IOException {
        Path destino = resolver(clave);
        Files.createDirectories(destino.getParent());
        Path temporal = Files.createTempFile(destino.getParent(), "subida-", ".tmp");
        try {
            Files.copy(contenido, temporal, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    Path ruta(String clave) {
        return resolver(clave);
    }

    private Path resolver(String clave) {
        Path destino = raiz.resolve(clave).normalize();
        if (!destino.startsWith(raiz)) {
            throw new ExcepcionNegocio(CodigoError.ARCHIVO_INVALIDO, "La clave del archivo no es válida");
        }
        return destino;
    }

    private String firmar(Permiso p) {
        String carga = String.join("|", p.metodo(), p.clave(), String.valueOf(p.expiraEn().getEpochSecond()),
                p.contentType(), String.valueOf(p.tamanoMaximo()), p.nombreArchivo());
        byte[] bytes = carga.getBytes(StandardCharsets.UTF_8);
        Base64.Encoder codificador = Base64.getUrlEncoder().withoutPadding();
        return codificador.encodeToString(bytes) + "." + codificador.encodeToString(hmac(bytes));
    }

    private byte[] hmac(byte[] datos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            return mac.doFinal(datos);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no está disponible", e);
        }
    }

    private static ExcepcionNegocio denegado() {
        return new ExcepcionNegocio(CodigoError.ACCESO_DENEGADO, "El enlace del archivo no es válido o venció");
    }
}
