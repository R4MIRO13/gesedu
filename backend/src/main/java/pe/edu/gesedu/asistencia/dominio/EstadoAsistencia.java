package pe.edu.gesedu.asistencia.dominio;

/**
 * Estado de una asistencia diaria (valores de la columna {@code estado}; ERS: PUNCTUAL, LATE, ABSENT, C-09).
 * La tardanza cuenta como presencia en los reportes (UC-11); la falta no.
 */
public enum EstadoAsistencia {
    PUNTUAL, TARDANZA, FALTA
}
