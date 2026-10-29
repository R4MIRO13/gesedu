package pe.edu.gesedu.seguridad.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.seguridad.dominio.RolAcceso;

public interface RolAccesoRepository extends JpaRepository<RolAcceso, String> {

    Optional<RolAcceso> findByCodigo(String codigo);
}
