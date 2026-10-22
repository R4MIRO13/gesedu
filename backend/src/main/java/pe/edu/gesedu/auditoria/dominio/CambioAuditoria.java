package pe.edu.gesedu.auditoria.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/** Cambio de un campo dentro de un registro de auditoría (valor anterior y nuevo). Solo inserción. */
@Entity
@Table(name = "cambio_auditoria", schema = "auditoria")
public class CambioAuditoria {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(length = 36, updatable = false)
    private String id;

    @Column(name = "registro_auditoria_id", nullable = false, length = 36, updatable = false)
    private String registroAuditoriaId;

    @Column(nullable = false, length = 80, updatable = false)
    private String campo;

    @Column(name = "valor_anterior", columnDefinition = "text", updatable = false)
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "text", updatable = false)
    private String valorNuevo;

    protected CambioAuditoria() {
    }

    public CambioAuditoria(String registroAuditoriaId, String campo, String valorAnterior, String valorNuevo) {
        this.registroAuditoriaId = registroAuditoriaId;
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
    }

    public String getRegistroAuditoriaId() {
        return registroAuditoriaId;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }
}
