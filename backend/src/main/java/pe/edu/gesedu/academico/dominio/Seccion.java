package pe.edu.gesedu.academico.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Sección de un grado con su capacidad y sus vacantes (RF-04, RNF-09). Las vacantes se descuentan con un
 * {@code UPDATE} condicional, nunca desde la entidad.
 */
@Entity
@Table(name = "seccion", schema = "academico")
public class Seccion extends EntidadBase {

    // Relaciones dentro del módulo se guardan como ids: simple y sin cargas perezosas
    @Column(name = "grado_id", nullable = false, length = 36)
    private String gradoId;

    @Column(name = "anio_escolar_id", nullable = false, length = 36)
    private String anioEscolarId;

    @Column(nullable = false, length = 2)
    private String letra;

    @Column(nullable = false)
    private int capacidad;

    @Column(name = "vacantes_disponibles", nullable = false)
    private int vacantesDisponibles;

    // Entre módulos solo el id (el docente es de seguridad)
    @Column(name = "tutor_docente_id", length = 36)
    private String tutorDocenteId;

    @Version
    private long version;

    protected Seccion() {
    }

    public String getGradoId() {
        return gradoId;
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public String getLetra() {
        return letra;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public int getVacantesDisponibles() {
        return vacantesDisponibles;
    }

    public String getTutorDocenteId() {
        return tutorDocenteId;
    }
}
