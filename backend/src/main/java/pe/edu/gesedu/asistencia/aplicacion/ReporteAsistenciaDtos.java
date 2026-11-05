package pe.edu.gesedu.asistencia.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** DTO del reporte mensual de asistencia (UC-10, UC-11). Los reportes de alumnos y de docentes nunca se mezclan. */
public final class ReporteAsistenciaDtos {

    private ReporteAsistenciaDtos() {
    }

    public enum Ambito {
        ALUMNOS, DOCENTES
    }

    public enum Formato {
        JSON, XLSX, PDF
    }

    /**
     * Una persona del reporte. {@code porcentaje} es un decimal con 2 decimales en texto, o {@code null} («No disponible»)
     * si no tiene días registrados. {@code noRegistrados} son los días esperados sin registro: no son faltas y no entran en
     * el porcentaje. {@code codigo} y {@code seccion} solo aplican a los alumnos.
     */
    public record FilaReporteDto(String id, String codigo, String nombre, String seccion, int puntual, int tardanza, int falta,
                                 int registrados, int diasEsperados, int noRegistrados, String porcentaje) {
    }

    /**
     * El reporte calculado al momento de pedirlo (no se guarda nada). {@code parcial} indica un periodo en curso;
     * {@code fechaCorte} es el último día considerado (el día actual solo cuenta con la jornada cerrada y no hay fechas
     * futuras). {@code diasLectivos} son los días lectivos considerados, y {@code totalNoRegistrados} avisa de información
     * incompleta (en docentes puede señalar que el cierre diario no corrió).
     */
    public record ReporteAsistenciaDto(Ambito ambito, int anio, int mes, String alcance, OffsetDateTime generadoEn,
                                       LocalDate fechaCorte, boolean parcial, int diasLectivos, int totalNoRegistrados,
                                       List<FilaReporteDto> filas) {
    }

    /**
     * El resumen de asistencia de un alumno en un mes (inicio del alumno, RF-16 bajo demanda): los mismos conteos y el
     * mismo porcentaje que el reporte, calculados al pedirlo. {@code porcentaje} nulo = «No disponible».
     */
    public record ResumenAsistenciaAlumnoDto(String alumnoId, int anio, int mes, int puntual, int tardanza, int falta,
                                             int registrados, int diasEsperados, int noRegistrados, String porcentaje,
                                             boolean parcial, LocalDate fechaCorte) {
    }

    /** Un archivo descargable. */
    public record ArchivoReporte(byte[] contenido, String tipoContenido, String nombre) {
    }
}
