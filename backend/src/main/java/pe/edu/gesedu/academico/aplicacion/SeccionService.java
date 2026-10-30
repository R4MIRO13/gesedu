package pe.edu.gesedu.academico.aplicacion;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.SeccionesDtos.DocenteTutorDto;
import pe.edu.gesedu.academico.aplicacion.SeccionesDtos.GradoSeccionesDto;
import pe.edu.gesedu.academico.aplicacion.SeccionesDtos.GradosAnioDto;
import pe.edu.gesedu.academico.aplicacion.SeccionesDtos.SeccionConfiguracionDto;
import pe.edu.gesedu.academico.dominio.AnioEscolar;
import pe.edu.gesedu.academico.dominio.Grado;
import pe.edu.gesedu.academico.dominio.Seccion;
import pe.edu.gesedu.academico.infraestructura.AnioEscolarRepository;
import pe.edu.gesedu.academico.infraestructura.GradoRepository;
import pe.edu.gesedu.academico.infraestructura.SeccionRepository;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.compartido.web.Etags;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;

/**
 * Secciones de cada grado con su capacidad, sus vacantes y su tutor (RF-03, RF-04). Las vacantes las mueve la
 * matrícula con {@code UPDATE} condicionales; aquí solo cambia la capacidad, y lo hace también con un
 * {@code UPDATE} condicional para no pisar una matrícula que ocurra al mismo tiempo (RNF-09).
 */
@Service
public class SeccionService {

    private final AnioEscolarRepository anios;
    private final GradoRepository grados;
    private final SeccionRepository secciones;
    private final DirectorioDocentes docentes;

    SeccionService(AnioEscolarRepository anios, GradoRepository grados, SeccionRepository secciones,
                   DirectorioDocentes docentes) {
        this.anios = anios;
        this.grados = grados;
        this.secciones = secciones;
        this.docentes = docentes;
    }

    @Transactional(readOnly = true)
    public GradosAnioDto listar(int anio) {
        AnioEscolar a = anios.findByAnio(anio).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el año escolar " + anio));
        List<Grado> losGrados = grados.findByAnioEscolarId(a.getId()).stream()
                .sorted(Comparator.comparingInt(Grado::getNumeroNivel)).toList();
        List<Seccion> lasSecciones = secciones.findByAnioEscolarId(a.getId());
        Map<String, DocenteResumen> todos = docentes.todos().stream()
                .collect(Collectors.toMap(DocenteResumen::id, d -> d));
        Map<String, List<Seccion>> porGrado = lasSecciones.stream().collect(Collectors.groupingBy(Seccion::getGradoId));

        List<GradoSeccionesDto> dtos = losGrados.stream().map(g -> new GradoSeccionesDto(g.getId(), g.getNumeroNivel(),
                porGrado.getOrDefault(g.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(Seccion::getLetra))
                        .map(s -> dto(s, g.getNumeroNivel(), todos))
                        .toList())).toList();
        List<DocenteTutorDto> candidatos = todos.values().stream()
                .sorted(Comparator.comparing(d -> d.apellidos() + " " + d.nombres()))
                .map(d -> new DocenteTutorDto(d.id(), d.nombreCompleto())).toList();
        return new GradosAnioDto(a.getAnio(), a.getEstado(), dtos, candidatos);
    }

    @Transactional(readOnly = true)
    public SeccionConfiguracionDto obtener(String seccionId) {
        Seccion s = seccion(seccionId);
        Grado g = grados.findById(s.getGradoId()).orElseThrow();
        return dto(s, g.getNumeroNivel(), docentesDe(s.getTutorDocenteId()));
    }

    /** Agrega una sección al grado, con todas sus vacantes libres. */
    @Transactional
    @Auditable(accion = "CREAR_SECCION", modulo = "ACADEMICO", tipoEntidad = "Seccion", idEntidad = "#result.id()")
    public SeccionConfiguracionDto crear(String gradoId, String letra, int capacidad, String tutorDocenteId) {
        Grado grado = grados.findById(gradoId).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el grado"));
        abierto(grado.getAnioEscolarId());
        String letraNormal = letra.trim().toUpperCase();
        Map<String, DocenteResumen> tutor = validarTutor(tutorDocenteId);
        if (secciones.findByGradoIdOrderByLetra(gradoId).stream().anyMatch(s -> s.getLetra().equals(letraNormal))) {
            throw duplicada(grado, letraNormal);
        }
        Seccion nueva = new Seccion(gradoId, grado.getAnioEscolarId(), letraNormal, capacidad, tutorDocenteId);
        try {
            secciones.saveAndFlush(nueva);
        } catch (DataIntegrityViolationException e) {
            throw causa(e, grado, letraNormal);
        }
        ContextoAuditoria.registrarCambio("sección", null, grado.getNumeroNivel() + "° " + letraNormal);
        ContextoAuditoria.registrarCambio("capacidad", null, capacidad);
        if (tutorDocenteId != null) {
            ContextoAuditoria.registrarCambio("tutor", null, tutor.get(tutorDocenteId).nombreCompleto());
        }
        return dto(nueva, grado.getNumeroNivel(), tutor);
    }

    /**
     * Cambia la capacidad y/o el tutor (RF-04). La capacidad no puede quedar por debajo de los matriculados; las
     * vacantes se ajustan en la misma sentencia. Exige la versión de la sección en {@code If-Match}.
     */
    @Transactional
    @Auditable(accion = "MODIFICAR_SECCION", modulo = "ACADEMICO", tipoEntidad = "Seccion", idEntidad = "#seccionId")
    public SeccionConfiguracionDto modificar(String seccionId, int capacidad, String tutorDocenteId, String ifMatch) {
        long version = Etags.leerVersion(ifMatch);
        Seccion actual = seccion(seccionId);
        abierto(actual.getAnioEscolarId());
        Grado grado = grados.findById(actual.getGradoId()).orElseThrow();
        Map<String, DocenteResumen> tutores = validarTutor(tutorDocenteId);
        Map<String, DocenteResumen> anteriores = docentesDe(actual.getTutorDocenteId());
        int capacidadAntes = actual.getCapacidad();
        int vacantesAntes = actual.getVacantesDisponibles();
        String tutorAntes = actual.getTutorDocenteId();
        long versionAntes = actual.getVersion();

        int filas;
        try {
            filas = secciones.actualizarConfiguracion(seccionId, capacidad, tutorDocenteId, version);
        } catch (DataIntegrityViolationException e) {
            throw causa(e, grado, actual.getLetra());
        }
        if (filas == 0) {
            // Se vuelve a leer para saber por qué: otra persona cambió la sección, o ya hay más matriculados
            Seccion fresca = seccion(seccionId);
            if (fresca.getVersion() != version) {
                throw new ExcepcionNegocio(CodigoError.CONFLICTO_CONCURRENCIA,
                        "La sección cambió mientras la editabas; recárgala");
            }
            throw capacidadInsuficiente(fresca.getCapacidad() - fresca.getVacantesDisponibles());
        }
        secciones.flush();
        Seccion nueva = seccion(seccionId);
        if (versionAntes == nueva.getVersion()) {
            throw new ExcepcionNegocio(CodigoError.ERROR_INTERNO, "No se pudo leer la sección actualizada");
        }
        if (capacidadAntes != capacidad) {
            ContextoAuditoria.registrarCambio("capacidad", capacidadAntes, capacidad);
            ContextoAuditoria.registrarCambio("vacantes", vacantesAntes, nueva.getVacantesDisponibles());
        }
        if (!java.util.Objects.equals(tutorAntes, tutorDocenteId)) {
            ContextoAuditoria.registrarCambio("tutor",
                    tutorAntes == null ? null : nombre(anteriores, tutorAntes),
                    tutorDocenteId == null ? null : nombre(tutores, tutorDocenteId));
        }
        return dto(nueva, grado.getNumeroNivel(), tutorDocenteId == null ? Map.of() : tutores);
    }

    private static String nombre(Map<String, DocenteResumen> mapa, String id) {
        DocenteResumen d = mapa.get(id);
        return d == null ? id : d.nombreCompleto();
    }

    private Map<String, DocenteResumen> validarTutor(String tutorDocenteId) {
        if (tutorDocenteId == null) {
            return Map.of();
        }
        Map<String, DocenteResumen> encontrado = docentes.porIds(Set.of(tutorDocenteId));
        if (!encontrado.containsKey(tutorDocenteId)) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "El docente elegido como tutor no existe");
        }
        return encontrado;
    }

    private Map<String, DocenteResumen> docentesDe(String tutorDocenteId) {
        return tutorDocenteId == null ? Map.of() : docentes.porIds(Set.of(tutorDocenteId));
    }

    private ExcepcionNegocio causa(DataIntegrityViolationException e, Grado grado, String letra) {
        String mensaje = String.valueOf(e.getMostSpecificCause().getMessage());
        if (mensaje.contains("ux_seccion_tutor")) {
            return new ExcepcionNegocio(CodigoError.TUTOR_YA_ASIGNADO);
        }
        if (mensaje.contains("ux_seccion_letra")) {
            return duplicada(grado, letra);
        }
        return new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "Los datos de la sección no son válidos");
    }

    private static ExcepcionNegocio duplicada(Grado grado, String letra) {
        return new ExcepcionNegocio(CodigoError.SECCION_DUPLICADA,
                "Ya existe la sección " + grado.getNumeroNivel() + "° " + letra);
    }

    private static ExcepcionNegocio capacidadInsuficiente(int matriculados) {
        return new ExcepcionNegocio(CodigoError.CAPACIDAD_INSUFICIENTE,
                "La capacidad no puede ser menor que los " + matriculados + " estudiantes ya matriculados");
    }

    private Seccion seccion(String id) {
        return secciones.findById(id).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe la sección"));
    }

    private void abierto(String anioId) {
        AnioEscolar a = anios.findById(anioId).orElseThrow();
        if ("CERRADO".equals(a.getEstado())) {
            throw new ExcepcionNegocio(CodigoError.ANIO_CERRADO);
        }
    }

    private static SeccionConfiguracionDto dto(Seccion s, int nivel, Map<String, DocenteResumen> tutores) {
        DocenteResumen tutor = s.getTutorDocenteId() == null ? null : tutores.get(s.getTutorDocenteId());
        return new SeccionConfiguracionDto(s.getId(), s.getGradoId(), s.getLetra(), nivel + "° " + s.getLetra(), s.getCapacidad(),
                s.getVacantesDisponibles(), s.getCapacidad() - s.getVacantesDisponibles(), s.getTutorDocenteId(),
                tutor == null ? null : tutor.nombreCompleto(), s.getVersion());
    }
}
