package pe.edu.gesedu.pagos.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.pagos.dominio.CronogramaCobro;

public interface CronogramaCobroRepository extends JpaRepository<CronogramaCobro, String> {

    List<CronogramaCobro> findByAnioEscolarId(String anioEscolarId);
}
