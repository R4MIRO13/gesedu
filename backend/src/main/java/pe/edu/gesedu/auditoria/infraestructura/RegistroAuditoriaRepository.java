package pe.edu.gesedu.auditoria.infraestructura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import pe.edu.gesedu.auditoria.dominio.RegistroAuditoria;

public interface RegistroAuditoriaRepository
        extends JpaRepository<RegistroAuditoria, String>, JpaSpecificationExecutor<RegistroAuditoria> {
}
