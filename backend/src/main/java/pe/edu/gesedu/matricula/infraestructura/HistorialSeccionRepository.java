package pe.edu.gesedu.matricula.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.matricula.dominio.HistorialSeccion;

public interface HistorialSeccionRepository extends JpaRepository<HistorialSeccion, String> {

    boolean existsByMatriculaId(String matriculaId);

    /** Ids de las matrículas que alguna vez estuvieron en la sección. */
    @Query("SELECT DISTINCT h.matriculaId FROM HistorialSeccion h WHERE h.seccionId = :seccionId")
    List<String> matriculasQueEstuvieronEn(@Param("seccionId") String seccionId);

    /** Línea de tiempo de cada matrícula, de la más antigua a la más reciente (el id UUIDv7 desempata el mismo día). */
    @Query("SELECT h FROM HistorialSeccion h WHERE h.matriculaId IN :matriculaIds ORDER BY h.desde, h.id")
    List<HistorialSeccion> lineaDeTiempo(@Param("matriculaIds") Collection<String> matriculaIds);
}
