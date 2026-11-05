package pe.edu.gesedu.asistencia.infraestructura;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.asistencia.dominio.AsistenciaDocente;

public interface AsistenciaDocenteRepository extends JpaRepository<AsistenciaDocente, String> {

    Optional<AsistenciaDocente> findByDocenteIdAndFecha(String docenteId, LocalDate fecha);

    List<AsistenciaDocente> findByDocenteIdAndFechaBetweenOrderByFechaDesc(String docenteId, LocalDate desde, LocalDate hasta);

    /**
     * Inserta la asistencia si el docente aún no tiene una ese día y devuelve cuántas filas creó (1 o 0). Con dos
     * solicitudes simultáneas gana una sola: la otra recibe 0 sin que la transacción falle (UC-07 FA-1).
     */
    @Modifying
    @Query(value = """
            INSERT INTO asistencia.asistencia_docente (id, docente_id, fecha, hora_registro, estado)
            VALUES (uuidv7()::text, :docenteId, :fecha, :horaRegistro, :estado)
            ON CONFLICT (docente_id, fecha) DO NOTHING
            """, nativeQuery = true)
    int insertarSiNoExiste(@Param("docenteId") String docenteId, @Param("fecha") LocalDate fecha,
                           @Param("horaRegistro") Instant horaRegistro, @Param("estado") String estado);
}
