package pe.edu.gesedu.seguridad.infraestructura;

import java.util.Collection;
import java.util.List;

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

    @Query("SELECT d.id AS id, u.nombres AS nombres, u.apellidos AS apellidos FROM Docente d, Usuario u "
            + "WHERE u.id = d.usuarioId ORDER BY u.apellidos, u.nombres")
    List<DocenteConNombres> buscarTodosConNombres();
}
