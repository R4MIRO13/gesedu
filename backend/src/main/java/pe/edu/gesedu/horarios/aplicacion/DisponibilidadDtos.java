package pe.edu.gesedu.horarios.aplicacion;

import java.time.OffsetDateTime;
import java.util.List;

import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.CursoOpcionDto;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.PeriodoDto;

/** Registro de la disponibilidad horaria del docente (UC-20, RF-46). */
public final class DisponibilidadDtos {

    private DisponibilidadDtos() {
    }

    /**
     * Resultado de comparar los bloques marcados con la carga horaria del docente (FA-2). {@code suficiente} es
     * falso si marcó menos bloques que su carga; {@code faltan} dice cuántos como mínimo.
     */
    public record ValidacionDisponibilidadDto(int bloquesMarcados, int cargaSemanalReferencia, int faltan,
                                              boolean suficiente, String mensaje) {
    }

    /**
     * La disponibilidad del docente autenticado frente a un año: la matriz de bloques, lo último que confirmó y
     * el estado del periodo de registro. {@code vinculado} es falso si el docente no está habilitado en el año.
     */
    public record DisponibilidadDto(int anio, boolean vinculado, String vinculacionId, String docente,
                                    int cargaSemanalReferencia, List<CursoOpcionDto> cursos, PeriodoDto periodo,
                                    List<BloqueHorarioDto> bloques, List<String> bloqueIds, String observaciones,
                                    OffsetDateTime ultimaConfirmacion, ValidacionDisponibilidadDto validacion) {
    }
}
