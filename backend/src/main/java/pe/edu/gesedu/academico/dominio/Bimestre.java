package pe.edu.gesedu.academico.dominio;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Bimestre de un año escolar (RF-25): cuatro por año, sin superponerse. */
@Entity
@Table(name = "bimestre", schema = "academico")
public class Bimestre extends EntidadBase {

    @Column(name = "anio_escolar_id", nullable = false, length = 36)
    private String anioEscolarId;

    @Column(nullable = false)
    private int numero;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    protected Bimestre() {
    }

    public Bimestre(String anioEscolarId, int numero, LocalDate fechaInicio, LocalDate fechaFin) {
        this.anioEscolarId = anioEscolarId;
        this.numero = numero;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public int getNumero() {
        return numero;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }
}
