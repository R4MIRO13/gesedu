package pe.edu.gesedu.horarios.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.horarios.dominio.HorarioBorrador;

public interface HorarioBorradorRepository extends JpaRepository<HorarioBorrador, String> {

    Optional<HorarioBorrador> findByAnioEscolarId(String anioEscolarId);
}
