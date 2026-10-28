package pe.edu.gesedu.transversal.archivos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;

/** Reglas de archivos (S-19) y URL prefirmadas del Gestor de Archivos en disco (S-20, DA-07). */
class ReglasYDiscoTest {

    private static final String SECRETO = "secreto-de-pruebas-para-las-url-de-archivos-0123456789";
    private static final Instant T0 = Instant.parse("2027-03-01T13:00:00Z");

    @TempDir
    Path carpeta;

    private GestorArchivosDisco gestor(Instant instante, String secreto) {
        return new GestorArchivosDisco(
                new PropiedadesArchivos("disco", carpeta.toString(), secreto, null, null, null),
                Clock.fixed(instante, ZoneId.of("America/Lima")));
    }

    private static void assertCodigo(Throwable error, CodigoError esperado) {
        assertThat(error).isInstanceOfSatisfying(ExcepcionNegocio.class, e -> assertThat(e.codigo()).isEqualTo(esperado));
    }

    // --- ReglasArchivo ---

    @Test
    void soloSePermitenPdfJpgYPngDeHasta5MB() {
        ReglasArchivo.validar("application/pdf", 1);
        ReglasArchivo.validar("image/jpeg", ReglasArchivo.TAMANO_MAXIMO_BYTES);
        ReglasArchivo.validar("IMAGE/PNG", 1000);
        assertThatThrownBy(() -> ReglasArchivo.validar("text/html", 100)).satisfies(e -> assertCodigo(e, CodigoError.ARCHIVO_INVALIDO));
        assertThatThrownBy(() -> ReglasArchivo.validar(null, 100)).satisfies(e -> assertCodigo(e, CodigoError.ARCHIVO_INVALIDO));
        assertThatThrownBy(() -> ReglasArchivo.validar("application/pdf", 0)).satisfies(e -> assertCodigo(e, CodigoError.ARCHIVO_INVALIDO));
        assertThatThrownBy(() -> ReglasArchivo.validar("application/pdf", ReglasArchivo.TAMANO_MAXIMO_BYTES + 1))
                .satisfies(e -> assertCodigo(e, CodigoError.ARCHIVO_INVALIDO));
    }

    @Test
    void lasClavesNuevasSonUnicasYSaneadas() {
        String a = ReglasArchivo.nuevaClave("solicitudes", "../Certificado de estudios (1).pdf");
        String b = ReglasArchivo.nuevaClave("solicitudes", "../Certificado de estudios (1).pdf");

        assertThat(a).isNotEqualTo(b).startsWith("solicitudes/").doesNotContain("..").doesNotContain(" ");
        ReglasArchivo.validarClave(a);
    }

    @Test
    void lasClavesQueSalenDelDirectorioSeRechazan() {
        for (String clave : new String[] {"../secreto.pdf", "a/../../b.pdf", "/etc/passwd", "a\\b.pdf", "", "a b.pdf"}) {
            assertThatThrownBy(() -> ReglasArchivo.validarClave(clave))
                    .as(clave).satisfies(e -> assertCodigo(e, CodigoError.ARCHIVO_INVALIDO));
        }
    }

    // --- GestorArchivosDisco ---

    @Test
    void laUrlDeSubidaSeVerificaDentroDeLos5MinutosYVenceDespues() {
        GestorArchivos.UrlPrefirmada url = gestor(T0, SECRETO).urlSubida("docs/a.pdf", "application/pdf", 10);
        String token = url.url().substring(GestorArchivosDisco.RUTA.length());

        assertThat(url.metodo()).isEqualTo("PUT");
        assertThat(url.expiraEn()).isEqualTo(T0.plus(Duration.ofMinutes(5)));
        assertThat(url.cabeceras()).containsEntry("Content-Type", "application/pdf");

        GestorArchivosDisco.Permiso permiso = gestor(T0.plus(Duration.ofMinutes(4)), SECRETO).verificar(token, "PUT");
        assertThat(permiso.clave()).isEqualTo("docs/a.pdf");
        assertThat(permiso.tamanoMaximo()).isEqualTo(10);

        assertThatThrownBy(() -> gestor(T0.plus(Duration.ofMinutes(6)), SECRETO).verificar(token, "PUT"))
                .satisfies(e -> assertCodigo(e, CodigoError.ACCESO_DENEGADO));
    }

    @Test
    void unTokenNoSirveParaOtraOperacionNiConOtraClaveNiAlterado() {
        GestorArchivosDisco emisor = gestor(T0, SECRETO);
        String token = emisor.urlSubida("docs/a.pdf", "application/pdf", 10).url().substring(GestorArchivosDisco.RUTA.length());

        assertThatThrownBy(() -> emisor.verificar(token, "GET")).satisfies(e -> assertCodigo(e, CodigoError.ACCESO_DENEGADO));
        assertThatThrownBy(() -> gestor(T0, "otro-secreto-distinto-de-al-menos-32-caracteres").verificar(token, "PUT"))
                .satisfies(e -> assertCodigo(e, CodigoError.ACCESO_DENEGADO));
        assertThatThrownBy(() -> emisor.verificar(token.substring(0, token.length() - 2) + "xx", "PUT"))
                .satisfies(e -> assertCodigo(e, CodigoError.ACCESO_DENEGADO));
        assertThatThrownBy(() -> emisor.verificar("basura", "PUT")).satisfies(e -> assertCodigo(e, CodigoError.ACCESO_DENEGADO));
    }

    @Test
    void guardaYReconoceElObjetoYNuncaSaleDelDirectorio() throws Exception {
        GestorArchivosDisco gestor = gestor(T0, SECRETO);
        assertThat(gestor.existe("docs/b.pdf")).isFalse();

        gestor.guardar("docs/b.pdf", new ByteArrayInputStream("hola".getBytes(StandardCharsets.UTF_8)));

        assertThat(gestor.existe("docs/b.pdf")).isTrue();
        assertThat(java.nio.file.Files.readString(carpeta.resolve("docs/b.pdf"))).isEqualTo("hola");
        assertThatThrownBy(() -> gestor.existe("../fuera.pdf")).satisfies(e -> assertCodigo(e, CodigoError.ARCHIVO_INVALIDO));
        assertThatThrownBy(() -> gestor.urlSubida("../fuera.pdf", "application/pdf", 5))
                .satisfies(e -> assertCodigo(e, CodigoError.ARCHIVO_INVALIDO));
    }

    @Test
    void sinUnSecretoLargoElGestorNoArranca() {
        assertThatThrownBy(() -> gestor(T0, "corto")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secreto-url");
        assertThatThrownBy(() -> gestor(T0, null)).isInstanceOf(IllegalStateException.class);
    }
}
