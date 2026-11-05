package pe.edu.gesedu.asistencia.dominio;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/**
 * Constancia de que el cierre diario procesó una fecha (UC-12; S-30): evita repetir el trabajo y deja el motivo de un
 * fallo (FA-4) para atenderlo y reintentar. Es una sola fila por fecha ({@code UNIQUE (fecha)}). Se escribe con
 * {@code INSERT … ON CONFLICT}; la entidad sirve para leerla.
 */
@Entity
@Table(name = "cierre_diario", schema = "asistencia")
public class CierreDiario extends EntidadBase {

    public static final String EXITOSO = "EXITOSO";
    public static final String FALLIDO = "FALLIDO";

    @Column(nullable = false, updatable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 10)
    private String estado;

    @Column
    private String detalle;

    @Column(name = "ejecutado_en", nullable = false)
    private Instant ejecutadoEn;

    protected CierreDiario() {
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getEstado() {
        return estado;
    }

    public String getDetalle() {
        return detalle;
    }

    public Instant getEjecutadoEn() {
        return ejecutadoEn;
    }
}
