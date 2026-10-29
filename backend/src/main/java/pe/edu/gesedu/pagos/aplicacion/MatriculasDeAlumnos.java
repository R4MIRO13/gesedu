package pe.edu.gesedu.pagos.aplicacion;

import java.util.Optional;

/**
 * Puerto hacia matrícula (SPI): el historial financiero muestra la matrícula del estudiante, pero {@code pagos} no
 * puede depender de {@code matricula} (el grafo lo prohíbe). {@code matricula} implementa este puerto.
 */
public interface MatriculasDeAlumnos {

    /** Datos de la matrícula tal como se muestran: «3° A · 2026». */
    record MatriculaDeAlumno(int anio, String grado, String seccion, String estado) {
    }

    /** La matrícula confirmada del año más reciente del alumno, si tiene alguna. */
    Optional<MatriculaDeAlumno> ultimaDe(String alumnoId);
}
