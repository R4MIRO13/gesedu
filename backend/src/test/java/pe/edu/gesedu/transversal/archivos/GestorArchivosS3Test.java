package pe.edu.gesedu.transversal.archivos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import pe.edu.gesedu.compartido.error.ExcepcionNegocio;

/** URL prefirmadas de S3 de 5 minutos con el tipo y el tamaño firmados (DA-07). La firma se calcula sin red. */
class GestorArchivosS3Test {

    private final S3Presigner firmador = S3Presigner.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIAPRUEBA", "secreto")))
            .build();
    private final S3Client cliente = mock(S3Client.class);
    private final GestorArchivosS3 gestor = new GestorArchivosS3(firmador, cliente, "gesedu-documentos",
            Clock.fixed(Instant.parse("2027-03-01T13:00:00Z"), ZoneId.of("America/Lima")));

    @Test
    void laUrlDeSubidaDuraCincoMinutosYFirmaElTipoYElTamano() {
        GestorArchivos.UrlPrefirmada url = gestor.urlSubida("solicitudes/abc-certificado.pdf", "application/pdf", 2048);

        assertThat(url.metodo()).isEqualTo("PUT");
        assertThat(url.url()).contains("gesedu-documentos").contains("solicitudes/abc-certificado.pdf")
                .contains("X-Amz-Expires=300").contains("X-Amz-Signature=");
        assertThat(url.url().toLowerCase()).contains("content-length").contains("content-type");
        assertThat(url.cabeceras()).containsEntry("Content-Type", "application/pdf");
        assertThat(url.expiraEn()).isEqualTo(Instant.parse("2027-03-01T13:05:00Z"));
    }

    @Test
    void laUrlDeDescargaIndicaTipoYNombre() {
        GestorArchivos.UrlPrefirmada url = gestor.urlDescarga("comprobantes/x.png", "image/png", "voucher.png");

        assertThat(url.metodo()).isEqualTo("GET");
        assertThat(url.url()).contains("X-Amz-Expires=300").contains("response-content-type=image%2Fpng")
                .contains("response-content-disposition");
    }

    @Test
    void noFirmaTiposNiTamanosNiClavesInvalidas() {
        assertThatThrownBy(() -> gestor.urlSubida("a.exe", "application/x-msdownload", 10)).isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> gestor.urlSubida("a.pdf", "application/pdf", 6L * 1024 * 1024)).isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> gestor.urlSubida("../a.pdf", "application/pdf", 10)).isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void existeConsultaElObjetoYTratarComoInexistenteUn404() {
        when(cliente.headObject(any(HeadObjectRequest.class))).thenReturn(null);
        assertThat(gestor.existe("a/b.pdf")).isTrue();

        when(cliente.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());
        assertThat(gestor.existe("a/b.pdf")).isFalse();

        when(cliente.headObject(any(HeadObjectRequest.class)))
                .thenThrow((S3Exception) S3Exception.builder().statusCode(404).build());
        assertThat(gestor.existe("a/b.pdf")).isFalse();

        when(cliente.headObject(any(HeadObjectRequest.class)))
                .thenThrow((S3Exception) S3Exception.builder().statusCode(403).build());
        assertThatThrownBy(() -> gestor.existe("a/b.pdf")).isInstanceOf(S3Exception.class);
    }
}
