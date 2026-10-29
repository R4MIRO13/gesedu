package pe.edu.gesedu.transversal.archivos;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del Gestor de Archivos ({@code gesedu.archivos.*}).
 *
 * @param tipo       {@code disco} (desarrollo local) o {@code s3} (perfil aws)
 * @param directorio carpeta donde {@code disco} guarda los objetos
 * @param secretoUrl clave HMAC con la que {@code disco} firma sus URL prefirmadas (mínimo 32 caracteres)
 * @param bucket     bucket privado de S3
 * @param region     región de AWS
 * @param endpoint   endpoint alternativo de S3 (LocalStack); vacío en AWS
 */
@Validated
@ConfigurationProperties("gesedu.archivos")
public record PropiedadesArchivos(
        @NotBlank String tipo,
        String directorio,
        String secretoUrl,
        String bucket,
        String region,
        String endpoint) {
}
