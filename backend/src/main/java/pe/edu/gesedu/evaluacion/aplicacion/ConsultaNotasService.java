package pe.edu.gesedu.evaluacion.aplicacion;

import java.text.Normalizer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.BimestreResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.CompetenciaResumen;
import pe.edu.gesedu.compartido.dominio.NivelLogro;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.AlumnoNotasDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.ArchivoNotas;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.BimestreConsolidadoDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.CursoNotasDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.CursosAlumnoDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.LibretaDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.LibretaResumenDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsultaNotasDtos.NotaCompetenciaDto;
import pe.edu.gesedu.evaluacion.dominio.AsignacionDocente;
import pe.edu.gesedu.evaluacion.dominio.Libreta;
import pe.edu.gesedu.evaluacion.dominio.NotaConsolidada;
import pe.edu.gesedu.evaluacion.infraestructura.AsignacionDocenteRepository;
import pe.edu.gesedu.evaluacion.infraestructura.LibretaRepository;
import pe.edu.gesedu.evaluacion.infraestructura.NotaConsolidadaRepository;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService.SeccionDeAlumno;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias.AlumnoResumen;
import pe.edu.gesedu.transversal.reportes.GeneradorPdf;

/**
 * UC-19, Consultar notas y libreta (RF-30, RF-32 a RF-37). El alumno ve sus cursos —los de su sección— y el apoderado los de
 * cada hijo (con varios hijos elige uno, FA-1), con las competencias, el nivel esperado y las notas consolidadas por bimestre;
 * descarga el informe de progreso de un bimestre o la libreta completa (FA-2). Solo se muestra lo ya consolidado (S-72). El
 * alcance es «yo» o «mis hijos»: otro alumno responde 403. Los años anteriores se consultan con su libreta.
 */
@Service
public class ConsultaNotasService {

    private static final List<String> ROMANOS = List.of("", "I", "II", "III", "IV");

    private final AsignacionDocenteRepository asignaciones;
    private final NotaConsolidadaRepository notas;
    private final LibretaRepository libretas;
    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final ConsultaMatriculaService matriculas;
    private final DirectorioFamilias familias;
    private final DirectorioDocentes directorio;
    private final ContextoUsuario contexto;
    private final GeneradorPdf pdf;
    private final Clock reloj;

    ConsultaNotasService(AsignacionDocenteRepository asignaciones, NotaConsolidadaRepository notas, LibretaRepository libretas,
                         AnioEscolarService anios, CatalogoAcademicoService catalogo, ConsultaMatriculaService matriculas,
                         DirectorioFamilias familias, DirectorioDocentes directorio, ContextoUsuario contexto, GeneradorPdf pdf, Clock reloj) {
        this.asignaciones = asignaciones;
        this.notas = notas;
        this.libretas = libretas;
        this.anios = anios;
        this.catalogo = catalogo;
        this.matriculas = matriculas;
        this.familias = familias;
        this.directorio = directorio;
        this.contexto = contexto;
        this.pdf = pdf;
        this.reloj = reloj;
    }

    // ---- alcance ----

    /** Los alumnos que el usuario puede consultar: él mismo (alumno) o sus hijos (apoderado), con su sección vigente. */
    @Transactional(readOnly = true)
    public List<AlumnoNotasDto> misAlumnos() {
        List<AlumnoResumen> alcance = alcance();
        Map<String, SeccionDeAlumno> secciones = matriculas.seccionesDeMiAlcance().stream()
                .collect(Collectors.toMap(SeccionDeAlumno::alumnoId, Function.identity(), (a, b) -> a));
        return alcance.stream().map(a -> {
            SeccionDeAlumno s = secciones.get(a.id());
            Integer anio = s == null ? null : anios.obtenerPorId(catalogo.anioEscolarDe(s.seccionId())).anio();
            return new AlumnoNotasDto(a.id(), a.codigo(), a.nombreCompleto(), s == null ? null : s.seccion(), anio);
        }).sorted(Comparator.comparing(AlumnoNotasDto::nombre)).toList();
    }

    private List<AlumnoResumen> alcance() {
        String usuarioId = contexto.idUsuarioActual().orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO));
        return familias.apoderadoDeUsuario(usuarioId).map(ap -> familias.hijosDe(ap.id()))
                .or(() -> familias.alumnoDeUsuario(usuarioId).map(List::of)).orElse(List.of());
    }

    /** El alumno debe ser el propio usuario o su hijo; si no, queda fuera de su alcance (403). */
    private AlumnoResumen alumnoEnAlcance(String alumnoId) {
        return alcance().stream().filter(a -> a.id().equals(alumnoId)).findFirst().orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo puedes consultar las notas del alumno o de tus hijos"));
    }

    // ---- cursos (pasos 2 a 5) ----

    /**
     * Pasos 2 a 5: los cursos del alumno con sus competencias y sus notas consolidadas. {@code anio} nulo = el de su
     * matrícula vigente o, sin ella, el último con libreta; {@code bimestre} nulo = el último consolidado.
     */
    @Transactional(readOnly = true)
    public CursosAlumnoDto cursos(String alumnoId, Integer anio, Integer bimestre) {
        AlumnoResumen alumno = alumnoEnAlcance(alumnoId);
        List<Libreta> deAlumno = libretas.findByAlumnoId(alumnoId);
        Optional<AnioEscolarResumen> elegido = elegirAnio(alumno, anio, deAlumno);
        List<Integer> aniosDisponibles = aniosDisponibles(alumno, deAlumno, elegido);
        if (elegido.isEmpty()) {
            return new CursosAlumnoDto(alumno.id(), alumno.nombreCompleto(), alumno.codigo(), null, null, aniosDisponibles, List.of(), 1,
                    null, List.of());
        }
        Contenido c = contenido(alumno, elegido.get());
        int seleccionado = bimestre != null ? bimestre : c.consolidados().isEmpty() ? 1 : c.consolidados().last();
        List<BimestreConsolidadoDto> estados = c.bimestres().stream()
                .map(b -> new BimestreConsolidadoDto(b.numero(), c.consolidados().contains(b.numero()))).toList();
        String estadoLibreta = deAlumno.stream().filter(l -> l.getAnioEscolarId().equals(elegido.get().id())).map(Libreta::getEstado).findFirst().orElse(null);
        return new CursosAlumnoDto(alumno.id(), alumno.nombreCompleto(), alumno.codigo(), elegido.get().anio(), c.seccion(), aniosDisponibles,
                estados, seleccionado, estadoLibreta, c.cursos());
    }

    /** El detalle de un curso: sus competencias con el nivel esperado y las notas de cada bimestre (RF-34). */
    @Transactional(readOnly = true)
    public CursoNotasDto curso(String alumnoId, String cursoId, Integer anio) {
        AlumnoResumen alumno = alumnoEnAlcance(alumnoId);
        List<Libreta> deAlumno = libretas.findByAlumnoId(alumnoId);
        AnioEscolarResumen elegido = elegirAnio(alumno, anio, deAlumno).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "El alumno no tiene cursos en ningún año"));
        return contenido(alumno, elegido).cursos().stream().filter(c -> c.cursoId().equals(cursoId)).findFirst().orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "El curso no pertenece a la sección del alumno"));
    }

    // ---- libreta (pasos 6 y 7, FA-2) ----

    /** Los años con libreta del alumno, del más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public List<LibretaResumenDto> libretas(String alumnoId) {
        alumnoEnAlcance(alumnoId);
        return libretas.findByAlumnoId(alumnoId).stream()
                .map(l -> new LibretaResumenDto(anios.obtenerPorId(l.getAnioEscolarId()).anio(), l.getEstado(),
                        l.getFechaActualizacion().atZone(reloj.getZone()).toOffsetDateTime()))
                .sorted(Comparator.comparingInt(LibretaResumenDto::anio).reversed()).toList();
    }

    /**
     * La libreta completa del año o, con {@code hastaBimestre}, el informe de progreso de ese periodo. Sin ninguna nota
     * consolidada: 422 {@code SIN_NOTAS_CONSOLIDADAS}.
     */
    @Transactional(readOnly = true)
    public LibretaDto libreta(String alumnoId, Integer anio, Integer hastaBimestre) {
        AlumnoResumen alumno = alumnoEnAlcance(alumnoId);
        List<Libreta> deAlumno = libretas.findByAlumnoId(alumnoId);
        AnioEscolarResumen elegido = elegirAnio(alumno, anio, deAlumno).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.SIN_NOTAS_CONSOLIDADAS, "Aún no hay notas consolidadas para mostrar."));
        Contenido c = contenido(alumno, elegido);
        Libreta libreta = deAlumno.stream().filter(l -> l.getAnioEscolarId().equals(elegido.id())).findFirst().orElse(null);
        List<Integer> incluidos = c.consolidados().stream().filter(b -> hastaBimestre == null || b <= hastaBimestre).toList();
        // El informe de un bimestre solo existe cuando ese bimestre ya está consolidado
        if (libreta == null || incluidos.isEmpty() || (hastaBimestre != null && !c.consolidados().contains(hastaBimestre))) {
            throw new ExcepcionNegocio(CodigoError.SIN_NOTAS_CONSOLIDADAS, hastaBimestre == null
                    ? "Aún no hay notas consolidadas en la libreta de " + elegido.anio() + "."
                    : "El Bimestre " + romano(hastaBimestre) + " aún no está consolidado: no hay informe de progreso.");
        }
        List<CursoNotasDto> cursos = c.cursos().stream().map(curso -> new CursoNotasDto(curso.cursoId(), curso.curso(), curso.docente(),
                curso.competencias().stream().map(n -> {
                    Map<Integer, NivelLogro> porBimestre = new TreeMap<>();
                    n.bimestres().forEach((numero, nivel) -> {
                        if (hastaBimestre == null || numero <= hastaBimestre) {
                            porBimestre.put(numero, nivel);
                        }
                    });
                    return new NotaCompetenciaDto(n.competenciaId(), n.codigo(), n.nombre(), n.nivelEsperado(), porBimestre,
                            hastaBimestre == null ? n.anual() : null);
                }).toList())).toList();
        return new LibretaDto(alumno.id(), alumno.nombreCompleto(), alumno.codigo(), c.seccion(), elegido.anio(), libreta.getEstado(),
                libreta.getFechaActualizacion().atZone(reloj.getZone()).toOffsetDateTime(), hastaBimestre, incluidos, cursos);
    }

    /** El PDF de la libreta o del informe de progreso (RF-36). */
    @Transactional(readOnly = true)
    public ArchivoNotas libretaPdf(String alumnoId, Integer anio, Integer hastaBimestre) {
        LibretaDto l = libreta(alumnoId, anio, hastaBimestre);
        boolean informe = hastaBimestre != null;
        String titulo = (informe ? "Informe de progreso · Bimestre " + romano(hastaBimestre) : "Libreta de notas") + " · " + l.anio();
        List<String> resumen = List.of(
                "Alumno: " + l.alumno() + " (" + l.codigo() + ")",
                "Sección: " + l.seccion() + " · Año escolar " + l.anio(),
                "Libreta " + (l.estado().equals("FINAL") ? "final" : "parcial") + ", actualizada el "
                        + l.fechaActualizacion().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                "Escala: AD logro destacado · A logro esperado · B en proceso · C en inicio. Solo figuran las notas ya consolidadas.");
        List<String> encabezados = new ArrayList<>(List.of("Curso", "Docente", "Competencia", "Nivel esperado"));
        List<Integer> columnas = l.bimestres().isEmpty() ? List.of() : new TreeSet<>(l.bimestres()).stream().toList();
        columnas.forEach(b -> encabezados.add("Bim. " + romano(b)));
        if (!informe) {
            encabezados.add("Final");
        }
        List<List<Object>> filas = new ArrayList<>();
        for (CursoNotasDto curso : l.cursos()) {
            for (NotaCompetenciaDto n : curso.competencias()) {
                List<Object> fila = new ArrayList<>(List.of(curso.curso(), curso.docente(), n.codigo() + " · " + n.nombre(),
                        n.nivelEsperado() == null ? "—" : n.nivelEsperado()));
                columnas.forEach(b -> fila.add(n.bimestres().get(b) == null ? "—" : n.bimestres().get(b).name()));
                if (!informe) {
                    fila.add(n.anual() == null ? "—" : n.anual().name());
                }
                filas.add(fila);
            }
        }
        String nombre = (informe ? "informe-progreso-b" + hastaBimestre : "libreta") + "-" + l.codigo() + "-" + l.anio() + ".pdf";
        return new ArchivoNotas(pdf.generar(titulo, resumen, encabezados, filas), GeneradorPdf.TIPO_CONTENIDO, nombre);
    }

    // ---- armado ----

    /** Lo ya consolidado del alumno en un año, armado por curso. */
    private record Contenido(String seccion, List<BimestreResumen> bimestres, TreeSet<Integer> consolidados, List<CursoNotasDto> cursos) {
    }

    private Contenido contenido(AlumnoResumen alumno, AnioEscolarResumen anio) {
        List<BimestreResumen> bimestres = anios.bimestres(anio.id());
        Map<String, Integer> numeroPorId = bimestres.stream().collect(Collectors.toMap(BimestreResumen::id, BimestreResumen::numero));
        List<NotaConsolidada> delAlumno = notas.findByAlumnoIdAndAnioEscolarId(alumno.id(), anio.id());
        Optional<String> seccionId = matriculas.seccionDeAlumnoEnAnio(alumno.id(), anio.id());
        // Los cursos de la sección del año; si no hay matrícula en ese año, los que aparecen en sus notas
        List<AsignacionDocente> delCurso = seccionId.map(s -> asignaciones.findBySeccionIdIn(List.of(s))).orElseGet(() -> {
            Set<String> ids = delAlumno.stream().map(NotaConsolidada::getAsignacionId).collect(Collectors.toCollection(LinkedHashSet::new));
            return ids.isEmpty() ? List.<AsignacionDocente>of() : asignaciones.findAllById(ids);
        });
        Map<String, String> nombresCurso = catalogo.cursos(anio.id()).stream().collect(Collectors.toMap(c -> c.id(), c -> c.nombre()));
        Map<String, List<CompetenciaResumen>> competencias = catalogo.competenciasDe(
                delCurso.stream().map(AsignacionDocente::getCursoId).collect(Collectors.toSet()));
        Map<String, DocenteResumen> docentes = directorio.porIds(delCurso.stream().map(AsignacionDocente::getDocenteId).collect(Collectors.toSet()));

        Map<String, Map<Integer, NivelLogro>> bimestrales = new HashMap<>();
        Map<String, NivelLogro> anuales = new HashMap<>();
        TreeSet<Integer> consolidados = new TreeSet<>();
        for (NotaConsolidada n : delAlumno) {
            if (NotaConsolidada.ANUAL.equals(n.getTipo())) {
                anuales.put(n.getCompetenciaId(), n.getNivelLogro());
            } else {
                Integer numero = numeroPorId.get(n.getBimestreId());
                if (numero != null) {
                    bimestrales.computeIfAbsent(n.getCompetenciaId(), k -> new TreeMap<>()).put(numero, n.getNivelLogro());
                    consolidados.add(numero);
                }
            }
        }
        List<CursoNotasDto> cursos = delCurso.stream().map(a -> {
            DocenteResumen docente = docentes.get(a.getDocenteId());
            List<NotaCompetenciaDto> lista = competencias.getOrDefault(a.getCursoId(), List.of()).stream()
                    .map(k -> new NotaCompetenciaDto(k.id(), k.codigo(), k.nombre(), k.nivelEsperado(),
                            bimestrales.getOrDefault(k.id(), new TreeMap<>()), anuales.get(k.id()))).toList();
            return new CursoNotasDto(a.getCursoId(), nombresCurso.getOrDefault(a.getCursoId(), "—"),
                    docente == null ? "—" : docente.nombreCompleto(), lista);
        }).sorted(Comparator.comparing(c -> sinTildes(c.curso()))).toList();
        String seccion = seccionId.map(s -> catalogo.seccion(s).nombre()).orElse(null);
        return new Contenido(seccion, bimestres, consolidados, cursos);
    }

    /** El año pedido; sin pedir, el de la matrícula vigente o el de la libreta más reciente. */
    private Optional<AnioEscolarResumen> elegirAnio(AlumnoResumen alumno, Integer anio, List<Libreta> deAlumno) {
        if (anio != null) {
            return Optional.of(anios.obtenerPorAnio(anio));
        }
        Optional<AnioEscolarResumen> vigente = matriculas.seccionesDeMiAlcance().stream().filter(s -> s.alumnoId().equals(alumno.id()))
                .findFirst().map(s -> anios.obtenerPorId(catalogo.anioEscolarDe(s.seccionId())));
        if (vigente.isPresent()) {
            return vigente;
        }
        return deAlumno.stream().map(l -> anios.obtenerPorId(l.getAnioEscolarId())).max(Comparator.comparingInt(AnioEscolarResumen::anio));
    }

    private List<Integer> aniosDisponibles(AlumnoResumen alumno, List<Libreta> deAlumno, Optional<AnioEscolarResumen> elegido) {
        TreeSet<Integer> lista = new TreeSet<>(Comparator.reverseOrder());
        deAlumno.forEach(l -> lista.add(anios.obtenerPorId(l.getAnioEscolarId()).anio()));
        matriculas.seccionesDeMiAlcance().stream().filter(s -> s.alumnoId().equals(alumno.id()))
                .forEach(s -> lista.add(anios.obtenerPorId(catalogo.anioEscolarDe(s.seccionId())).anio()));
        elegido.ifPresent(a -> lista.add(a.anio()));
        return List.copyOf(lista);
    }

    private static String romano(int numero) {
        return numero >= 1 && numero < ROMANOS.size() ? ROMANOS.get(numero) : String.valueOf(numero);
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }
}
