package pe.edu.gesedu.evaluacion.aplicacion;

import java.util.List;
import java.util.Map;

import pe.edu.gesedu.compartido.dominio.NivelLogro;
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.CompetenciaCursoDto;
import pe.edu.gesedu.evaluacion.dominio.TipoRegla;

/** DTO de UC-17 (notas finales del curso). */
public final class NotasFinalesDtos {

    private NotasFinalesDtos() {
    }

    /** Las notas de un alumno en una competencia: el logro de cada bimestre (por número) y la nota anual si ya se calculó. */
    public record CeldaNotaDto(Map<Integer, NivelLogro> bimestres, NivelLogro anual) {
    }

    /** Fila de la tabla; {@code competencias} va por id de competencia. */
    public record FilaNotasDto(String alumnoId, String codigo, String apellidos, String nombres,
                               Map<String, CeldaNotaDto> competencias) {
    }

    /** Lo que hizo el cálculo: notas nuevas, notas cuyo nivel cambió, notas que ya estaban igual y celdas sin ningún logro. */
    public record CalculoDto(int creadas, int actualizadas, int sinCambios, int sinNota) {
    }

    /**
     * La tabla de notas finales de una asignación. {@code calculadas} indica si hay alguna nota anual; {@code desactualizada},
     * que lo registrado desde entonces (logros, regla o pesos) daría otras notas: hay que volver a calcular.
     * {@code celdasSinNota} cuenta las celdas alumno × competencia sin ningún logro. {@code calculo} solo viene en la
     * respuesta del cálculo.
     */
    public record NotasFinalesDto(String asignacionId, String curso, String seccion, int anio, TipoRegla regla,
                                  List<Integer> bimestres, List<CompetenciaCursoDto> competencias, List<FilaNotasDto> alumnos,
                                  boolean calculadas, boolean desactualizada, boolean editable, int celdasSinNota,
                                  CalculoDto calculo) {
    }
}
