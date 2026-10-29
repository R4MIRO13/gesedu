package pe.edu.gesedu.matricula.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.gesedu.matricula.dominio.DocumentoSolicitud;

public interface DocumentoSolicitudRepository extends JpaRepository<DocumentoSolicitud, String> {

    List<DocumentoSolicitud> findBySolicitudIdOrderByCreatedAt(String solicitudId);
}
