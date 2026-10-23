package pe.edu.gesedu.auditoria.infraestructura;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.auditoria.dominio.CambioAuditoria;

public interface CambioAuditoriaRepository extends JpaRepository<CambioAuditoria, String> {
}
