package pe.edu.gesedu.matricula.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import pe.edu.gesedu.matricula.dominio.SolicitudMatricula;

public interface SolicitudMatriculaRepository
        extends JpaRepository<SolicitudMatricula, String>, JpaSpecificationExecutor<SolicitudMatricula> {

    Optional<SolicitudMatricula> findByCodigoSeguimiento(String codigoSeguimiento);
}
