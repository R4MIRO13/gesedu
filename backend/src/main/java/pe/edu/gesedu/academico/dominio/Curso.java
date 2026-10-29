package pe.edu.gesedu.academico.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Curso de un año escolar, igual para todos los grados (RF-23). Sus competencias llegan con UC-14. */
@Entity
@Table(name = "curso", schema = "academico")
public class Curso extends EntidadBase {

    @Column(name = "anio_escolar_id", nullable = false, length = 36)
    private String anioEscolarId;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(name = "horas_lectivas", nullable = false)
    private int horasLectivas;

    @Column(nullable = false)
    private boolean habilitado;

    protected Curso() {
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getSlug() {
        return slug;
    }

    public int getHorasLectivas() {
        return horasLectivas;
    }

    public boolean isHabilitado() {
        return habilitado;
    }
}
