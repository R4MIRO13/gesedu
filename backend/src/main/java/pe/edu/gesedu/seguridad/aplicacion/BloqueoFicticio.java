package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.stereotype.Component;

import pe.edu.gesedu.seguridad.infraestructura.PropiedadesSeguridad;

/**
 * Bloqueo simulado para correos que no corresponden a una cuenta con contraseña (ISO/IEC 27002:2022, 8.5; S-84).
 * Sin él, «cuenta bloqueada» solo aparecería para correos registrados y serviría para averiguar cuáles existen.
 * Aplica la misma regla que el bloqueo real (máximo de intentos, misma duración), pero en memoria y sin tocar la
 * base de datos: con varias instancias cada una cuenta por separado, igual que el límite de peticiones por IP.
 */
@Component
class BloqueoFicticio {

    /** Tope de correos distintos que se recuerdan: probar millones de correos no hace crecer la memoria. */
    static final int MAXIMO_CORREOS = 10_000;

    private record Estado(int intentos, Instant bloqueadoHasta) {
    }

    private final Cache<String, Estado> estados;
    private final PropiedadesSeguridad propiedades;
    private final Clock reloj;

    BloqueoFicticio(PropiedadesSeguridad propiedades, Clock reloj) {
        this.propiedades = propiedades;
        this.reloj = reloj;
        this.estados = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(propiedades.bloqueoMinutos()))
                .maximumSize(MAXIMO_CORREOS)
                .build();
    }

    /** Entradas guardadas (aplica antes las expulsiones pendientes); lo usan las pruebas. */
    long tamano() {
        estados.cleanUp();
        return estados.estimatedSize();
    }

    /**
     * Cuenta un intento sobre un correo sin cuenta.
     *
     * @return hasta cuándo está «bloqueado», o vacío si este intento aún se responde como credenciales inválidas
     */
    Optional<Instant> registrarIntento(String correo) {
        String clave = correo.trim().toLowerCase(Locale.ROOT);
        Instant ahora = reloj.instant();
        Estado nuevo = estados.asMap().compute(clave, (k, actual) -> {
            if (actual != null && actual.bloqueadoHasta() != null && actual.bloqueadoHasta().isAfter(ahora)) {
                return actual;
            }
            int intentos = (actual == null || actual.bloqueadoHasta() != null ? 0 : actual.intentos()) + 1;
            return intentos >= propiedades.maxIntentos()
                    ? new Estado(intentos, ahora.plus(Duration.ofMinutes(propiedades.bloqueoMinutos())))
                    : new Estado(intentos, null);
        });
        return Optional.ofNullable(nuevo.bloqueadoHasta());
    }
}
