package pe.edu.gesedu.matricula.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.GradoResumen;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.ArchivoSubida;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.DatosApoderado;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.GradoOpcion;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.OpcionesSolicitud;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.SolicitudPropia;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.SolicitudRegistrada;
import pe.edu.gesedu.matricula.dominio.DocumentoSolicitud;
import pe.edu.gesedu.matricula.dominio.EstadoSolicitud;
import pe.edu.gesedu.matricula.dominio.ReglasMatricula;
import pe.edu.gesedu.matricula.dominio.SolicitudMatricula;
import pe.edu.gesedu.matricula.dominio.TipoEstudiante;
import pe.edu.gesedu.matricula.infraestructura.DocumentoSolicitudRepository;
import pe.edu.gesedu.matricula.infraestructura.SolicitudMatriculaRepository;
import pe.edu.gesedu.pagos.aplicacion.PlanDeCobros;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias.AlumnoResumen;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias.ApoderadoResumen;
import pe.edu.gesedu.seguridad.aplicacion.RegistroFamilias;
import pe.edu.gesedu.seguridad.aplicacion.RegistroFamilias.AlumnoRegistrado;
import pe.edu.gesedu.seguridad.aplicacion.RegistroFamilias.DatosAlumno;
import pe.edu.gesedu.transversal.archivos.GestorArchivos;
import pe.edu.gesedu.transversal.archivos.ReglasArchivo;

/**
 * Solicitud de matrícula del apoderado (UC-01, RF-02, RF-08): asistente con documentos subidos por URL prefirmada,
 * validación de campos y documentos (FA-1), del documento de identidad (FA-2) y de la solicitud en trámite, y
 * «mis solicitudes» para seguir el estado. No envía correo (C-17).
 */
@Service
public class SolicitudApoderadoService {

    /** Un documento que el apoderado ya subió y ahora declara en la solicitud. */
    public record DocumentoPresentado(String tipo, String claveObjeto, String nombreArchivo, String contentType,
                                      long tamanoBytes) {
    }

    public record NuevaSolicitud(int anio, TipoEstudiante tipoEstudiante, String gradoId, DatosAlumno alumno,
                                 String telefonoApoderado, List<DocumentoPresentado> documentos) {
    }

    private static final int MAXIMO_DOCUMENTOS = 5;

    private final SolicitudMatriculaRepository solicitudes;
    private final DocumentoSolicitudRepository documentos;
    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final PlanDeCobros cobros;
    private final DirectorioFamilias familias;
    private final RegistroFamilias registro;
    private final ContextoUsuario contexto;
    private final GestorArchivos archivos;
    private final Clock reloj;

    SolicitudApoderadoService(SolicitudMatriculaRepository solicitudes, DocumentoSolicitudRepository documentos,
                              AnioEscolarService anios, CatalogoAcademicoService catalogo, PlanDeCobros cobros,
                              DirectorioFamilias familias, RegistroFamilias registro, ContextoUsuario contexto,
                              GestorArchivos archivos, Clock reloj) {
        this.solicitudes = solicitudes;
        this.documentos = documentos;
        this.anios = anios;
        this.catalogo = catalogo;
        this.cobros = cobros;
        this.familias = familias;
        this.registro = registro;
        this.contexto = contexto;
        this.archivos = archivos;
        this.reloj = reloj;
    }

    /** Grados del año, documentos que se exigen por tipo y si los costos ya están configurados (precondiciones). */
    @Transactional(readOnly = true)
    public OpcionesSolicitud opciones(int anio) {
        ApoderadoResumen apoderado = exigirApoderado();
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        List<GradoOpcion> grados = catalogo.gradosDelAnio(a.id()).stream()
                .map(g -> new GradoOpcion(g.id(), g.numeroNivel() + "°")).toList();
        Map<TipoEstudiante, List<String>> requeridos = new EnumMap<>(TipoEstudiante.class);
        for (TipoEstudiante tipo : TipoEstudiante.values()) {
            requeridos.put(tipo, ReglasMatricula.documentosRequeridos(tipo));
        }
        boolean costos = !cobros.resumen(a.id(), false).lineas().isEmpty();
        return new OpcionesSolicitud(anio, new DatosApoderado(apoderado.nombreCompleto(), apoderado.documento(),
                apoderado.correo(), apoderado.telefono()), grados, requeridos, costos);
    }

    /** URL prefirmada de 5 minutos para subir un documento; la solicitud lo declara después con su clave. */
    @Transactional(readOnly = true)
    public ArchivoSubida solicitarSubida(String nombreArchivo, String contentType, long tamanoBytes) {
        ApoderadoResumen apoderado = exigirApoderado();
        ReglasArchivo.validar(contentType, tamanoBytes);
        String clave = ReglasArchivo.nuevaClave(prefijo(apoderado.id()), nombreArchivo);
        return new ArchivoSubida(clave, archivos.urlSubida(clave, contentType, tamanoBytes));
    }

    /** Registra la solicitud en estado «Pendiente de revisión» y devuelve la constancia con el código (pasos 5 a 7). */
    @Transactional
    @Auditable(accion = "REGISTRAR_SOLICITUD_MATRICULA", modulo = "MATRICULA", tipoEntidad = "SolicitudMatricula",
            idEntidad = "#result.codigo", descripcion = "Solicitud de matrícula presentada por el apoderado")
    public SolicitudRegistrada registrar(NuevaSolicitud nueva) {
        ApoderadoResumen apoderado = exigirApoderado();
        AnioEscolarResumen anio = anios.obtenerPorAnio(nueva.anio());
        if ("CERRADO".equals(anio.estado())) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA,
                    "El año escolar " + nueva.anio() + " ya no admite solicitudes");
        }
        if (cobros.resumen(anio.id(), nueva.tipoEstudiante() == TipoEstudiante.NUEVO_INGRESO).lineas().isEmpty()) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA,
                    "Los costos del año escolar " + nueva.anio() + " aún no están configurados");
        }
        GradoResumen grado = catalogo.grado(nueva.gradoId());
        if (!grado.anioEscolarId().equals(anio.id())) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA, "El grado no pertenece al año " + nueva.anio());
        }
        validarDocumentos(nueva, apoderado.id());

        AlumnoRegistrado registrado = registro.obtenerORegistrar(apoderado.id(), nueva.alumno(), nueva.anio());
        AlumnoResumen alumno = registrado.alumno();
        if (solicitudes.existsByAlumnoIdAndAnioEscolarIdAndEstado(alumno.id(), anio.id(),
                EstadoSolicitud.PENDIENTE_REVISION)) {
            throw enTramite(nueva.anio());
        }
        registro.actualizarTelefono(apoderado.id(), nueva.telefonoApoderado());

        String codigo = "MAT-%d-%05d".formatted(nueva.anio(), solicitudes.siguienteNumeroDeSolicitud());
        SolicitudMatricula solicitud;
        try {
            solicitud = solicitudes.saveAndFlush(new SolicitudMatricula(codigo, anio.id(), alumno.id(), apoderado.id(),
                    grado.id(), LocalDate.now(reloj), nueva.tipoEstudiante()));
        } catch (DataIntegrityViolationException e) {
            // Dos envíos simultáneos: el índice único de solicitudes en trámite deja pasar solo uno
            throw enTramite(nueva.anio());
        }
        for (DocumentoPresentado d : nueva.documentos()) {
            documentos.save(new DocumentoSolicitud(solicitud.getId(), d.tipo(), d.claveObjeto(), d.nombreArchivo(),
                    d.contentType(), d.tamanoBytes()));
        }
        return new SolicitudRegistrada(codigo, solicitud.getEstado(), solicitud.estadoVisible(),
                solicitud.getFechaSolicitud(), nueva.anio(), alumno.nombreCompleto(), grado.numeroNivel() + "°",
                "Recibimos la solicitud. Pasará a revisión administrativa; con el código " + codigo
                        + " puedes consultar su estado.");
    }

    /** Las solicitudes del apoderado, de la más reciente a la más antigua (seguimiento del trámite). */
    @Transactional(readOnly = true)
    public List<SolicitudPropia> mias() {
        ApoderadoResumen apoderado = exigirApoderado();
        List<SolicitudMatricula> lista = solicitudes
                .findByApoderadoIdOrderByFechaSolicitudDescCodigoSeguimientoDesc(apoderado.id());
        Map<String, AlumnoResumen> alumnos = familias.alumnos(
                lista.stream().map(SolicitudMatricula::getAlumnoId).distinct().toList());
        Map<String, Integer> anioPorId = lista.stream().map(SolicitudMatricula::getAnioEscolarId).distinct()
                .collect(Collectors.toMap(id -> id, id -> anios.obtenerPorId(id).anio()));
        Map<String, String> gradoPorId = lista.stream().map(SolicitudMatricula::getGradoId).distinct()
                .collect(Collectors.toMap(id -> id, id -> catalogo.grado(id).numeroNivel() + "°"));
        return lista.stream().map(s -> new SolicitudPropia(s.getCodigoSeguimiento(),
                alumnos.containsKey(s.getAlumnoId()) ? alumnos.get(s.getAlumnoId()).nombreCompleto() : "—",
                gradoPorId.get(s.getGradoId()), anioPorId.get(s.getAnioEscolarId()), s.getFechaSolicitud(),
                s.getTipoEstudiante(), s.getEstado(), s.estadoVisible(), s.getObservacion())).toList();
    }

    // ---- Apoyo -------------------------------------------------------------------------------------------

    private ApoderadoResumen exigirApoderado() {
        return contexto.idUsuarioActual().flatMap(familias::apoderadoDeUsuario).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo un apoderado puede presentar solicitudes"));
    }

    private static String prefijo(String apoderadoId) {
        return "solicitudes/" + apoderadoId;
    }

    private ExcepcionNegocio enTramite(int anio) {
        return new ExcepcionNegocio(CodigoError.SOLICITUD_EN_TRAMITE,
                "Ya existe una solicitud en trámite con este DNI para " + anio);
    }

    /** FA-1: documentos obligatorios y válidos; el error lista cada campo faltante para resaltarlo. */
    private void validarDocumentos(NuevaSolicitud nueva, String apoderadoId) {
        List<Map<String, String>> errores = new ArrayList<>();
        Set<String> presentes = nueva.documentos().stream().map(DocumentoPresentado::tipo).collect(Collectors.toSet());
        for (String requerido : ReglasMatricula.documentosRequeridos(nueva.tipoEstudiante())) {
            if (!presentes.contains(requerido)) {
                errores.add(Map.of("campo", "documentos." + requerido, "mensaje", "Adjunta " + nombre(requerido)));
            }
        }
        if (!errores.isEmpty()) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "Faltan documentos obligatorios")
                    .con("errores", errores);
        }
        if (nueva.documentos().size() > MAXIMO_DOCUMENTOS) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                    "Se admiten hasta " + MAXIMO_DOCUMENTOS + " documentos");
        }
        for (DocumentoPresentado d : nueva.documentos()) {
            if (!ReglasMatricula.esTipoDeDocumento(d.tipo())) {
                throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "Tipo de documento desconocido: " + d.tipo());
            }
            ReglasArchivo.validar(d.contentType(), d.tamanoBytes());
            if (!d.claveObjeto().startsWith(prefijo(apoderadoId) + "/") || !archivos.existe(d.claveObjeto())) {
                throw new ExcepcionNegocio(CodigoError.ARCHIVO_INVALIDO,
                        "El archivo " + d.nombreArchivo() + " no se subió correctamente; súbelo de nuevo");
            }
        }
    }

    private static String nombre(String tipo) {
        return switch (tipo) {
            case "CERTIFICADO_ESTUDIOS" -> "el certificado de estudios";
            case "FICHA_MATRICULA_ANTERIOR" -> "la ficha de matrícula anterior";
            default -> "el documento";
        };
    }
}
