package pe.edu.gesedu.asistencia.infraestructura;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.asistencia.dominio.AsistenciaAlumno;

public interface AsistenciaAlumnoRepository extends JpaRepository<AsistenciaAlumno, String> {

    List<AsistenciaAlumno> findByFechaAndAlumnoIdIn(LocalDate fecha, Collection<String> alumnoIds);

    /**
     * Inserta la asistencia si el alumno aún no tiene una ese día y devuelve cuántas filas creó (1 o 0). Con dos
     * registros simultáneos de la misma sección gana uno solo: el otro recibe 0 sin que la transacción falle (UC-08 FA-5).
     */
    @Modifying
    @Query(value = """
            INSERT INTO asistencia.asistencia_alumno (id, alumno_id, fecha, estado)
            VALUES (uuidv7()::text, :alumnoId, :fecha, :estado)
            ON CONFLICT (alumno_id, fecha) DO NOTHING
            """, nativeQuery = true)
    int insertarSiNoExiste(@Param("alumnoId") String alumnoId, @Param("fecha") LocalDate fecha,
                           @Param("estado") String estado);

    /**
     * Cambia una FALTA de la fecha a TARDANZA y devuelve cuántas filas cambió (1 o 0): solo cambia si sigue siendo FALTA,
     * así dos cambios simultáneos o un cambio sobre un registro que ya no es elegible no pisan nada (UC-09). Modifica
     * el mismo registro y su {@code updated_at}; no deja historial en el módulo (C-07).
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE asistencia.asistencia_alumno SET estado = 'TARDANZA', updated_at = :ahora
            WHERE id = :id AND fecha = :fecha AND estado = 'FALTA'
            """, nativeQuery = true)
    int cambiarFaltaATardanza(@Param("id") String id, @Param("fecha") LocalDate fecha, @Param("ahora") java.time.Instant ahora);
}
