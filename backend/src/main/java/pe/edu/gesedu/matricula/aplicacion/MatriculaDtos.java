package pe.edu.gesedu.matricula.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.SeccionConVacantes;
import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.matricula.dominio.EstadoAdmision;
import pe.edu.gesedu.matricula.dominio.EstadoDocumentacion;
import pe.edu.gesedu.matricula.dominio.EstadoSolicitud;
import pe.edu.gesedu.matricula.dominio.MotivoRechazo;
import pe.edu.gesedu.matricula.dominio.ReglasMatricula.Regla;
import pe.edu.gesedu.matricula.dominio.TipoEstudiante;
import pe.edu.gesedu.pagos.aplicacion.PlanDeCobros.ResumenCostos;
import pe.edu.gesedu.transversal.archivos.GestorArchivos.UrlPrefirmada;

/** Contrato de la evaluación de solicitudes de matrícula (UC-02). */
public final class MatriculaDtos {

    private MatriculaDtos() {
    }

    /** Fila de la bandeja (paso 1). */
    public record SolicitudResumen(String codigo, String alumno, String grado, LocalDate fechaSolicitud,
                                   TipoEstudiante tipoEstudiante, EstadoSolicitud estado,
                                   EstadoDocumentacion estadoDocumentacion, MotivoRechazo motivoRechazo,
                                   String estadoVisible) {
    }

    public record AlumnoSolicitudDto(String codigo, String nombre, String documento, LocalDate fechaNacimiento) {
    }

    public record ApoderadoSolicitudDto(String nombre, String documento, String correo, String telefono, String parentesco) {
    }

    public record DocumentoSolicitudDto(String id, String tipo, String nombreArchivo, String contentType, long tamanoBytes) {
    }

    public record ValidacionMatriculaDto(Regla regla, boolean aprobada, String mensaje, MotivoRechazo motivoSugerido) {
    }

    /** Detalle para evaluar (pasos 2 a 7): datos, validaciones, vacantes por sección y resumen de costos. */
    public record DetalleSolicitud(String codigo, String anioEscolar, String grado, LocalDate fechaSolicitud,
                                   TipoEstudiante tipoEstudiante, EstadoAdmision estadoAdmision,
                                   EstadoDocumentacion estadoDocumentacion, EstadoSolicitud estado,
                                   MotivoRechazo motivoRechazo, String estadoVisible, String observacion,
                                   AlumnoSolicitudDto alumno, ApoderadoSolicitudDto apoderado, List<DocumentoSolicitudDto> documentos,
                                   List<ValidacionMatriculaDto> validaciones, boolean puedeConfirmar,
                                   List<SeccionConVacantes> secciones, ResumenCostos costos, long version) {
    }

    /** Resultado de la confirmación (paso 13): «3° A pasa a 3 vacantes y se generan 11 compromisos de pago». */
    public record ResultadoConfirmacion(String matriculaId, String codigoSolicitud, String seccionId, String seccion,
                                        int vacantesRestantes, int compromisosGenerados, Dinero total) {
    }

    public record ResultadoRechazo(String solicitudId, String codigoSolicitud, MotivoRechazo motivo, String estadoVisible) {
    }

    // ---- UC-01: solicitud del apoderado ----

    public record GradoOpcion(String id, String nombre) {
    }

    /** Datos del apoderado que presenta la solicitud (solo lectura en el asistente, salvo el celular). */
    public record DatosApoderado(String nombre, String documento, String correo, String telefono) {
    }

    /** Lo que el asistente necesita para armar la solicitud: el apoderado, los grados del año y los documentos que exige cada tipo. */
    public record OpcionesSolicitud(int anio, DatosApoderado apoderado, List<GradoOpcion> grados,
                                    Map<TipoEstudiante, List<String>> documentosRequeridos, boolean costosConfigurados) {
    }

    /** URL para subir un documento y la clave con la que se declara después en la solicitud. */
    public record ArchivoSubida(String claveObjeto, UrlPrefirmada subida) {
    }

    /** Constancia de la solicitud registrada (paso 7): código de seguimiento y estado. */
    public record SolicitudRegistrada(String codigo, EstadoSolicitud estado, String estadoVisible, LocalDate fechaSolicitud,
                                      int anio, String alumno, String grado, String mensaje) {
    }

    /** Fila de «mis solicitudes» del apoderado. */
    public record SolicitudPropia(String codigo, String alumno, String grado, int anio, LocalDate fechaSolicitud,
                                  TipoEstudiante tipoEstudiante, EstadoSolicitud estado, String estadoVisible,
                                  String observacion) {
    }
}
