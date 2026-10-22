package pe.edu.gesedu.horarios.api;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import pe.edu.gesedu.horarios.dominio.DiaSemana;

/** Cuerpo de {@code POST /api/v1/anios-escolares/{anio}/bloques}; las horas van como {@code HH:mm}. */
public record CrearBloqueRequest(
        @NotNull DiaSemana diaSemana,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime horaFin) {

    @JsonIgnore
    @AssertTrue(message = "La hora de fin debe ser posterior a la hora de inicio")
    public boolean isHoraFinPosterior() {
        // Si falta alguna hora, @NotNull ya la reporta
        return horaInicio == null || horaFin == null || horaFin.isAfter(horaInicio);
    }
}
