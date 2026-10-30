package pe.edu.gesedu.academico.dominio;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Año escolar (RF-03). Se crea con UC-13 y queda PLANIFICADO; el activo y el cerrado vienen sembrados. */
@Entity
@Table(name = "anio_escolar", schema = "academico")
public class AnioEscolar extends EntidadBase {

    @Column(nullable = false, unique = true)
    private int anio;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(nullable = false, length = 12)
    private String estado;

    protected AnioEscolar() {
    }

    public AnioEscolar(int anio, LocalDate fechaInicio, LocalDate fechaFin, String estado) {
        this.anio = anio;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.estado = estado;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public int getAnio() {
        return anio;
    }

    public String getEstado() {
        return estado;
    }
}
