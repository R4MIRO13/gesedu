package pe.edu.gesedu.horarios.aplicacion;

import java.time.OffsetDateTime;
import java.util.List;

/** Completitud y publicación del horario (UC-22, RF-43, RF-44). Son {@code record}: las entidades no salen de la capa. */
public final class PublicacionDtos {

    private PublicacionDtos() {
    }

    /** Un curso de la sección que no cuadra con su carga semanal: «Matemática 4/5 h». */
    public record DiferenciaDto(String cursoId, String curso, int asignadas, int requeridas) {
    }

    /**
     * Una sección frente a su carga semanal. {@code estado} es COMPLETO, FALTAN, EXCEDE o FALTAN_Y_EXCEDE;
     * {@code faltan} y {@code sobran} suman las diferencias de cada curso y {@code detalle} las lista.
     */
    public record SeccionCompletitudDto(String seccionId, String seccion, int horasAsignadas, int horasRequeridas,
                                        String estado, int faltan, int sobran, List<DiferenciaDto> detalle) {
    }

    /** Aviso que no bloquea la publicación (C-40): por ejemplo, docentes que no confirmaron su disponibilidad. */
    public record AdvertenciaDto(String tipo, String mensaje, List<String> docentes) {
    }

    /** Una versión publicada del horario. */
    public record PublicacionDto(String id, int numeroVersion, OffsetDateTime fechaHoraPublicacion, boolean vigente,
                                 String publicadoPor, int clases) {
    }

    /**
     * Resultado de validar el borrador (paso 2). {@code versionBorrador} viaja también como {@code ETag}: quien confirma
     * la publicación la devuelve en {@code If-Match} para publicar exactamente lo que revisó. {@code sinCambios} es
     * verdadero si el borrador es igual a la versión vigente.
     */
    public record CompletitudDto(int anio, long versionBorrador, List<SeccionCompletitudDto> secciones, int completas,
                                 int conObservaciones, boolean publicable, List<AdvertenciaDto> advertencias,
                                 PublicacionDto vigente, boolean sinCambios) {
    }
}
