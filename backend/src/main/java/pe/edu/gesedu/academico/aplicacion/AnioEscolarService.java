package pe.edu.gesedu.academico.aplicacion;

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
}
