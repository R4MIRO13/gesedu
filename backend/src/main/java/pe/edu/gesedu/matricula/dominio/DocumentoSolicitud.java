package pe.edu.gesedu.matricula.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Documento que acompaña la solicitud; el archivo vive en el Gestor de Archivos bajo {@code claveObjeto}. */
@Entity
@Table(name = "documento_solicitud", schema = "matricula")
public class DocumentoSolicitud extends EntidadBase {

    @Column(name = "solicitud_id", nullable = false, length = 36)
    private String solicitudId;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(name = "clave_objeto", nullable = false)
    private String claveObjeto;

    @Column(name = "nombre_archivo", nullable = false)
    private String nombreArchivo;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "tamano_bytes", nullable = false)
    private long tamanoBytes;

    protected DocumentoSolicitud() {
    }

    public String getSolicitudId() {
        return solicitudId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getClaveObjeto() {
        return claveObjeto;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public String getContentType() {
        return contentType;
    }

    public long getTamanoBytes() {
        return tamanoBytes;
    }
}
