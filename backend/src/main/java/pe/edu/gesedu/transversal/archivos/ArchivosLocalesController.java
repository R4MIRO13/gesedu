package pe.edu.gesedu.transversal.archivos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;

/**
 * Imita las URL prefirmadas de S3 en desarrollo local (S-20). Solo existe con {@code gesedu.archivos.tipo=disco}.
 * No exige sesión: el token firmado por el servidor es la autorización, igual que en S3.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/archivos-locales")
@ConditionalOnProperty(name = "gesedu.archivos.tipo", havingValue = "disco")
class ArchivosLocalesController {

    private final GestorArchivosDisco gestor;

    ArchivosLocalesController(GestorArchivosDisco gestor) {
        this.gestor = gestor;
    }

    @PutMapping("/{token}")
    @ResponseStatus(HttpStatus.OK)
    void subir(@PathVariable String token, HttpServletRequest peticion) throws IOException {
        GestorArchivosDisco.Permiso permiso = gestor.verificar(token, "PUT");
        String tipo = peticion.getContentType();
        if (tipo == null || !tipo.equalsIgnoreCase(permiso.contentType())) {
            throw new ExcepcionNegocio(CodigoError.ARCHIVO_INVALIDO, "El tipo de contenido no coincide con el solicitado");
        }
        try (InputStream limitado = new LimiteDeBytes(peticion.getInputStream(), permiso.tamanoMaximo())) {
            gestor.guardar(permiso.clave(), limitado);
        } catch (LimiteDeBytes.ExcedidoException e) {
            throw new ExcepcionNegocio(CodigoError.ARCHIVO_INVALIDO, "El archivo supera el tamaño declarado");
        }
    }

    @GetMapping("/{token}")
    ResponseEntity<FileSystemResource> descargar(@PathVariable String token) {
        GestorArchivosDisco.Permiso permiso = gestor.verificar(token, "GET");
        Path archivo = gestor.ruta(permiso.clave());
        if (!Files.isRegularFile(archivo)) {
            throw new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(permiso.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(permiso.nombreArchivo(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(archivo));
    }

    /** Corta la lectura si el cuerpo supera el tamaño que firmó la URL. */
    private static final class LimiteDeBytes extends InputStream {

        static final class ExcedidoException extends IOException {
            ExcedidoException() {
                super("Se superó el tamaño máximo");
            }
        }

        private final InputStream origen;
        private final long maximo;
        private long leidos;

        LimiteDeBytes(InputStream origen, long maximo) {
            this.origen = origen;
            this.maximo = maximo;
        }

        @Override
        public int read() throws IOException {
            int b = origen.read();
            if (b >= 0 && ++leidos > maximo) {
                throw new ExcedidoException();
            }
            return b;
        }

        @Override
        public int read(byte[] destino, int desde, int largo) throws IOException {
            int n = origen.read(destino, desde, largo);
            if (n > 0 && (leidos += n) > maximo) {
                throw new ExcedidoException();
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            origen.close();
        }
    }
}
