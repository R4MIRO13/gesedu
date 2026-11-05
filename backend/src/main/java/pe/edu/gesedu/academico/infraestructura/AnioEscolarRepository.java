package pe.edu.gesedu.academico.infraestructura;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.academico.dominio.AnioEscolar;

public interface AnioEscolarRepository extends JpaRepository<AnioEscolar, String> {

    Optional<AnioEscolar> findByAnio(int anio);

    List<AnioEscolar> findByAnioLessThan(int anio);

    List<AnioEscolar> findByEstadoNot(String estado);

    /** El año ACTIVO que tiene un bimestre en curso en la fecha (S-07: así se reconoce un día lectivo). */
    @Query("""
            SELECT a FROM AnioEscolar a WHERE a.estado = 'ACTIVO' AND EXISTS
                (SELECT 1 FROM Bimestre b WHERE b.anioEscolarId = a.id AND :fecha BETWEEN b.fechaInicio AND b.fechaFin)
            """)
    List<AnioEscolar> findActivosConBimestreEn(@Param("fecha") LocalDate fecha);
}
