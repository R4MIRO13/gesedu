package pe.edu.gesedu.seguridad.dominio;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Perfil de alumno de una cuenta, con su apoderado responsable (C-22). */
@Entity
@Table(name = "alumno", schema = "seguridad")
public class Alumno extends EntidadBase {

    @Column(name = "usuario_id", nullable = false, unique = true, length = 36)
    private String usuarioId;

    @Column(name = "codigo_alumno", nullable = false, unique = true, length = 20)
    private String codigoAlumno;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(name = "apoderado_id", nullable = false, length = 36)
    private String apoderadoId;

    @Column(name = "parentesco_apoderado", nullable = false, length = 30)
    private String parentescoApoderado;

    protected Alumno() {
    }

    public Alumno(String usuarioId, String codigoAlumno, LocalDate fechaNacimiento, String apoderadoId,
                  String parentescoApoderado) {
        this.usuarioId = usuarioId;
        this.codigoAlumno = codigoAlumno;
        this.fechaNacimiento = fechaNacimiento;
        this.apoderadoId = apoderadoId;
        this.parentescoApoderado = parentescoApoderado;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public String getCodigoAlumno() {
        return codigoAlumno;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public String getApoderadoId() {
        return apoderadoId;
    }

    public String getParentescoApoderado() {
        return parentescoApoderado;
    }
}
