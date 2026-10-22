package pe.edu.gesedu.horarios.aplicacion;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import pe.edu.gesedu.horarios.dominio.DiaSemana;

/** Bloque horario tal como lo ve la API; las horas viajan como {@code HH:mm}. */
public record BloqueHorarioDto(
        String id,
        DiaSemana diaSemana,
        @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
        @JsonFormat(pattern = "HH:mm") LocalTime horaFin) {
}
