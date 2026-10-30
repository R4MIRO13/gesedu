package pe.edu.gesedu.horarios.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.horarios.dominio.PeriodoRegistroDisponibilidad;

public interface PeriodoDisponibilidadRepository extends JpaRepository<PeriodoRegistroDisponibilidad, String> {

    Optional<PeriodoRegistroDisponibilidad> findByAnioEscolarId(String anioEscolarId);
}
