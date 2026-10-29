package pe.edu.gesedu.pagos.dominio;

import java.util.Comparator;
import java.util.List;

/**
 * Convierte el cronograma del año en las obligaciones de un alumno (RF-07, C-11): todas se generan al confirmar la
 * matrícula. La cuota de ingreso solo se cobra a quien ingresa por primera vez (S-18).
 */
public final class GeneradorObligaciones {

    private GeneradorObligaciones() {
    }

    /** Líneas del cronograma que le tocan al alumno, ordenadas por vencimiento. */
    public static List<CronogramaCobro> aplicables(List<CronogramaCobro> cronograma, boolean nuevoIngreso) {
        return cronograma.stream()
                .filter(l -> nuevoIngreso || l.getConcepto() != ConceptoPago.CUOTA_INGRESO)
                .sorted(Comparator.comparing(CronogramaCobro::getFechaVencimiento)
                        .thenComparing(l -> l.getConcepto().ordinal()))
                .toList();
    }

    public static List<ObligacionPago> generar(List<CronogramaCobro> cronograma, boolean nuevoIngreso,
                                               String matriculaId, String anioEscolarId, String alumnoId,
                                               String apoderadoId) {
        return aplicables(cronograma, nuevoIngreso).stream()
                .map(l -> new ObligacionPago(matriculaId, anioEscolarId, alumnoId, apoderadoId, l.getConcepto(),
                        l.getPeriodo(), l.getMonto(), l.getFechaVencimiento()))
                .toList();
    }
}
