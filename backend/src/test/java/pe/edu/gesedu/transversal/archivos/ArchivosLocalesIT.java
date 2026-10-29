package pe.edu.gesedu.transversal.archivos;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import pe.edu.gesedu.apoyo.BaseIT;

/** Subida y descarga con URL prefirmadas locales, sin sesión: el token firmado es la autorización (S-20). */
class ArchivosLocalesIT extends BaseIT {

    @Autowired
    private GestorArchivos gestor;

    @Value("${local.server.port}")
    private int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<byte[]> subir(GestorArchivos.UrlPrefirmada url, String contentType, byte[] cuerpo) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + url.url()))
                .header("Content-Type", contentType).PUT(HttpRequest.BodyPublishers.ofByteArray(cuerpo)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> bajar(String ruta) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void subirYDescargarUnArchivoSinSesion() throws Exception {
        String clave = ReglasArchivo.nuevaClave("pruebas", "certificado.pdf");
        byte[] contenido = "%PDF-1.4 contenido de prueba".getBytes();

        assertThat(gestor.existe(clave)).isFalse();
        HttpResponse<byte[]> subida = subir(gestor.urlSubida(clave, "application/pdf", contenido.length),
                "application/pdf", contenido);

        assertThat(subida.statusCode()).isEqualTo(200);
        assertThat(gestor.existe(clave)).isTrue();
        HttpResponse<byte[]> descarga = bajar(gestor.urlDescarga(clave, "application/pdf", "certificado.pdf").url());
        assertThat(descarga.statusCode()).isEqualTo(200);
        assertThat(descarga.body()).isEqualTo(contenido);
        assertThat(descarga.headers().firstValue("Content-Type")).contains("application/pdf");
        assertThat(descarga.headers().firstValue("Content-Disposition").orElse("")).contains("certificado.pdf");
    }

    @Test
    void unTipoDeContenidoDistintoAlFirmadoSeRechaza() throws Exception {
        String clave = ReglasArchivo.nuevaClave("pruebas", "a.pdf");
        HttpResponse<byte[]> r = subir(gestor.urlSubida(clave, "application/pdf", 5), "image/png", "12345".getBytes());

        assertThat(r.statusCode()).isEqualTo(422);
        assertThat(new String(r.body())).contains("ARCHIVO_INVALIDO");
        assertThat(gestor.existe(clave)).isFalse();
    }

    @Test
    void unCuerpoMayorQueElDeclaradoSeRechazaYNoQuedaGuardado() throws Exception {
        String clave = ReglasArchivo.nuevaClave("pruebas", "b.pdf");
        HttpResponse<byte[]> r = subir(gestor.urlSubida(clave, "application/pdf", 5), "application/pdf",
                "mucho mas de cinco bytes".getBytes());

        assertThat(r.statusCode()).isEqualTo(422);
        assertThat(new String(r.body())).contains("ARCHIVO_INVALIDO");
        assertThat(gestor.existe(clave)).isFalse();
    }

    @Test
    void unTokenAlteradoOUsadoParaOtraOperacionResponde403() throws Exception {
        String clave = ReglasArchivo.nuevaClave("pruebas", "c.pdf");
        GestorArchivos.UrlPrefirmada subida = gestor.urlSubida(clave, "application/pdf", 3);

        HttpResponse<byte[]> alterado = subir(new GestorArchivos.UrlPrefirmada(
                subida.url().substring(0, subida.url().length() - 3) + "AAA", "PUT", subida.expiraEn(), subida.cabeceras()),
                "application/pdf", "abc".getBytes());
        assertThat(alterado.statusCode()).isEqualTo(403);

        // El token de subida no sirve para descargar
        assertThat(bajar(subida.url()).statusCode()).isEqualTo(403);
        assertThat(gestor.existe(clave)).isFalse();
    }

    @Test
    void descargarUnObjetoInexistenteResponde404() throws Exception {
        String clave = ReglasArchivo.nuevaClave("pruebas", "no-subido.pdf");

        assertThat(bajar(gestor.urlDescarga(clave, "application/pdf", "x.pdf").url()).statusCode()).isEqualTo(404);
    }
}
