package pe.edu.gesedu.academico.aplicacion;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.infraestructura.AnioEscolarRepository;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;

/** Consulta del año escolar para los demás módulos (horarios la usa para sus bloques). */
@Service
public class AnioEscolarService {

    /** Datos públicos de un año escolar. */
    public record AnioEscolarResumen(String id, int anio, String estado) {
    }

    private final AnioEscolarRepository anios;

    AnioEscolarService(AnioEscolarRepository anios) {
        this.anios = anios;
    }

    @Transactional(readOnly = true)
    public AnioEscolarResumen obtenerPorAnio(int anio) {
        return anios.findByAnio(anio)
                .map(a -> new AnioEscolarResumen(a.getId(), a.getAnio(), a.getEstado()))
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO,
                        "No existe el año escolar " + anio));
    }

    @Transactional(readOnly = true)
    public AnioEscolarResumen obtenerPorId(String id) {
        return anios.findById(id)
                .map(a -> new AnioEscolarResumen(a.getId(), a.getAnio(), a.getEstado()))
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO,
                        "No existe el año escolar"));
    }

    /**
     * El año ACTIVO con un bimestre en curso en la fecha, o vacío si la fecha cae fuera de todo bimestre (vacaciones,
     * antes de empezar o después de terminar). No hay feriados: el modelo no tiene esa entidad (S-07, P-04).
     */
    @Transactional(readOnly = true)
    public java.util.Optional<AnioEscolarResumen> anioLectivoEn(java.time.LocalDate fecha) {
        return anios.findActivosConBimestreEn(fecha).stream().findFirst()
                .map(a -> new AnioEscolarResumen(a.getId(), a.getAnio(), a.getEstado()));
    }

    /** El año escolar ACTIVO, si lo hay (solo puede haber uno). */
    @Transactional(readOnly = true)
    public java.util.Optional<AnioEscolarResumen> anioActivo() {
        return anios.findByEstado("ACTIVO").stream().findFirst()
                .map(a -> new AnioEscolarResumen(a.getId(), a.getAnio(), a.getEstado()));
    }

    /** Hay un año escolar ACTIVO: sin él no existe calendario sobre el cual decidir qué días son lectivos. */
    @Transactional(readOnly = true)
    public boolean existeAnioActivo() {
        return !anios.findByEstado("ACTIVO").isEmpty();
    }

    /** Ids de los años escolares anteriores al indicado (para las deudas de años pasados, UC-02 FA-3). */
    @Transactional(readOnly = true)
    public List<String> idsAnterioresA(int anio) {
        return anios.findByAnioLessThan(anio).stream().map(a -> a.getId()).toList();
    }

    /** Ids de los años que no están cerrados: los «vigentes» para el apoderado (matrícula y obligaciones). */
    @Transactional(readOnly = true)
    public List<String> idsNoCerrados() {
        return anios.findByEstadoNot("CERRADO").stream().map(a -> a.getId()).toList();
    }
}
