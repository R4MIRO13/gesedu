package pe.edu.gesedu.auditoria.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import pe.edu.gesedu.auditoria.dominio.RegistroAuditoria;

public interface RegistroAuditoriaRepository
        extends JpaRepository<RegistroAuditoria, String>, JpaSpecificationExecutor<RegistroAuditoria> {

    /** Par módulo–acción que existe en la auditoría. */
    interface ModuloYAccion {
        String getModulo();

        String getAccion();
    }

    @Query("SELECT DISTINCT r.modulo AS modulo, r.accion AS accion FROM RegistroAuditoria r "
            + "ORDER BY r.modulo, r.accion")
    List<ModuloYAccion> modulosYAcciones();

    /** Usuarios que figuran en la auditoría (el actor «Sistema» no tiene id). */
    @Query("SELECT DISTINCT r.usuarioId FROM RegistroAuditoria r WHERE r.usuarioId IS NOT NULL")
    List<String> usuariosConRegistros();
}
