package pe.edu.gesedu.horarios.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Periodo en que los docentes registran su disponibilidad horaria (RF-46, UC-20). Está abierto si el administrador lo
 * habilitó y el instante cae dentro de sus fechas; fuera de eso, el docente solo ve su última disponibilidad (FE-1).
 */
@Entity
@Table(name = "periodo_registro_disponibilidad", schema = "horarios")
public class PeriodoRegistroDisponibilidad extends EntidadBase {

    public static final String HABILITADO = "HABILITADO";
    public static final String CERRADO = "CERRADO";

    @Column(name = "anio_escolar_id", nullable = false, unique = true, length = 36)
    private String anioEscolarId;

    @Column(name = "fecha_hora_inicio", nullable = false)
    private Instant fechaHoraInicio;

    @Column(name = "fecha_hora_fin", nullable = false)
    private Instant fechaHoraFin;

    @Column(nullable = false, length = 10)
    private String estado;

    protected PeriodoRegistroDisponibilidad() {
    }

    public PeriodoRegistroDisponibilidad(String anioEscolarId, Instant fechaHoraInicio, Instant fechaHoraFin,
                                         boolean habilitado) {
        this.anioEscolarId = anioEscolarId;
        actualizar(fechaHoraInicio, fechaHoraFin, habilitado);
    }

    public void actualizar(Instant inicio, Instant fin, boolean habilitado) {
        this.fechaHoraInicio = inicio;
        this.fechaHoraFin = fin;
        this.estado = habilitado ? HABILITADO : CERRADO;
    }

    /** Habilitado y dentro de las fechas (los extremos cuentan como abierto). */
    public boolean estaAbierto(Instant ahora) {
        return HABILITADO.equals(estado) && !ahora.isBefore(fechaHoraInicio) && !ahora.isAfter(fechaHoraFin);
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public Instant getFechaHoraInicio() {
        return fechaHoraInicio;
    }

    public Instant getFechaHoraFin() {
        return fechaHoraFin;
    }

    public String getEstado() {
        return estado;
    }
}
