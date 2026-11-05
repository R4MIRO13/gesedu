package pe.edu.gesedu.evaluacion.aplicacion;

import java.util.List;

import pe.edu.gesedu.compartido.dominio.NivelLogro;
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.CompetenciaDto;
import pe.edu.gesedu.evaluacion.dominio.TipoRegla;

/** DTO de UC-16 (regla de consolidación y pesos por bimestre). */
public final class ReglaConsolidacionDtos {

    private ReglaConsolidacionDtos() {
    }

    /** Ejemplo con los bimestres I a IV: los niveles de ejemplo y lo que la regla da con ellos (lo calcula el servidor). */
    public record EjemploDto(List<NivelLogro> niveles, NivelLogro resultado) {
    }

    /** Una de las tres reglas de RF-26 con su explicación y su ejemplo. */
    public record ReglaDto(TipoRegla tipo, String nombre, String descripcion, boolean porDefecto, EjemploDto ejemplo) {
    }

    /** Peso del bimestre en la nota anual, como texto decimal («1.00»); {@code personalizado} si el docente lo ajustó. */
    public record PesoDto(int bimestre, String peso, boolean personalizado) {
    }

    /**
     * La configuración del curso en una sección. {@code regla} es la vigente (PROMEDIO_PONDERADO si el docente nunca
     * eligió otra, UC-16 FA-2); {@code editable} es falso con el año cerrado; {@code cambios} cuenta lo que modificó el
     * guardado que produjo la respuesta.
     */
    public record ConfiguracionDto(String asignacionId, String curso, String seccion, int anio, TipoRegla regla,
                                   List<ReglaDto> reglas, List<PesoDto> pesos, List<CompetenciaDto> competencias,
                                   boolean editable, int cambios) {
    }

    /** Un peso pedido: número de bimestre y valor decimal en texto. */
    public record PesoSolicitud(Integer bimestre, String peso) {
    }

    /**
     * Lo que se guarda: la regla y, si se envía, el conjunto completo de pesos (los bimestres omitidos vuelven a pesar 1).
     * Sin {@code pesos} no se tocan.
     */
    public record SolicitudConfiguracion(String regla, List<PesoSolicitud> pesos) {
    }
}
