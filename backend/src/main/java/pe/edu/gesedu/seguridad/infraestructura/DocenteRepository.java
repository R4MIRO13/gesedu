package pe.edu.gesedu.seguridad.infraestructura;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.seguridad.dominio.Docente;

public interface DocenteRepository extends JpaRepository<Docente, String> {

    /** Id del docente con los nombres de su cuenta. */
    interface DocenteConNombres {
        String getId();

        String getNombres();

        String getApellidos();
    }

    @Query("SELECT d.id AS id, u.nombres AS nombres, u.apellidos AS apellidos FROM Docente d, Usuario u "
            + "WHERE u.id = d.usuarioId AND d.id IN :ids")
    List<DocenteConNombres> buscarConNombres(@Param("ids") Collection<String> ids);

    /** El perfil de docente de una cuenta, si la tiene. */
    @Query("SELECT d.id AS id, u.nombres AS nombres, u.apellidos AS apellidos FROM Docente d, Usuario u "
            + "WHERE u.id = d.usuarioId AND u.id = :usuarioId")
    List<DocenteConNombres> buscarPorUsuario(@Param("usuarioId") String usuarioId);

    @Query("SELECT d.id AS id, u.nombres AS nombres, u.apellidos AS apellidos FROM Docente d, Usuario u "
            + "WHERE u.id = d.usuarioId ORDER BY u.apellidos, u.nombres")
    List<DocenteConNombres> buscarTodosConNombres();

    Optional<Docente> findByUsuarioId(String usuarioId);

    /** Candado consultivo de la transacción: serializa las altas de docentes que calculan su código. */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(7311002)) AS candado", nativeQuery = true)
    int tomarCandadoDeCodigos();

    /** Siguiente número del código {@code DOC-NNN} (llamar tras el candado). */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(codigo_docente FROM 5) AS integer)), 0) + 1
            FROM seguridad.docente
            WHERE codigo_docente ~ '^DOC-[0-9]+$'
            """, nativeQuery = true)
    int siguienteNumeroDeCodigo();
}
