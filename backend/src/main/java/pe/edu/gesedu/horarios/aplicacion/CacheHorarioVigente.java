package pe.edu.gesedu.horarios.aplicacion;

import java.time.Duration;
import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Caché Caffeine del horario vigente de cada año (UC-23, RNF-03): el horario publicado es inmutable y lo consultan todos
 * los alumnos, apoderados y docentes, así que se arma una sola vez por año. La publicación de una versión nueva lo
 * invalida cuando la transacción ya terminó; el vencimiento a los 5 minutos es la red de seguridad para el caso de
 * varias instancias, cada una con su propia caché (DA-11).
 */
@Component
public class CacheHorarioVigente {

    private final Cache<Integer, Object> cache = Caffeine.newBuilder()
            .maximumSize(50)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    /** El valor guardado para el año o, si no hay, el que arma el cargador (que también se guarda). */
    @SuppressWarnings("unchecked")
    <T> T obtener(int anio, Supplier<T> cargador) {
        return (T) cache.get(anio, clave -> cargador.get());
    }

    void invalidar(int anio) {
        cache.invalidate(anio);
    }

    /**
     * Invalida el año cuando la transacción en curso se confirma: antes sería inútil, porque otra petición podría volver
     * a guardar el valor viejo antes del commit. Si se revierte, no se invalida nada (el horario no cambió).
     */
    void invalidarTrasCommit(int anio) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidar(anio);
                }
            });
        } else {
            invalidar(anio);
        }
    }
}
