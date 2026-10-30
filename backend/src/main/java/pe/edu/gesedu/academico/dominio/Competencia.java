package pe.edu.gesedu.academico.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Competencia que se evalúa en un curso con la escala AD, A, B o C (RF-24, RF-22). */
@Entity
@Table(name = "competencia", schema = "academico")
public class Competencia extends EntidadBase {

    @Column(name = "curso_id", nullable = false, length = 36)
    private String cursoId;

    @Column(nullable = false, length = 10)
    private String codigo;

    @Column(nullable = false, length = 160)
    private String nombre;

    @Column(name = "nivel_esperado", columnDefinition = "text")
    private String nivelEsperado;

    @Column(nullable = false)
    private int orden;

    protected Competencia() {
    }

    public Competencia(String cursoId, String codigo, String nombre, String nivelEsperado, int orden) {
        this.cursoId = cursoId;
        this.codigo = codigo;
        this.nombre = nombre;
        this.nivelEsperado = nivelEsperado;
        this.orden = orden;
    }

    /** Edita el nombre, el nivel esperado y la posición; el código no cambia (las notas lo referencian). */
    public void actualizar(String nombre, String nivelEsperado, int orden) {
        this.nombre = nombre;
        this.nivelEsperado = nivelEsperado;
        this.orden = orden;
    }

    public String getCursoId() {
        return cursoId;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getNivelEsperado() {
        return nivelEsperado;
    }

    public int getOrden() {
        return orden;
    }
}
