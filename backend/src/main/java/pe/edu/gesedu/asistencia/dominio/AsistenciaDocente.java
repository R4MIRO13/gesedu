package pe.edu.gesedu.asistencia.dominio;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Asistencia diaria de un docente (UC-07, UC-12; RF-20): como máximo una por docente y fecha, garantizado por la base
 * ({@code ux_asistencia_docente}) también ante solicitudes simultáneas. {@code horaRegistro} es el instante de la
 * marcación según el reloj del servidor; es nulo solo en la FALTA que crea el cierre diario. No se copia la hora del
 * horario ni se guarda el origen del registro.
 * <p>
 * Los registros nuevos se insertan con {@code INSERT … ON CONFLICT DO NOTHING} (ver el repositorio); la entidad sirve
 * para leerlos.
 */
@Entity
@Table(name = "asistencia_docente", schema = "asistencia")
public class AsistenciaDocente extends EntidadBase {

    // Relación entre módulos (seguridad): solo el id
    @Column(name = "docente_id", nullable = false, updatable = false, length = 36)
    private String docenteId;

    @Column(nullable = false, updatable = false)
    private LocalDate fecha;

    @Column(name = "hora_registro", updatable = false)
    private Instant horaRegistro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private EstadoAsistencia estado;

    protected AsistenciaDocente() {
    }

    public String getDocenteId() {
        return docenteId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public Instant getHoraRegistro() {
        return horaRegistro;
    }

    public EstadoAsistencia getEstado() {
        return estado;
    }
}
