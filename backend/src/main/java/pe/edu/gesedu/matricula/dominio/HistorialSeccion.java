package pe.edu.gesedu.matricula.dominio;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Desde qué fecha una matrícula pertenece a una sección (RF-05): la última fila con {@code desde <= día} manda. */
@Entity
@Table(name = "historial_seccion", schema = "matricula")
public class HistorialSeccion extends EntidadBase {

    @Column(name = "matricula_id", nullable = false, updatable = false, length = 36)
    private String matriculaId;

    @Column(name = "seccion_id", nullable = false, updatable = false, length = 36)
    private String seccionId;

    @Column(nullable = false, updatable = false)
    private LocalDate desde;

    protected HistorialSeccion() {
    }

    public HistorialSeccion(String matriculaId, String seccionId, LocalDate desde) {
        this.matriculaId = matriculaId;
        this.seccionId = seccionId;
        this.desde = desde;
    }

    public String getMatriculaId() {
        return matriculaId;
    }

    public String getSeccionId() {
        return seccionId;
    }

    public LocalDate getDesde() {
        return desde;
    }
}
