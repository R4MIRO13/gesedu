package pe.edu.gesedu.evaluacion.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.BimestreResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.CompetenciaResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.CursoResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.SeccionResumen;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.dominio.NivelLogro;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.AsignacionCursoDto;
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.BimestreLogrosDto;
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.CompetenciaCursoDto;
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.FilaAlumnoDto;
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.IncompletoDto;
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.ItemLogro;
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.RejillaDto;
import pe.edu.gesedu.evaluacion.dominio.AsignacionDocente;
import pe.edu.gesedu.evaluacion.dominio.ConsolidacionBimestral;
import pe.edu.gesedu.evaluacion.dominio.RegistroLogro;
import pe.edu.gesedu.evaluacion.infraestructura.AsignacionDocenteRepository;
import pe.edu.gesedu.evaluacion.infraestructura.ConsolidacionBimestralRepository;
import pe.edu.gesedu.evaluacion.infraestructura.RegistroLogroRepository;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService.AlumnoDeSeccion;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;

/**
 * UC-15, Registrar evaluación por competencias (RF-22, RF-31). El docente califica con AD, A, B o C a cada alumno de su
 * sección en cada competencia de su curso, en el bimestre vigente (S-67). Hay un logro por (alumno, competencia,
 * bimestre): guardar de nuevo la celda la actualiza. El guardado es atómico (si algo es inválido no se guarda nada) y
 * se serializa por asignación. Editar un bimestre ya consolidado marca la consolidación como desactualizada (S-68).
 * Solo el docente de la asignación puede ver y registrar su rejilla.
 */
@Service
public class RegistroLogrosService {

    private final AsignacionDocenteRepository asignaciones;
    private final RegistroLogroRepository registros;
    private final ConsolidacionBimestralRepository consolidaciones;
    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final ConsultaMatriculaService matriculas;
    private final DirectorioDocentes directorio;
    private final ContextoUsuario contexto;
    private final Clock reloj;

    RegistroLogrosService(AsignacionDocenteRepository asignaciones, RegistroLogroRepository registros,
                          ConsolidacionBimestralRepository consolidaciones, AnioEscolarService anios,
                          CatalogoAcademicoService catalogo, ConsultaMatriculaService matriculas,
                          DirectorioDocentes directorio, ContextoUsuario contexto, Clock reloj) {
        this.asignaciones = asignaciones;
        this.registros = registros;
        this.consolidaciones = consolidaciones;
        this.anios = anios;
        this.catalogo = catalogo;
        this.matriculas = matriculas;
        this.directorio = directorio;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    /** Todo lo necesario para armar o validar la rejilla de una asignación. */
    private record Contexto(AsignacionDocente asignacion, SeccionResumen seccion, String curso, AnioEscolarResumen anio,
                            List<BimestreResumen> bimestres, BimestreResumen vigente, List<CompetenciaResumen> competencias,
                            List<AlumnoDeSeccion> alumnos, LocalDate hoy) {
    }

    /** RF-31: los cursos y secciones que el docente imparte este año (el año ACTIVO). */
    @Transactional(readOnly = true)
    public List<AsignacionCursoDto> misAsignaciones() {
        DocenteResumen docente = docenteActual();
        Optional<AnioEscolarResumen> activo = anios.anioActivo();
        if (activo.isEmpty()) {
            return List.of();
        }
        Map<String, SeccionResumen> secciones = catalogo.secciones(activo.get().id()).stream()
                .collect(Collectors.toMap(SeccionResumen::id, Function.identity()));
        List<AsignacionDocente> propias = asignaciones.findByDocenteId(docente.id()).stream()
                .filter(a -> secciones.containsKey(a.getSeccionId())).toList();
        Set<String> cursoIds = propias.stream().map(AsignacionDocente::getCursoId).collect(Collectors.toSet());
        Map<String, List<CompetenciaResumen>> competencias = catalogo.competenciasDe(cursoIds);
        Map<String, CursoResumen> cursos = new HashMap<>();
        cursoIds.forEach(id -> cursos.put(id, catalogo.curso(id)));
        LocalDate hoy = reloj.instant().atZone(reloj.getZone()).toLocalDate();
        Integer vigente = anios.bimestres(activo.get().id()).stream().filter(b -> b.vigenteEn(hoy))
                .map(BimestreResumen::numero).findFirst().orElse(null);
        return propias.stream().map(a -> {
            SeccionResumen s = secciones.get(a.getSeccionId());
            CursoResumen curso = cursos.get(a.getCursoId());
            return new AsignacionCursoDto(a.getId(), a.getCursoId(), curso.nombre(), curso.slug(), s.id(), s.nombre(),
                    s.numeroGrado() + s.letra(), activo.get().anio(), a.getReglaConsolidacion(),
                    matriculas.alumnosDeSeccion(s.id()).size(), competencias.getOrDefault(a.getCursoId(), List.of()).size(),
                    vigente);
        }).sorted(Comparator.comparing(AsignacionCursoDto::curso).thenComparing(AsignacionCursoDto::seccion)).toList();
    }

    /** Pasos 1 y 2: la rejilla de la asignación en el bimestre pedido (por omisión, el vigente). */
    @Transactional(readOnly = true)
    public RejillaDto consultar(String asignacionId, Integer numeroBimestre) {
        AsignacionDocente asignacion = asignaciones.findById(asignacionId).orElse(null);
        Contexto c = resolver(asignacion);
        BimestreResumen bimestre = elegir(c, numeroBimestre);
        return armar(c, bimestre, 0);
    }

    /**
     * Pasos 3 a 7: guarda las celdas indicadas, o ninguna. Un nivel fuera de AD, A, B o C: 422 con la lista (FA-1);
     * un alumno o una competencia que no corresponden, o una celda repetida: 400. Fuera del bimestre vigente: 422
     * BIMESTRE_NO_VIGENTE. Si el registro de la sección queda incompleto, se guarda igual y la respuesta trae el aviso
     * y los alumnos pendientes (FA-2).
     */
    @Transactional
    @Auditable(accion = "REGISTRAR_LOGROS", modulo = "EVALUACION", tipoEntidad = "RegistroLogro",
            idEntidad = "#asignacionId", descripcion = "Registro de logros por competencia", condicion = "#result.cambios() > 0")
    public RejillaDto guardar(String asignacionId, Integer numeroBimestre, List<ItemLogro> items) {
        // Se bloquea la asignación: dos guardados de la misma rejilla se ejecutan uno tras otro
        AsignacionDocente asignacion = asignaciones.buscarBloqueando(asignacionId).orElse(null);
        Contexto c = resolver(asignacion);
        if (c.vigente() == null) {
            throw new ExcepcionNegocio(CodigoError.BIMESTRE_NO_VIGENTE,
                    "Hoy no hay un bimestre vigente: el registro de logros no está habilitado.");
        }
        if (numeroBimestre != null && numeroBimestre != c.vigente().numero()) {
            throw new ExcepcionNegocio(CodigoError.BIMESTRE_NO_VIGENTE, "El Bimestre " + romano(numeroBimestre)
                    + " no está vigente: solo se registran logros del Bimestre " + romano(c.vigente().numero()) + ".");
        }
        if (items == null || items.isEmpty()) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "No hay logros para guardar")
                    .con("errores", List.of(Map.of("campo", "logros", "mensaje", "Indica al menos un logro")));
        }
        Map<String, NivelLogro> niveles = validar(c, items);

        BimestreResumen bimestre = c.vigente();
        Map<String, RegistroLogro> existentes = registros.findByAsignacionIdAndBimestreId(asignacionId, bimestre.id()).stream()
                .collect(Collectors.toMap(r -> celda(r.getAlumnoId(), r.getCompetenciaId()), Function.identity()));
        Map<String, AlumnoDeSeccion> alumnos = c.alumnos().stream()
                .collect(Collectors.toMap(AlumnoDeSeccion::alumnoId, Function.identity()));
        Map<String, CompetenciaResumen> competencias = c.competencias().stream()
                .collect(Collectors.toMap(CompetenciaResumen::id, Function.identity()));
        Instant ahora = reloj.instant();
        int cambios = 0;
        for (ItemLogro item : items) {
            String clave = celda(item.alumnoId(), item.competenciaId());
            NivelLogro nuevo = niveles.get(clave);
            RegistroLogro existente = existentes.get(clave);
            String campo = "B" + bimestre.numero() + " " + alumnos.get(item.alumnoId()).codigo() + " "
                    + competencias.get(item.competenciaId()).codigo();
            if (existente == null) {
                registros.save(new RegistroLogro(asignacionId, item.alumnoId(), item.competenciaId(), bimestre.id(), nuevo, ahora));
                ContextoAuditoria.registrarCambio(campo, null, nuevo);
                cambios++;
            } else {
                NivelLogro anterior = existente.getNivelLogro();
                if (existente.cambiarNivel(nuevo, ahora)) {
                    ContextoAuditoria.registrarCambio(campo, anterior, nuevo);
                    cambios++;
                }
            }
        }
        if (cambios > 0) {
            consolidaciones.findByBimestreId(bimestre.id()).ifPresent(consolidacion -> {
                String antes = consolidacion.getEstado();
                if (consolidacion.marcarDesactualizada()) {
                    ContextoAuditoria.registrarCambio("consolidacion.bimestre" + bimestre.numero(), antes,
                            ConsolidacionBimestral.DESACTUALIZADA);
                }
            });
        }
        registros.flush();
        return armar(c, bimestre, cambios);
    }

    // ---- validación ----

    /** Niveles por celda ya validados. Estructura (400) primero; después la escala (422, FA-1). */
    private Map<String, NivelLogro> validar(Contexto c, List<ItemLogro> items) {
        Set<String> alumnos = c.alumnos().stream().map(AlumnoDeSeccion::alumnoId).collect(Collectors.toSet());
        Set<String> competencias = c.competencias().stream().map(CompetenciaResumen::id).collect(Collectors.toSet());
        List<Map<String, String>> estructura = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        for (ItemLogro item : items) {
            String campo = "logros." + item.alumnoId() + "." + item.competenciaId();
            if (item.alumnoId() == null || !alumnos.contains(item.alumnoId())) {
                estructura.add(Map.of("campo", campo, "mensaje", "El alumno no pertenece a la sección"));
            } else if (item.competenciaId() == null || !competencias.contains(item.competenciaId())) {
                estructura.add(Map.of("campo", campo, "mensaje", "La competencia no pertenece al curso"));
            } else if (!vistas.add(celda(item.alumnoId(), item.competenciaId()))) {
                estructura.add(Map.of("campo", campo, "mensaje", "La celda aparece más de una vez"));
            }
        }
        if (!estructura.isEmpty()) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                    "El registro contiene celdas que no corresponden: no se guardó nada").con("errores", estructura);
        }
        Map<String, NivelLogro> niveles = new LinkedHashMap<>();
        List<Map<String, String>> invalidos = new ArrayList<>();
        for (ItemLogro item : items) {
            NivelLogro nivel = nivelDe(item.nivel());
            if (nivel == null) {
                invalidos.add(Map.of("campo", "logros." + item.alumnoId() + "." + item.competenciaId(),
                        "mensaje", "El logro no es válido: usa AD, A, B o C"));
            } else {
                niveles.put(celda(item.alumnoId(), item.competenciaId()), nivel);
            }
        }
        if (!invalidos.isEmpty()) {
            throw new ExcepcionNegocio(CodigoError.NIVEL_LOGRO_INVALIDO,
                    "Hay " + invalidos.size() + (invalidos.size() == 1 ? " logro" : " logros")
                            + " que no pertenecen a la escala AD, A, B o C: no se guardó nada").con("errores", invalidos);
        }
        return niveles;
    }

    /** La escala literal oficial, exacta: AD, A, B o C. */
    private static NivelLogro nivelDe(String texto) {
        if (texto == null) {
            return null;
        }
        for (NivelLogro nivel : NivelLogro.values()) {
            if (nivel.name().equals(texto)) {
                return nivel;
            }
        }
        return null;
    }

    // ---- contexto y armado ----

    /** El alcance: solo el docente de la asignación. Una asignación ajena o inexistente responde igual (403). */
    private Contexto resolver(AsignacionDocente asignacion) {
        DocenteResumen docente = docenteActual();
        if (asignacion == null || !docente.id().equals(asignacion.getDocenteId())) {
            throw new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo el docente del curso en la sección registra sus logros");
        }
        SeccionResumen seccion = catalogo.seccion(asignacion.getSeccionId());
        AnioEscolarResumen anio = anios.obtenerPorId(catalogo.anioEscolarDe(seccion.id()));
        List<BimestreResumen> bimestres = anios.bimestres(anio.id());
        LocalDate hoy = reloj.instant().atZone(reloj.getZone()).toLocalDate();
        BimestreResumen vigente = bimestres.stream().filter(b -> b.vigenteEn(hoy)).findFirst().orElse(null);
        List<CompetenciaResumen> competencias = catalogo.competenciasDe(List.of(asignacion.getCursoId()))
                .getOrDefault(asignacion.getCursoId(), List.of());
        return new Contexto(asignacion, seccion, catalogo.curso(asignacion.getCursoId()).nombre(), anio, bimestres, vigente,
                competencias, matriculas.alumnosDeSeccion(seccion.id()), hoy);
    }

    /** El bimestre pedido; sin pedir, el vigente, o el último que ya empezó, o el primero. */
    private BimestreResumen elegir(Contexto c, Integer numero) {
        if (numero != null) {
            return c.bimestres().stream().filter(b -> b.numero() == numero).findFirst().orElseThrow(() ->
                    new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el Bimestre " + numero + " del año escolar"));
        }
        if (c.vigente() != null) {
            return c.vigente();
        }
        return c.bimestres().stream().filter(b -> !b.fechaInicio().isAfter(c.hoy()))
                .max(Comparator.comparingInt(BimestreResumen::numero))
                .orElseGet(() -> c.bimestres().stream().findFirst().orElseThrow(() ->
                        new ExcepcionNegocio(CodigoError.CALENDARIO_NO_DISPONIBLE, "El año escolar no tiene bimestres")));
    }

    private RejillaDto armar(Contexto c, BimestreResumen bimestre, int cambios) {
        Map<String, NivelLogro> niveles = new HashMap<>();
        for (RegistroLogro r : registros.findByAsignacionIdAndBimestreId(c.asignacion().getId(), bimestre.id())) {
            niveles.put(celda(r.getAlumnoId(), r.getCompetenciaId()), r.getNivelLogro());
        }
        List<FilaAlumnoDto> filas = new ArrayList<>();
        List<IncompletoDto> incompletos = new ArrayList<>();
        int completas = 0;
        for (AlumnoDeSeccion alumno : c.alumnos()) {
            Map<String, NivelLogro> logros = new LinkedHashMap<>();
            List<String> faltan = new ArrayList<>();
            for (CompetenciaResumen competencia : c.competencias()) {
                NivelLogro nivel = niveles.get(celda(alumno.alumnoId(), competencia.id()));
                if (nivel == null) {
                    faltan.add(competencia.codigo());
                } else {
                    logros.put(competencia.id(), nivel);
                    completas++;
                }
            }
            filas.add(new FilaAlumnoDto(alumno.alumnoId(), alumno.codigo(), alumno.apellidos(), alumno.nombres(), logros));
            if (!faltan.isEmpty()) {
                incompletos.add(new IncompletoDto(alumno.alumnoId(), alumno.apellidos() + " " + alumno.nombres(), faltan));
            }
        }
        boolean editable = bimestre.vigenteEn(c.hoy());
        String mensaje = editable ? null : bimestre.fechaFin().isBefore(c.hoy())
                ? "El Bimestre " + romano(bimestre.numero()) + " ya terminó: solo puedes consultar sus logros."
                : "El Bimestre " + romano(bimestre.numero()) + " aún no empieza: todavía no se registran logros.";
        Optional<ConsolidacionBimestral> consolidacion = consolidaciones.findByBimestreId(bimestre.id());
        String estado = consolidacion.map(ConsolidacionBimestral::getEstado).orElse(null);
        String avisoConsolidacion = consolidacion.filter(ConsolidacionBimestral::estaDesactualizada)
                .map(x -> "Se modificaron logros después de consolidar el Bimestre " + romano(bimestre.numero())
                        + ": hay que volver a consolidarlo.").orElse(null);
        String aviso = incompletos.isEmpty() || c.alumnos().isEmpty() ? null
                : "Hay " + incompletos.size() + (incompletos.size() == 1 ? " alumno" : " alumnos")
                        + " con competencias sin calificar";
        List<BimestreLogrosDto> lista = c.bimestres().stream()
                .map(b -> new BimestreLogrosDto(b.numero(), b.fechaInicio(), b.fechaFin(), b.vigenteEn(c.hoy()))).toList();
        return new RejillaDto(c.asignacion().getId(), c.curso(), c.seccion().nombre(), c.anio().anio(),
                new BimestreLogrosDto(bimestre.numero(), bimestre.fechaInicio(), bimestre.fechaFin(), editable), lista, editable,
                mensaje, estado, avisoConsolidacion,
                c.competencias().stream().map(x -> new CompetenciaCursoDto(x.id(), x.codigo(), x.nombre(), x.nivelEsperado())).toList(),
                filas, c.alumnos().size() * c.competencias().size(), completas, incompletos, aviso, cambios);
    }

    private static String celda(String alumnoId, String competenciaId) {
        return alumnoId + "|" + competenciaId;
    }

    private static String romano(int numero) {
        return switch (numero) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(numero);
        };
    }

    private DocenteResumen docenteActual() {
        String usuarioId = contexto.idUsuarioActual().orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO));
        return directorio.docenteDeUsuario(usuarioId).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo un docente registra logros por competencia"));
    }
}
