package pe.edu.gesedu.seguridad.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.seguridad.dominio.Administrador;

public interface AdministradorRepository extends JpaRepository<Administrador, String> {

    Optional<Administrador> findByUsuarioId(String usuarioId);
}
