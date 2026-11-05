package pe.edu.gesedu.evaluacion.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;
import pe.edu.gesedu.compartido.dominio.NivelLogro;

/**
 * Nota bimestral o anual de un alumno en una competencia (RF-27, RF-28). Hay una sola por periodo: calcular de nuevo la
 * reemplaza, nunca la duplica (índices únicos parciales {@code ux_nota_bimestral} y {@code ux_nota_anual}).
 */
@Entity
@Table(name = "nota_consolidada", schema = "evaluacion")
public class NotaConsolidada extends EntidadBase {

    public static final String BIMESTRAL = "BIMESTRAL";
    public static final String ANUAL = "ANUAL";

    // Referencias a otros módulos: solo ids
    @Column(name = "alumno_id", nullable = false, updatable = false, length = 36)
    private String alumnoId;

    @Column(name = "competencia_id", nullable = false, updatable = false, length = 36)
    private String competenciaId;

    @Column(name = "asignacion_id", nullable = false, updatable = false, length = 36)
    private String asignacionId;

    @Column(name = "anio_escolar_id", nullable = false, updatable = false, length = 36)
    private String anioEscolarId;

    /** Nulo en la nota anual. */
    @Column(name = "bimestre_id", updatable = false, length = 36)
    private String bimestreId;

    @Column(nullable = false, updatable = false, length = 10)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_logro", nullable = false, length = 2)
    private NivelLogro nivelLogro;

    @Column(name = "calculada_en", nullable = false)
    private Instant calculadaEn;

    @Column(name = "libreta_id", length = 36)
    private String libretaId;

    protected NotaConsolidada() {
    }

    private NotaConsolidada(String alumnoId, String competenciaId, String asignacionId, String anioEscolarId,
                            String bimestreId, String tipo, NivelLogro nivelLogro, Instant calculadaEn) {
        this.alumnoId = alumnoId;
        this.competenciaId = competenciaId;
        this.asignacionId = asignacionId;
        this.anioEscolarId = anioEscolarId;
        this.bimestreId = bimestreId;
        this.tipo = tipo;
        this.nivelLogro = nivelLogro;
        this.calculadaEn = calculadaEn;
    }

    public static NotaConsolidada anual(String alumnoId, String competenciaId, String asignacionId, String anioEscolarId,
                                        NivelLogro nivel, Instant ahora) {
        return new NotaConsolidada(alumnoId, competenciaId, asignacionId, anioEscolarId, null, ANUAL, nivel, ahora);
    }

    /**
     * Reemplaza el nivel con un nuevo cálculo; devuelve {@code true} si el nivel cambió. Si el nivel es el mismo no se
     * toca nada (ni la fecha de cálculo): repetir el cálculo no cambia el resultado.
     */
    public boolean reemplazar(NivelLogro nuevo, Instant ahora) {
        if (nivelLogro == nuevo) {
            return false;
        }
        this.nivelLogro = nuevo;
        this.calculadaEn = ahora;
        return true;
    }

    /** Vincula la nota a la libreta del alumno (UC-18); un valor nulo no cambia nada. */
    public void vincularLibreta(String libretaId) {
        if (libretaId != null) {
            this.libretaId = libretaId;
        }
    }

    public static NotaConsolidada bimestral(String alumnoId, String competenciaId, String asignacionId, String anioEscolarId,
                                            String bimestreId, NivelLogro nivel, Instant ahora) {
        return new NotaConsolidada(alumnoId, competenciaId, asignacionId, anioEscolarId, bimestreId, BIMESTRAL, nivel, ahora);
    }

    public String getAlumnoId() {
        return alumnoId;
    }

    public String getCompetenciaId() {
        return competenciaId;
    }

    public String getAsignacionId() {
        return asignacionId;
    }

    public String getAnioEscolarId() {
        return anioEscolarId;
    }

    public String getBimestreId() {
        return bimestreId;
    }

    public String getTipo() {
        return tipo;
    }

    public NivelLogro getNivelLogro() {
        return nivelLogro;
    }

    public Instant getCalculadaEn() {
        return calculadaEn;
    }

    public String getLibretaId() {
        return libretaId;
    }
}
