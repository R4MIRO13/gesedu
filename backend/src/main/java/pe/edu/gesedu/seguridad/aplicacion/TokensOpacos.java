package pe.edu.gesedu.seguridad.aplicacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos (refresh y recuperación de contraseña): 32 bytes aleatorios en base64url; en la base de datos
 * solo se guarda su hash SHA-256 (DA-04, S-01).
 */
final class TokensOpacos {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private TokensOpacos() {
    }

    static String generar() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String valor) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
