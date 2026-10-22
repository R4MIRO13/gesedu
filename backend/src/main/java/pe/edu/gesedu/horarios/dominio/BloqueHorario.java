package pe.edu.gesedu.horarios.dominio;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/**
 * Bloque horario de un año escolar (RF-46). Dos bloques del mismo día no pueden superponerse; la base de
 * datos lo exige además con una restricción EXCLUDE. El recreo es el hueco entre bloques.
 */
@Entity
@Table(name = "bloque_horario", schema = "horarios")
public class BloqueHorario {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(length = 36)
    private String id;

    // Relación entre módulos: solo el id, sin asociación JPA
    @Column(name = "anio_escolar_id", nullable = false, length = 36)
    private String anioEscolarId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DiaSemana diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    protected BloqueHorario() {
    }

    public BloqueHorario(String anioEscolarId, DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFin) {
        if (!horaFin.isAfter(horaInicio)) {
            throw new IllegalArgumentException("La hora de fin debe ser posterior a la de inicio");
        }
        this.anioEscolarId = anioEscolarId;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
    }

    /** Dos bloques se superponen si comparten día y sus intervalos se cruzan (tocarse en el borde no cruza). */
    public boolean seSuperponeCon(BloqueHorario otro) {
        return diaSemana == otro.diaSemana && horaInicio.isBefore(otro.horaFin) && otro.horaInicio.isBefore(horaFin);
    }

    public String getId() {
        return id;
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public DiaSemana getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }
}
