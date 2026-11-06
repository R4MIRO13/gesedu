package pe.edu.gesedu.seguridad.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.seguridad.dominio.Alumno;

public interface AlumnoRepository extends JpaRepository<Alumno, String> {

    Optional<Alumno> findByUsuarioId(String usuarioId);

    /**
     * Candado consultivo de la transacción, <b>por documento</b>: dos altas simultáneas del mismo documento se
     * serializan (la segunda ve a la primera), pero altas de documentos distintos no se esperan entre sí.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:documento, 7311001))) AS candado",
            nativeQuery = true)
    int tomarCandadoDelDocumento(@Param("documento") String documento);

    /** Siguiente número del código {@code AAAA-NNNN} (V13): no bloquea ni espera a otras transacciones. */
    @Query(value = "SELECT nextval('seguridad.seq_codigo_alumno')", nativeQuery = true)
    long siguienteNumeroDeCodigo();

    boolean existsByCodigoAlumno(String codigoAlumno);
}
