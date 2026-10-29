package pe.edu.gesedu.auditoria.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.auditoria.dominio.CambioAuditoria;

public interface CambioAuditoriaRepository extends JpaRepository<CambioAuditoria, String> {

    List<CambioAuditoria> findByRegistroAuditoriaIdOrderByCampo(String registroAuditoriaId);

    List<CambioAuditoria> findByRegistroAuditoriaIdIn(Collection<String> registroAuditoriaIds);
}
