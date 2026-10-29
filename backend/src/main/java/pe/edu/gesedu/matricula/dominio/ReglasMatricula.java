package pe.edu.gesedu.matricula.dominio;

import java.util.ArrayList;
import java.util.List;

import pe.edu.gesedu.compartido.dominio.Dinero;

/**
 * Validaciones automáticas de UC-02 (pasos 3 y 4, FA-1 a FA-3), por tipo de estudiante (C-37): el nuevo ingreso
 * necesita estar admitido; el recurrente no puede tener deudas vencidas de años anteriores. Ambos deben tener la
 * documentación completa y no estar ya matriculados en el año. Función pura: los datos los reúne el servicio.
 */
public final class ReglasMatricula {

    public enum Regla {
        DOCUMENTACION, ADMISION, MATRICULA_ACTIVA, DEUDAS
    }

    /**
     * Resultado de una regla. {@code motivo} es el rechazo que la regla justifica cuando falla (nulo si no hay
     * ninguno, como la documentación incompleta, que se corrige en vez de rechazarse).
     */
    public record Validacion(Regla regla, boolean aprobada, String mensaje, MotivoRechazo motivo) {
    }

    private ReglasMatricula() {
    }

    public static List<Validacion> evaluar(TipoEstudiante tipo, EstadoDocumentacion documentacion,
                                           EstadoAdmision admision, boolean tieneMatriculaActiva, Dinero deuda) {
        List<Validacion> resultado = new ArrayList<>();
        resultado.add(documentacion == EstadoDocumentacion.COMPLETA
                ? new Validacion(Regla.DOCUMENTACION, true, "Documentación completa", null)
                : new Validacion(Regla.DOCUMENTACION, false, "La documentación está incompleta", null));

        if (tipo == TipoEstudiante.NUEVO_INGRESO) {
            resultado.add(switch (admision) {
                case ADMITIDO -> new Validacion(Regla.ADMISION, true, "Admitido", null);
                case NO_ADMITIDO -> new Validacion(Regla.ADMISION, false, "El postulante no fue admitido",
                        MotivoRechazo.NO_ADMITIDO);
                case NO_APLICA -> new Validacion(Regla.ADMISION, false,
                        "Falta registrar el resultado de la admisión", null);
            });
        } else {
            resultado.add(new Validacion(Regla.ADMISION, true, "No aplica (alumno recurrente)", null));
        }

        resultado.add(tieneMatriculaActiva
                ? new Validacion(Regla.MATRICULA_ACTIVA, false, "El alumno ya tiene una matrícula activa en el año",
                        MotivoRechazo.DUPLICIDAD)
                : new Validacion(Regla.MATRICULA_ACTIVA, true, "Sin matrícula activa en el año", null));

        if (tipo == TipoEstudiante.RECURRENTE) {
            boolean debe = deuda.compareTo(Dinero.CERO) > 0;
            resultado.add(debe
                    ? new Validacion(Regla.DEUDAS, false, "Tiene deudas vencidas de años anteriores por S/ " + deuda,
                            MotivoRechazo.DEUDAS)
                    : new Validacion(Regla.DEUDAS, true, "Sin deudas vencidas de años anteriores", null));
        } else {
            resultado.add(new Validacion(Regla.DEUDAS, true, "No aplica (nuevo ingreso)", null));
        }
        return resultado;
    }

    public static boolean todasAprobadas(List<Validacion> validaciones) {
        return validaciones.stream().allMatch(Validacion::aprobada);
    }
}
