package pe.edu.gesedu.horarios.dominio;

import java.util.ArrayList;
import java.util.List;

/**
 * Completitud del horario de una sección (RF-43, UC-22): compara, curso por curso, los bloques asignados con la carga
 * semanal que el grado exige. Una sección está completa solo si todos sus cursos tienen exactamente los bloques
 * requeridos: faltar en un curso no se compensa con sobrar en otro. Sin Spring ni base de datos.
 */
public final class ValidadorCompletitud {

    private ValidadorCompletitud() {
    }

    /** Bloques asignados y requeridos de un curso en una sección. */
    public record CursoEnSeccion(String cursoId, String curso, int asignadas, int requeridas) {

        public int faltan() {
            return Math.max(0, requeridas - asignadas);
        }

        public int sobran() {
            return Math.max(0, asignadas - requeridas);
        }

        public boolean estaCompleto() {
            return asignadas == requeridas;
        }
    }

    public enum Estado {
        COMPLETO, FALTAN, EXCEDE, FALTAN_Y_EXCEDE
    }

    /**
     * Resultado de una sección. {@code asignadas} y {@code requeridas} suman todos sus cursos; {@code faltan} y
     * {@code sobran} suman solo las diferencias de cada curso; {@code diferencias} lista los cursos que no cuadran.
     */
    public record Resultado(String seccionId, String seccion, int asignadas, int requeridas, int faltan, int sobran,
                            Estado estado, List<CursoEnSeccion> diferencias) {

        public boolean estaCompleta() {
            return estado == Estado.COMPLETO;
        }
    }

    public static Resultado evaluar(String seccionId, String seccion, List<CursoEnSeccion> cursos) {
        int asignadas = cursos.stream().mapToInt(CursoEnSeccion::asignadas).sum();
        int requeridas = cursos.stream().mapToInt(CursoEnSeccion::requeridas).sum();
        int faltan = cursos.stream().mapToInt(CursoEnSeccion::faltan).sum();
        int sobran = cursos.stream().mapToInt(CursoEnSeccion::sobran).sum();
        List<CursoEnSeccion> diferencias = new ArrayList<>(cursos.stream().filter(c -> !c.estaCompleto()).toList());
        Estado estado = faltan > 0 && sobran > 0 ? Estado.FALTAN_Y_EXCEDE
                : faltan > 0 ? Estado.FALTAN
                : sobran > 0 ? Estado.EXCEDE
                : Estado.COMPLETO;
        return new Resultado(seccionId, seccion, asignadas, requeridas, faltan, sobran, estado, List.copyOf(diferencias));
    }

    /** Se puede publicar si hay al menos una sección y todas están completas. */
    public static boolean publicable(List<Resultado> secciones) {
        return !secciones.isEmpty() && secciones.stream().allMatch(Resultado::estaCompleta);
    }
}
