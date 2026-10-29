package pe.edu.gesedu.matricula.infraestructura;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import pe.edu.gesedu.matricula.dominio.EstadoSolicitud;
import pe.edu.gesedu.matricula.dominio.SolicitudMatricula;

public interface SolicitudMatriculaRepository
        extends JpaRepository<SolicitudMatricula, String>, JpaSpecificationExecutor<SolicitudMatricula> {

    Optional<SolicitudMatricula> findByCodigoSeguimiento(String codigoSeguimiento);

    boolean existsByAlumnoIdAndAnioEscolarIdAndEstado(String alumnoId, String anioEscolarId, EstadoSolicitud estado);

    List<SolicitudMatricula> findByApoderadoIdOrderByFechaSolicitudDescCodigoSeguimientoDesc(String apoderadoId);

    @Query(value = "SELECT nextval('matricula.seq_solicitud')", nativeQuery = true)
    long siguienteNumeroDeSolicitud();
}
