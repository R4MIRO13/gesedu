package pe.edu.gesedu.seguridad.infraestructura;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.seguridad.dominio.Alumno;

/** Consultas de solo lectura sobre alumnos y apoderados con los datos de su cuenta. */
public interface FamiliaRepository extends JpaRepository<Alumno, String> {

    interface AlumnoFila {
        String getId();

        String getCodigo();

        String getNombreCompleto();

        String getDocumento();

        LocalDate getFechaNacimiento();

        String getParentesco();
    }

    interface ApoderadoFila {
        String getId();

        String getNombreCompleto();

        String getDocumento();

        String getCorreo();

        String getTelefono();
    }

    @Query(value = """
            SELECT al.id AS id, al.codigo_alumno AS codigo, u.nombres || ' ' || u.apellidos AS nombreCompleto,
                   u.documento_identidad AS documento, al.fecha_nacimiento AS fechaNacimiento,
                   al.parentesco_apoderado AS parentesco
            FROM seguridad.alumno al JOIN seguridad.usuario u ON u.id = al.usuario_id
            WHERE al.id IN (:ids)
            """, nativeQuery = true)
    List<AlumnoFila> alumnos(@Param("ids") Collection<String> ids);

    @Query(value = """
            SELECT ap.id AS id, u.nombres || ' ' || u.apellidos AS nombreCompleto,
                   u.documento_identidad AS documento, CAST(u.correo AS text) AS correo,
                   ap.numero_telefono AS telefono
            FROM seguridad.apoderado ap JOIN seguridad.usuario u ON u.id = ap.usuario_id
            WHERE ap.id IN (:ids)
            """, nativeQuery = true)
    List<ApoderadoFila> apoderados(@Param("ids") Collection<String> ids);

    @Query(value = """
            SELECT al.id FROM seguridad.alumno al JOIN seguridad.usuario u ON u.id = al.usuario_id
            WHERE lower(u.nombres || ' ' || u.apellidos) LIKE :patron
               OR lower(u.documento_identidad) LIKE :patron
               OR lower(al.codigo_alumno) LIKE :patron
            """, nativeQuery = true)
    List<String> buscarAlumnoIds(@Param("patron") String patron);
}
