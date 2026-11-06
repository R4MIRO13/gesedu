package pe.edu.gesedu.seguridad.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.seguridad.dominio.Alumno;

public interface AlumnoRepository extends JpaRepository<Alumno, String> {

    Optional<Alumno> findByUsuarioId(String usuarioId);

    /** Candado consultivo de la transacción: serializa las altas de alumnos que calculan su código. */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(7311001)) AS candado", nativeQuery = true)
    int tomarCandadoDeCodigos();

    /** Siguiente número del código {@code AAAA-NNNN} de los alumnos del año (llamar tras el candado). */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(substring(codigo_alumno FROM 6) AS integer)), 0) + 1
            FROM seguridad.alumno
            WHERE codigo_alumno ~ ('^' || CAST(:anio AS text) || '-[0-9]+$')
            """, nativeQuery = true)
    int siguienteNumeroDeCodigo(@Param("anio") int anio);
}
