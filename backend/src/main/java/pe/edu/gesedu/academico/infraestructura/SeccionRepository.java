package pe.edu.gesedu.academico.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.academico.dominio.Seccion;

public interface SeccionRepository extends JpaRepository<Seccion, String> {

    List<Seccion> findByAnioEscolarId(String anioEscolarId);

    List<Seccion> findByGradoIdOrderByLetra(String gradoId);

    /**
     * Descuenta una vacante con un UPDATE condicional (RNF-09): la base serializa a quienes compiten por la última
     * y el CHECK impide que queden negativas. Devuelve 0 si ya no había.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Seccion s SET s.vacantesDisponibles = s.vacantesDisponibles - 1, s.version = s.version + 1 "
            + "WHERE s.id = :id AND s.vacantesDisponibles > 0")
    int reservarVacante(@Param("id") String id);

    /** Lectura directa de la base: devuelve el valor actual aunque la sección ya esté cargada en la sesión. */
    @Query("SELECT s.vacantesDisponibles FROM Seccion s WHERE s.id = :id")
    java.util.Optional<Integer> vacantesDe(@Param("id") String id);
}
