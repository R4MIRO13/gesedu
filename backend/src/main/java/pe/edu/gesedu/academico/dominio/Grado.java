package pe.edu.gesedu.academico.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Grado de secundaria de un año escolar: 1° a 5° (RF-03). */
@Entity
@Table(name = "grado", schema = "academico")
public class Grado extends EntidadBase {

    @Column(name = "anio_escolar_id", nullable = false, length = 36)
    private String anioEscolarId;

    @Column(name = "numero_nivel", nullable = false)
    private int numeroNivel;

    protected Grado() {
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public int getNumeroNivel() {
        return numeroNivel;
    }
}
