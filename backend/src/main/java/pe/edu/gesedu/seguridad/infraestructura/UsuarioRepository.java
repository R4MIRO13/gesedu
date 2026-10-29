package pe.edu.gesedu.seguridad.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.seguridad.dominio.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, String>, JpaSpecificationExecutor<Usuario> {

    /**
     * El correo es CITEXT. Con un parámetro varchar, PostgreSQL compara como text y distingue mayúsculas;
     * el CAST explícito conserva la comparación sin distinguirlas (y el índice único).
     */
    @Query(value = "SELECT * FROM seguridad.usuario WHERE correo = CAST(:correo AS citext)", nativeQuery = true)
    Optional<Usuario> buscarPorCorreo(@Param("correo") String correo);

    @EntityGraph(attributePaths = {"rolAcceso", "rolAcceso.permisos"})
    Optional<Usuario> findConPermisosById(String id);

    boolean existsByDocumentoIdentidad(String documentoIdentidad);

    boolean existsByDocumentoIdentidadAndIdNot(String documentoIdentidad, String id);

    /** El correo es CITEXT: el CAST conserva la comparación sin distinguir mayúsculas. */
    @Query(value = "SELECT count(*) > 0 FROM seguridad.usuario WHERE correo = CAST(:correo AS citext) AND id <> :id",
            nativeQuery = true)
    boolean existeCorreoDeOtro(@Param("correo") String correo, @Param("id") String id);


}
