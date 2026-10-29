package pe.edu.gesedu.pagos.aplicacion;

import java.time.Clock;
import java.time.LocalDate;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pe.edu.gesedu.pagos.aplicacion.VencimientosService.ResultadoVencimientos;

/**
 * Job «Vencimientos y penalidades»: todos los días a las 00:05 (America/Lima). Con varias instancias, ShedLock hace
 * que solo una ejecute cada disparo (DA-08). Si la aplicación estuvo apagada a esa hora, la siguiente ejecución
 * recupera lo pendiente: el proceso mira todas las obligaciones ya vencidas, no solo las de ayer.
 */
@Component
public class JobVencimientos {

    public static final String BLOQUEO = "vencimientos-y-penalidades";

    private static final Logger log = LoggerFactory.getLogger(JobVencimientos.class);

    private final VencimientosService vencimientos;
    private final Clock reloj;

    JobVencimientos(VencimientosService vencimientos, Clock reloj) {
        this.vencimientos = vencimientos;
        this.reloj = reloj;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "${gesedu.asistencia.time-zone}")
    @SchedulerLock(name = BLOQUEO, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void ejecutar() {
        ResultadoVencimientos r = vencimientos.procesar(LocalDate.now(reloj));
        log.info("Vencimientos y penalidades: {} obligaciones vencidas, {} penalidades por S/ {}",
                r.obligacionesVencidas(), r.penalidadesAplicadas(), r.totalPenalidades());
    }
}
