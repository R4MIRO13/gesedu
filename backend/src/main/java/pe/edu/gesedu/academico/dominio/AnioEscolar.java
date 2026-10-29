package pe.edu.gesedu.academico.dominio;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Año escolar. En la demo solo se lee (lo siembra db/demo); su configuración llega con UC-13. */
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

    public int getAnio() {
        return anio;
    }

    public String getEstado() {
        return estado;
    }
}
