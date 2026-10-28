package pe.edu.gesedu.horarios.aplicacion;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.horarios.dominio.HorarioBorrador;
import pe.edu.gesedu.horarios.infraestructura.HorarioBorradorRepository;

/**
 * Crea el borrador del año en una transacción propia. Si dos administradores lo piden a la vez, el índice único
 * de {@code anio_escolar_id} deja pasar a uno y el otro recibe una violación; esa violación sale de esta
 * transacción (se revierte sola) y el llamador la ignora y lee el borrador que creó el otro.
 */
@Component
class CreadorBorrador {

    private final HorarioBorradorRepository borradores;
    private final Clock reloj;

    CreadorBorrador(HorarioBorradorRepository borradores, Clock reloj) {
        this.borradores = borradores;
        this.reloj = reloj;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void crear(String anioEscolarId) {
        borradores.saveAndFlush(new HorarioBorrador(anioEscolarId, reloj.instant()));
    }
}
