package pe.edu.gesedu.matricula.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.SeccionConVacantes;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.compartido.paginacion.Pagina;
import pe.edu.gesedu.compartido.web.Etags;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.AlumnoSolicitudDto;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.ApoderadoSolicitudDto;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.DetalleSolicitud;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.DocumentoSolicitudDto;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.ResultadoConfirmacion;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.ResultadoRechazo;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.SolicitudResumen;
import pe.edu.gesedu.matricula.aplicacion.MatriculaDtos.ValidacionMatriculaDto;
import pe.edu.gesedu.matricula.dominio.DocumentoSolicitud;
import pe.edu.gesedu.matricula.dominio.EstadoAdmision;
import pe.edu.gesedu.matricula.dominio.EstadoMatricula;
import pe.edu.gesedu.matricula.dominio.EstadoSolicitud;
import pe.edu.gesedu.matricula.dominio.HistorialSeccion;
import pe.edu.gesedu.matricula.dominio.Matricula;
import pe.edu.gesedu.matricula.dominio.MotivoRechazo;
import pe.edu.gesedu.matricula.dominio.ReglasMatricula;
import pe.edu.gesedu.matricula.dominio.ReglasMatricula.Validacion;
import pe.edu.gesedu.matricula.dominio.SolicitudMatricula;
import pe.edu.gesedu.matricula.dominio.TipoEstudiante;
import pe.edu.gesedu.matricula.infraestructura.DocumentoSolicitudRepository;
import pe.edu.gesedu.matricula.infraestructura.HistorialSeccionRepository;
import pe.edu.gesedu.matricula.infraestructura.MatriculaRepository;
import pe.edu.gesedu.matricula.infraestructura.SolicitudMatriculaRepository;
import pe.edu.gesedu.pagos.aplicacion.PlanDeCobros;
import pe.edu.gesedu.pagos.aplicacion.PlanDeCobros.LineaCosto;
import pe.edu.gesedu.pagos.aplicacion.PlanDeCobros.ResumenCostos;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias.AlumnoResumen;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias.ApoderadoResumen;
import pe.edu.gesedu.transversal.archivos.GestorArchivos;
import pe.edu.gesedu.transversal.archivos.GestorArchivos.UrlPrefirmada;
import pe.edu.gesedu.transversal.notificaciones.Notificacion;
import pe.edu.gesedu.transversal.notificaciones.Notificaciones;

/**
 * Evaluación de solicitudes de matrícula (UC-02, RF-04, RF-05, RF-07, RF-12, RNF-09, RNF-10). La confirmación es
 * una sola transacción: vacante condicional → matrícula CONFIRMADA → todas las obligaciones → solicitud APROBADA →
 * auditoría. El correo sale después del commit; si algo falla, no sale y nada queda a medias.
 */
@Service
public class EvaluacionMatriculaService {

    private final SolicitudMatriculaRepository solicitudes;
    private final DocumentoSolicitudRepository documentos;
    private final MatriculaRepository matriculas;
    private final CatalogoAcademicoService catalogo;
    private final AnioEscolarService anios;
    private final PlanDeCobros cobros;
    private final DirectorioFamilias familias;
    private final ContextoUsuario contexto;
    private final GestorArchivos archivos;
    private final Notificaciones notificaciones;
    private final Clock reloj;
    private final HistorialSeccionRepository historial;

    EvaluacionMatriculaService(SolicitudMatriculaRepository solicitudes, DocumentoSolicitudRepository documentos,
                               MatriculaRepository matriculas, CatalogoAcademicoService catalogo,
                               AnioEscolarService anios, PlanDeCobros cobros, DirectorioFamilias familias,
                               ContextoUsuario contexto, GestorArchivos archivos, Notificaciones notificaciones,
                               Clock reloj, HistorialSeccionRepository historial) {
        this.solicitudes = solicitudes;
        this.documentos = documentos;
        this.matriculas = matriculas;
        this.catalogo = catalogo;
        this.anios = anios;
        this.cobros = cobros;
        this.familias = familias;
        this.contexto = contexto;
        this.archivos = archivos;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
        this.historial = historial;
    }

    // ---- Consulta ----------------------------------------------------------------------------------------

    /** Bandeja paginada (paso 1), filtrable por estado y por texto (código, nombre, documento o código del alumno). */
    @Transactional(readOnly = true)
    public Pagina<SolicitudResumen> listar(EstadoSolicitud estado, String q, Pageable paginacion) {
        String texto = q == null ? "" : q.trim();
        List<String> alumnoIds = texto.isEmpty() ? List.of() : familias.buscarAlumnoIds(texto);
        Specification<SolicitudMatricula> filtro = (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (estado != null) {
                condiciones.add(cb.equal(raiz.get("estado"), estado));
            }
            if (!texto.isEmpty()) {
                Predicate porCodigo = cb.like(cb.lower(raiz.get("codigoSeguimiento")),
                        "%" + texto.toLowerCase().replace("%", "").replace("_", "") + "%");
                condiciones.add(alumnoIds.isEmpty() ? porCodigo : cb.or(porCodigo, raiz.get("alumnoId").in(alumnoIds)));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        var pagina = solicitudes.findAll(filtro, paginacion);
        Map<String, AlumnoResumen> alumnos = familias.alumnos(
                pagina.getContent().stream().map(SolicitudMatricula::getAlumnoId).distinct().toList());
        Map<String, String> grados = pagina.getContent().stream().map(SolicitudMatricula::getGradoId).distinct()
                .collect(Collectors.toMap(g -> g, g -> catalogo.grado(g).numeroNivel() + "°"));
        return Pagina.de(pagina, s -> new SolicitudResumen(s.getCodigoSeguimiento(),
                alumnos.containsKey(s.getAlumnoId()) ? alumnos.get(s.getAlumnoId()).nombreCompleto() : "—",
                grados.get(s.getGradoId()), s.getFechaSolicitud(), s.getTipoEstudiante(), s.getEstado(),
                s.getEstadoDocumentacion(), s.getMotivoRechazo(), estadoVisible(s)));
    }

    /** Detalle para evaluar (pasos 2 a 7). */
    @Transactional(readOnly = true)
    public DetalleSolicitud obtener(String codigo) {
        SolicitudMatricula s = buscar(codigo);
        int anio = anios.obtenerPorId(s.getAnioEscolarId()).anio();
        AlumnoResumen alumno = familias.alumnos(List.of(s.getAlumnoId())).get(s.getAlumnoId());
        ApoderadoResumen apoderado = familias.apoderados(List.of(s.getApoderadoId())).get(s.getApoderadoId());
        List<Validacion> validaciones = validar(s, anio);
        List<DocumentoSolicitudDto> docs = documentos.findBySolicitudIdOrderByCreatedAt(s.getId()).stream()
                .map(d -> new DocumentoSolicitudDto(d.getId(), d.getTipo(), d.getNombreArchivo(), d.getContentType(),
                        d.getTamanoBytes()))
                .toList();
        boolean nuevo = s.getTipoEstudiante() == TipoEstudiante.NUEVO_INGRESO;
        return new DetalleSolicitud(s.getCodigoSeguimiento(), String.valueOf(anio),
                catalogo.grado(s.getGradoId()).numeroNivel() + "°", s.getFechaSolicitud(), s.getTipoEstudiante(),
                s.getEstadoAdmision(), s.getEstadoDocumentacion(), s.getEstado(), s.getMotivoRechazo(),
                estadoVisible(s), s.getObservacion(),
                new AlumnoSolicitudDto(alumno.codigo(), alumno.nombreCompleto(), alumno.documento(), alumno.fechaNacimiento()),
                new ApoderadoSolicitudDto(apoderado.nombreCompleto(), apoderado.documento(), apoderado.correo(),
                        apoderado.telefono(), alumno.parentescoApoderado()),
                docs,
                validaciones.stream().map(v -> new ValidacionMatriculaDto(v.regla(), v.aprobada(), v.mensaje(), v.motivo())).toList(),
                s.estaPendiente() && ReglasMatricula.todasAprobadas(validaciones),
                catalogo.seccionesDelGrado(s.getGradoId()), cobros.resumen(s.getAnioEscolarId(), nuevo),
                s.getVersion());
    }

    /** URL prefirmada de 5 minutos para ver un documento (paso 2). */
    @Transactional(readOnly = true)
    public UrlPrefirmada urlDocumento(String codigo, String documentoId) {
        SolicitudMatricula s = buscar(codigo);
        DocumentoSolicitud d = documentos.findById(documentoId)
                .filter(doc -> doc.getSolicitudId().equals(s.getId()))
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO,
                        "La solicitud no tiene ese documento"));
        return archivos.urlDescarga(d.getClaveObjeto(), d.getContentType(), d.getNombreArchivo());
    }

    // ---- Evaluación --------------------------------------------------------------------------------------

    /** El administrador corrige el tipo de estudiante y registra el resultado de admisión (C-37, S-08). */
    @Transactional
    @Auditable(accion = "REGISTRAR_EVALUACION_MATRICULA", modulo = "MATRICULA", tipoEntidad = "SolicitudMatricula",
            idEntidad = "#result", descripcion = "Registro del tipo de estudiante y la admisión de una solicitud")
    public String registrarEvaluacion(String codigo, TipoEstudiante tipo, EstadoAdmision admision, String ifMatch) {
        SolicitudMatricula s = buscar(codigo);
        exigirPendiente(s);
        Etags.verificar(s.getVersion(), ifMatch);
        ContextoAuditoria.registrarCambio("tipoEstudiante", s.getTipoEstudiante(), tipo);
        ContextoAuditoria.registrarCambio("estadoAdmision", s.getEstadoAdmision(), admision);
        s.registrarEvaluacion(tipo, admision);
        return s.getId();
    }

    /**
     * Confirma la matrícula (pasos 9 a 13). {@code 422} si una validación falla (el administrador la rechaza con el
     * motivo que corresponda), {@code 409 SIN_VACANTE} si la sección se llenó, {@code 409 CONFLICTO_CONCURRENCIA}
     * si la solicitud cambió.
     */
    @Transactional
    @Auditable(accion = "CONFIRMAR_MATRICULA", modulo = "MATRICULA", tipoEntidad = "Matricula",
            idEntidad = "#result.matriculaId", descripcion = "Confirmación de matrícula y generación de compromisos de pago")
    public ResultadoConfirmacion confirmar(String codigo, String seccionId, boolean costosAceptados, String ifMatch) {
        SolicitudMatricula s = buscar(codigo);
        exigirPendiente(s);
        Etags.verificar(s.getVersion(), ifMatch);
        if (!costosAceptados) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "El apoderado debe aceptar los costos");
        }
        int anio = anios.obtenerPorId(s.getAnioEscolarId()).anio();
        exigirValidaciones(validar(s, anio));
        SeccionConVacantes seccion = catalogo.seccionesDelGrado(s.getGradoId()).stream()
                .filter(sec -> sec.id().equals(seccionId)).findFirst()
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA,
                        "La sección no pertenece al grado solicitado"));

        catalogo.reservarVacante(seccion.id());
        Matricula matricula;
        try {
            matricula = matriculas.saveAndFlush(new Matricula(s.getId(), s.getAlumnoId(), s.getAnioEscolarId(),
                    seccion.id(), LocalDate.now(reloj), contexto.idUsuarioActual().orElse(null)));
        } catch (DataIntegrityViolationException e) {
            throw new ExcepcionNegocio(CodigoError.MATRICULA_DUPLICADA);
        }
        historial.save(new HistorialSeccion(matricula.getId(), seccion.id(), matricula.getFechaMatricula()));
        boolean nuevo = s.getTipoEstudiante() == TipoEstudiante.NUEVO_INGRESO;
        List<LineaCosto> lineas = cobros.generarObligaciones(matricula.getId(), s.getAnioEscolarId(),
                s.getAlumnoId(), s.getApoderadoId(), nuevo);
        Dinero total = lineas.stream().map(LineaCosto::monto).reduce(Dinero.CERO, Dinero::sumar);
        int vacantes = catalogo.vacantes(seccion.id());

        ContextoAuditoria.registrarCambio("estado", EstadoSolicitud.PENDIENTE_REVISION, EstadoSolicitud.APROBADA);
        ContextoAuditoria.registrarCambio("seccion", null, seccion.nombre());
        ContextoAuditoria.registrarCambio("vacantes", seccion.vacantes(), vacantes);
        ContextoAuditoria.registrarCambio("compromisosGenerados", null, lineas.size());
        s.aprobar(contexto.idUsuarioActual().orElse(null));

        AlumnoResumen alumno = familias.alumnos(List.of(s.getAlumnoId())).get(s.getAlumnoId());
        notificarConfirmacion(s, anio, alumno, seccion.nombre(), lineas, total);
        return new ResultadoConfirmacion(matricula.getId(), s.getCodigoSeguimiento(), seccion.id(), seccion.nombre(),
                vacantes, lineas.size(), total);
    }

    /** Rechaza la solicitud (FA-1 a FA-5) con el motivo que las validaciones respaldan, o por desistimiento. */
    @Transactional
    @Auditable(accion = "RECHAZAR_SOLICITUD_MATRICULA", modulo = "MATRICULA", tipoEntidad = "SolicitudMatricula",
            idEntidad = "#result.solicitudId", descripcion = "Rechazo de una solicitud de matrícula")
    public ResultadoRechazo rechazar(String codigo, MotivoRechazo motivo, String observacion, String ifMatch) {
        SolicitudMatricula s = buscar(codigo);
        exigirPendiente(s);
        Etags.verificar(s.getVersion(), ifMatch);
        int anio = anios.obtenerPorId(s.getAnioEscolarId()).anio();
        List<Validacion> validaciones = validar(s, anio);
        verificarMotivo(motivo, validaciones, catalogo.seccionesDelGrado(s.getGradoId()));

        String nota = observacion == null || observacion.isBlank() ? null : observacion.trim();
        ContextoAuditoria.registrarCambio("estado", EstadoSolicitud.PENDIENTE_REVISION, EstadoSolicitud.RECHAZADA);
        ContextoAuditoria.registrarCambio("motivoRechazo", null, motivo);
        s.rechazar(motivo, nota, contexto.idUsuarioActual().orElse(null));

        AlumnoResumen alumno = familias.alumnos(List.of(s.getAlumnoId())).get(s.getAlumnoId());
        notificarRechazo(s, alumno, motivo, nota);
        return new ResultadoRechazo(s.getId(), s.getCodigoSeguimiento(), motivo, motivo.estadoVisible());
    }

    // ---- Apoyo -------------------------------------------------------------------------------------------

    private SolicitudMatricula buscar(String codigo) {
        return solicitudes.findByCodigoSeguimiento(codigo).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe la solicitud " + codigo));
    }

    private static void exigirPendiente(SolicitudMatricula s) {
        if (!s.estaPendiente()) {
            throw new ExcepcionNegocio(CodigoError.CONFLICTO_CONCURRENCIA,
                    "La solicitud ya fue evaluada: " + estadoVisible(s));
        }
    }

    private List<Validacion> validar(SolicitudMatricula s, int anio) {
        boolean activa = matriculas.existsByAlumnoIdAndAnioEscolarIdAndEstado(s.getAlumnoId(), s.getAnioEscolarId(),
                EstadoMatricula.CONFIRMADA);
        Dinero deuda = s.getTipoEstudiante() == TipoEstudiante.RECURRENTE
                ? cobros.deudaVencidaDeAniosAnteriores(s.getAlumnoId(), anio) : Dinero.CERO;
        return ReglasMatricula.evaluar(s.getTipoEstudiante(), s.getEstadoDocumentacion(), s.getEstadoAdmision(),
                activa, deuda);
    }

    /** Primera validación fallida → su código HTTP (FA-1 a FA-3). */
    private static void exigirValidaciones(List<Validacion> validaciones) {
        for (Validacion v : validaciones) {
            if (v.aprobada()) {
                continue;
            }
            CodigoError codigo = switch (v.regla()) {
                case DOCUMENTACION -> CodigoError.DOCUMENTACION_INCOMPLETA;
                case ADMISION -> CodigoError.NO_ADMITIDO;
                case MATRICULA_ACTIVA -> CodigoError.MATRICULA_DUPLICADA;
                case DEUDAS -> CodigoError.DEUDA_VENCIDA;
            };
            ExcepcionNegocio error = new ExcepcionNegocio(codigo, v.mensaje());
            throw v.motivo() == null ? error : error.con("motivoSugerido", v.motivo());
        }
    }

    private static void verificarMotivo(MotivoRechazo motivo, List<Validacion> validaciones,
                                        List<SeccionConVacantes> secciones) {
        boolean respaldado = switch (motivo) {
            case VOLUNTAD_APODERADO -> true;
            case SIN_VACANTE -> secciones.stream().noneMatch(sec -> sec.vacantes() > 0);
            default -> validaciones.stream().anyMatch(v -> !v.aprobada() && v.motivo() == motivo);
        };
        if (!respaldado) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA,
                    "El motivo " + motivo + " no coincide con las validaciones de la solicitud");
        }
    }

    private static String estadoVisible(SolicitudMatricula s) {
        return s.estadoVisible();
    }

    private void notificarConfirmacion(SolicitudMatricula s, int anio, AlumnoResumen alumno, String seccion,
                                       List<LineaCosto> lineas, Dinero total) {
        ApoderadoResumen apoderado = familias.apoderados(List.of(s.getApoderadoId())).get(s.getApoderadoId());
        if (apoderado == null || apoderado.correo() == null) {
            return;
        }
        String cronograma = lineas.stream()
                .map(l -> nombreConcepto(l.concepto()) + " " + l.periodo()
                        + " — vence " + l.fechaVencimiento() + " — S/ " + l.monto())
                .collect(Collectors.joining("\n"));
        notificaciones.notificar(new Notificacion(apoderado.correo(),
                "Matrícula confirmada " + anio + " — " + alumno.nombreCompleto(), "matricula-confirmada", Map.of(
                        "nombre", apoderado.nombreCompleto(),
                        "alumno", alumno.nombreCompleto(),
                        "codigo", s.getCodigoSeguimiento(),
                        "anio", anio,
                        "seccion", seccion,
                        "total", total.toString(),
                        "cronograma", cronograma)));
    }

    private static String nombreConcepto(String concepto) {
        return switch (concepto) {
            case "MATRICULA" -> "Matrícula";
            case "PENSION" -> "Pensión";
            case "CUOTA_INGRESO" -> "Cuota de ingreso";
            default -> concepto;
        };
    }

    private void notificarRechazo(SolicitudMatricula s, AlumnoResumen alumno, MotivoRechazo motivo, String nota) {
        ApoderadoResumen apoderado = familias.apoderados(List.of(s.getApoderadoId())).get(s.getApoderadoId());
        if (apoderado == null || apoderado.correo() == null) {
            return;
        }
        String explicacion = switch (motivo) {
            case NO_ADMITIDO -> "El resultado del proceso de admisión no fue favorable.";
            case DUPLICIDAD -> "El alumno ya cuenta con una matrícula activa en el año escolar.";
            case DEUDAS -> "El alumno tiene deudas vencidas de años anteriores por S/ "
                    + cobros.deudaVencidaDeAniosAnteriores(s.getAlumnoId(),
                            anios.obtenerPorId(s.getAnioEscolarId()).anio())
                    + ". Regulariza el pago para volver a solicitar la matrícula.";
            case SIN_VACANTE -> "No quedan vacantes en el grado solicitado.";
            case VOLUNTAD_APODERADO -> "Se registró el desistimiento a solicitud del apoderado.";
        };
        notificaciones.notificar(new Notificacion(apoderado.correo(),
                "Solicitud de matrícula " + s.getCodigoSeguimiento() + ": " + motivo.estadoVisible(),
                "matricula-rechazada", Map.of(
                        "nombre", apoderado.nombreCompleto(),
                        "alumno", alumno.nombreCompleto(),
                        "codigo", s.getCodigoSeguimiento(),
                        "estado", motivo.estadoVisible(),
                        "detalle", nota == null ? explicacion : explicacion + "\nObservación: " + nota)));
    }
}
