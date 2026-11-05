package pe.edu.gesedu.asistencia.infraestructura;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.asistencia.dominio.CierreDiario;

public interface CierreDiarioRepository extends JpaRepository<CierreDiario, String> {

    Optional<CierreDiario> findByFecha(LocalDate fecha);

    /**
     * Anota el resultado del cierre de la fecha: una sola fila por fecha. Un cierre EXITOSO nunca se pisa (por ejemplo,
     * con el fallo de un reintento que llegó tarde); un FALLIDO sí se reemplaza cuando el reintento resulta.
     */
    @Modifying
    @Query(value = """
            INSERT INTO asistencia.cierre_diario (id, fecha, estado, detalle, ejecutado_en)
            VALUES (uuidv7()::text, :fecha, :estado, :detalle, :ejecutadoEn)
            ON CONFLICT (fecha) DO UPDATE SET estado = EXCLUDED.estado, detalle = EXCLUDED.detalle,
                ejecutado_en = EXCLUDED.ejecutado_en, updated_at = EXCLUDED.ejecutado_en
            WHERE asistencia.cierre_diario.estado <> 'EXITOSO'
            """, nativeQuery = true)
    int registrar(@Param("fecha") LocalDate fecha, @Param("estado") String estado, @Param("detalle") String detalle,
                  @Param("ejecutadoEn") Instant ejecutadoEn);
}
