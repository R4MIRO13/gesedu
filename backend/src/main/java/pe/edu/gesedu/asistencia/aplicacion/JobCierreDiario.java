package pe.edu.gesedu.asistencia.aplicacion;

import java.time.Clock;
import java.time.LocalDate;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pe.edu.gesedu.asistencia.aplicacion.CierreDiarioService.ResultadoCierre;

/**
 * Job «Cierre diario de asistencia» (UC-12): se ejecuta cada minuto y actúa solo si ya se alcanzó
 * {@code SchoolDayClosingTime} y la fecha de hoy no fue procesada con éxito. ShedLock hace que, con varias instancias,
 * solo una ejecute cada disparo (DA-08, R-09); el servicio es idempotente, así que un disparo repetido o un reintento
 * tras una caída no duplica nada. Actúa como el usuario «Sistema».
 */
@Component
public class JobCierreDiario {

    public static final String BLOQUEO = "cierre-diario-asistencia";

    private static final Logger log = LoggerFactory.getLogger(JobCierreDiario.class);

    private final CierreDiarioService cierre;
    private final Clock reloj;

    JobCierreDiario(CierreDiarioService cierre, Clock reloj) {
        this.cierre = cierre;
        this.reloj = reloj;
    }

    @Scheduled(cron = "${gesedu.planificador.cierre-diario-cron:0 * * * * *}", zone = "${gesedu.asistencia.time-zone}")
    @SchedulerLock(name = BLOQUEO, lockAtMostFor = "PT5M")
    public void ejecutar() {
        ResultadoCierre r = cierre.procesar(LocalDate.now(reloj));
        switch (r.resultado()) {
            case COMPLETADO -> log.info("Cierre diario {}: {}", r.fecha(), r.detalle());
            case DIA_NO_LECTIVO -> log.info("Cierre diario {}: {}", r.fecha(), r.detalle());
            case FALLIDO -> log.warn("Cierre diario {} FALLIDO (se reintenta en el siguiente disparo): {}", r.fecha(), r.detalle());
            case JORNADA_ABIERTA, YA_PROCESADO -> {
                // Es lo normal el resto del día: nada que informar
                if (log.isTraceEnabled()) {
                    log.trace("Cierre diario {}: {}", r.fecha(), r.resultado());
                }
            }
        }
    }
}
