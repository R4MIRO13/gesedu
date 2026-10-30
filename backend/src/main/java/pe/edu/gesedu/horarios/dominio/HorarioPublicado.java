package pe.edu.gesedu.horarios.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Versión publicada del horario de un año (RF-44, RF-45). Es inmutable: lo único que cambia es la marca
 * {@code vigente}, que pasa a falso cuando se publica una versión posterior. La base lo garantiza con triggers y con un
 * índice único parcial: a lo sumo una versión vigente por año.
 */
@Entity
@Table(name = "horario_publicado", schema = "horarios")
public class HorarioPublicado extends EntidadBase {

    @Column(name = "anio_escolar_id", nullable = false, updatable = false, length = 36)
    private String anioEscolarId;

    @Column(name = "numero_version", nullable = false, updatable = false)
    private int numeroVersion;

    @Column(name = "fecha_hora_publicacion", nullable = false, updatable = false)
    private Instant fechaHoraPublicacion;

    @Column(nullable = false)
    private boolean vigente;

    @Column(name = "publicado_por", nullable = false, updatable = false, length = 36)
    private String publicadoPor;

    protected HorarioPublicado() {
    }

    public HorarioPublicado(String anioEscolarId, int numeroVersion, Instant fechaHoraPublicacion, String publicadoPor) {
        this.anioEscolarId = anioEscolarId;
        this.numeroVersion = numeroVersion;
        this.fechaHoraPublicacion = fechaHoraPublicacion;
        this.publicadoPor = publicadoPor;
        this.vigente = true;
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public int getNumeroVersion() {
        return numeroVersion;
    }

    public Instant getFechaHoraPublicacion() {
        return fechaHoraPublicacion;
    }

    public boolean isVigente() {
        return vigente;
    }

    public String getPublicadoPor() {
        return publicadoPor;
    }
}
