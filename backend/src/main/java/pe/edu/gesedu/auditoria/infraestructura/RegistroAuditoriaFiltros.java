package pe.edu.gesedu.auditoria.infraestructura;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import pe.edu.gesedu.auditoria.dominio.RegistroAuditoria;

/** Filtros opcionales de la consulta de auditoría (RF-51): un filtro {@code null} no restringe. */
public final class RegistroAuditoriaFiltros {

    private RegistroAuditoriaFiltros() {
    }

    /** Rango de tiempo con {@code desde} incluido y {@code hasta} excluido. */
    public static Specification<RegistroAuditoria> con(String usuarioId, String modulo, String accion,
                                                       Instant desde, Instant hasta) {
        List<Specification<RegistroAuditoria>> filtros = new ArrayList<>();
        if (usuarioId != null) {
            filtros.add((r, q, cb) -> cb.equal(r.get("usuarioId"), usuarioId));
        }
        if (modulo != null) {
            filtros.add((r, q, cb) -> cb.equal(r.get("modulo"), modulo));
        }
        if (accion != null) {
            filtros.add((r, q, cb) -> cb.equal(r.get("accion"), accion));
        }
        if (desde != null) {
            filtros.add((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("fechaHora"), desde));
        }
        if (hasta != null) {
            filtros.add((r, q, cb) -> cb.lessThan(r.get("fechaHora"), hasta));
        }
        return Specification.allOf(filtros);
    }
}
