package pe.edu.gesedu.transversal.archivos;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Gestor de Archivos sobre S3 privado (DA-07): URL prefirmadas de 5 minutos con el tipo y el tamaño firmados,
 * así que S3 rechaza un archivo distinto del declarado. El bucket tiene SSE-S3 y versionado (docs/PLAN.md § 12).
 * Lo crea {@link ConfiguracionArchivos} solo con {@code gesedu.archivos.tipo=s3}.
 */
public class GestorArchivosS3 implements GestorArchivos {

    private final S3Presigner presigner;
    private final S3Client cliente;
    private final String bucket;
    private final Clock reloj;

    public GestorArchivosS3(S3Presigner presigner, S3Client cliente, String bucket, Clock reloj) {
        this.presigner = presigner;
        this.cliente = cliente;
        this.bucket = bucket;
        this.reloj = reloj;
    }

    @Override
    public UrlPrefirmada urlSubida(String clave, String contentType, long tamanoBytes) {
        ReglasArchivo.validarClave(clave);
        ReglasArchivo.validar(contentType, tamanoBytes);
        PutObjectRequest objeto = PutObjectRequest.builder()
                .bucket(bucket).key(clave).contentType(contentType).contentLength(tamanoBytes).build();
        var firmada = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(VIGENCIA_URL).putObjectRequest(objeto).build());
        return new UrlPrefirmada(firmada.url().toString(), "PUT", reloj.instant().plus(VIGENCIA_URL),
                Map.of("Content-Type", contentType));
    }

    @Override
    public UrlPrefirmada urlDescarga(String clave, String contentType, String nombreArchivo) {
        ReglasArchivo.validarClave(clave);
        GetObjectRequest objeto = GetObjectRequest.builder()
                .bucket(bucket).key(clave)
                .responseContentType(contentType)
                .responseContentDisposition("inline; filename=\"" + nombreArchivo.replace("\"", "") + "\"")
                .build();
        var firmada = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(VIGENCIA_URL).getObjectRequest(objeto).build());
        Instant expira = reloj.instant().plus(VIGENCIA_URL);
        return new UrlPrefirmada(firmada.url().toString(), "GET", expira, Map.of());
    }

    @Override
    public boolean existe(String clave) {
        ReglasArchivo.validarClave(clave);
        try {
            cliente.headObject(HeadObjectRequest.builder().bucket(bucket).key(clave).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }
}
