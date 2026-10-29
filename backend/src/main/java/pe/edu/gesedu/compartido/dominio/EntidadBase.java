package pe.edu.gesedu.compartido.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Base de las entidades de negocio: id {@code String} con UUIDv7 (S-21) y las marcas técnicas
 * {@code created_at} y {@code updated_at} (docs/PLAN.md § 4). La concurrencia optimista ({@code version})
 * la declara cada entidad que se edita en paralelo.
 */
@MappedSuperclass
public abstract class EntidadBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(length = 36)
    private String id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Marca la entidad como modificada: obliga al UPDATE y, con él, a que {@code @Version} suba aunque ningún dato de
     * negocio haya cambiado (por ejemplo, cuando se edita un perfil que cuelga de la cuenta).
     */
    public void tocar(Instant ahora) {
        this.updatedAt = ahora;
    }

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
