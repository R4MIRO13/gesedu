package pe.edu.gesedu.seguridad.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.seguridad.dominio.Permiso;

public interface PermisoRepository extends JpaRepository<Permiso, String> {

    List<Permiso> findByCodigoIn(Collection<String> codigos);
}
