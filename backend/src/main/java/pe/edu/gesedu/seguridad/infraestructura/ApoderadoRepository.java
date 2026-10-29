package pe.edu.gesedu.seguridad.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.seguridad.dominio.Apoderado;

public interface ApoderadoRepository extends JpaRepository<Apoderado, String> {

    Optional<Apoderado> findByUsuarioId(String usuarioId);
}
