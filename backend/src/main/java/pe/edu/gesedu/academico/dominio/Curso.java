package pe.edu.gesedu.academico.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Curso de un año escolar, igual para todos los grados (RF-23). Sus competencias son entidades aparte (UC-14). */
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

    public Curso(String anioEscolarId, String nombre, String slug, int horasLectivas) {
        this.anioEscolarId = anioEscolarId;
        this.nombre = nombre;
        this.slug = slug;
        this.horasLectivas = horasLectivas;
        this.habilitado = true;
    }

    /** Edita el nombre (y su slug) y las horas lectivas semanales de referencia (RF-23). */
    public void actualizar(String nombre, String slug, int horasLectivas) {
        this.nombre = nombre;
        this.slug = slug;
        this.horasLectivas = horasLectivas;
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
