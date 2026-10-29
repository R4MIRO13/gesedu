package pe.edu.gesedu.academico.infraestructura;

import java.util.Collection;
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

    /** Devuelve la vacante a la sección sin pasar de su capacidad (RF-05). Devuelve 0 si ya estaba completa. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Seccion s SET s.vacantesDisponibles = s.vacantesDisponibles + 1, s.version = s.version + 1 "
            + "WHERE s.id = :id AND s.vacantesDisponibles < s.capacidad")
    int liberarVacante(@Param("id") String id);

    /** Cupo de una sección leído con bloqueo (sin cargar la entidad). */
    interface CupoFila {
        String getId();

        int getCapacidad();

        int getVacantes();
    }

    /**
     * Bloquea las secciones con {@code SELECT … FOR UPDATE} siempre en el mismo orden (por id): dos traslados cruzados
     * (A→B y B→A) no se interbloquean. Es una consulta de escalares: con una entidad ya cargada, Hibernate comprobaría
     * su {@code @Version} al bloquear y fallaría si otra transacción la subió un instante antes.
     */
    @Query(value = "SELECT id AS id, capacidad AS capacidad, vacantes_disponibles AS vacantes FROM academico.seccion "
            + "WHERE id IN (:ids) ORDER BY id FOR UPDATE", nativeQuery = true)
    List<CupoFila> bloquearCupos(@Param("ids") Collection<String> ids);

    /** Lectura directa de la base: devuelve el valor actual aunque la sección ya esté cargada en la sesión. */
    @Query("SELECT s.vacantesDisponibles FROM Seccion s WHERE s.id = :id")
    java.util.Optional<Integer> vacantesDe(@Param("id") String id);
}
