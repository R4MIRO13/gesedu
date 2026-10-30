package pe.edu.gesedu.horarios.infraestructura;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.gesedu.horarios.dominio.HorarioPublicado;

public interface HorarioPublicadoRepository extends JpaRepository<HorarioPublicado, String> {

    Optional<HorarioPublicado> findByAnioEscolarIdAndVigenteTrue(String anioEscolarId);

    List<HorarioPublicado> findByAnioEscolarIdOrderByNumeroVersionDesc(String anioEscolarId);

    @Query("SELECT COALESCE(MAX(h.numeroVersion), 0) FROM HorarioPublicado h WHERE h.anioEscolarId = :anioEscolarId")
    int ultimaVersion(@Param("anioEscolarId") String anioEscolarId);

    /**
     * Retira la marca de vigente de la versión anterior. Es lo único que el trigger de la base deja cambiar en un
     * horario publicado. Va antes de insertar la nueva versión para no chocar con el índice único de vigentes.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE HorarioPublicado h SET h.vigente = FALSE WHERE h.anioEscolarId = :anioEscolarId AND h.vigente = TRUE")
    int retirarVigente(@Param("anioEscolarId") String anioEscolarId);
}
