package pe.edu.gesedu.apoyo;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Reloj de las pruebas: marca la hora que la prueba decide (RNF de tiempo: toda hora de negocio sale del {@link Clock}).
 * Se instala con {@link RelojPruebaConfig}; la zona es la institucional, así que las horas locales son las de Lima.
 */
public final class RelojPrueba extends Clock {

    private final ZoneId zona;
    private volatile Instant instante;

    public RelojPrueba(ZoneId zona, Instant inicial) {
        this.zona = zona;
        this.instante = inicial;
    }

    /** Fija la hora local (en la zona institucional) del día indicado. */
    public void fijar(LocalDate fecha, String hora) {
        this.instante = fecha.atTime(LocalTime.parse(hora)).atZone(zona).toInstant();
    }

    /** Fija un instante absoluto (para probar que la zona horaria es la institucional y no UTC). */
    public void fijar(Instant nuevo) {
        this.instante = nuevo;
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId otraZona) {
        return new RelojPrueba(otraZona, instante);
    }

    @Override
    public Instant instant() {
        return instante;
    }
}
