package pe.edu.gesedu.evaluacion.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.SeccionResumen;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.dominio.NivelLogro;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.evaluacion.aplicacion.ConsolidacionBimestralDtos.AvanceDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsolidacionBimestralDtos.BimestreAvanceDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsolidacionBimestralDtos.CursoAvanceDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsolidacionBimestralDtos.EstadoBimestreDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsolidacionBimestralDtos.ResultadoDto;
import pe.edu.gesedu.evaluacion.aplicacion.ConsolidacionBimestralDtos.TotalesAvanceDto;
import pe.edu.gesedu.evaluacion.aplicacion.NotasFinalesDtos.CalculoDto;
import pe.edu.gesedu.evaluacion.dominio.AsignacionDocente;
import pe.edu.gesedu.evaluacion.dominio.ConsolidacionBimestral;
import pe.edu.gesedu.evaluacion.dominio.Libreta;
import pe.edu.gesedu.evaluacion.dominio.LogroPonderado;
import pe.edu.gesedu.evaluacion.dominio.NotaConsolidada;
import pe.edu.gesedu.evaluacion.dominio.PesoBimestre;
import pe.edu.gesedu.evaluacion.dominio.RegistroLogro;
import pe.edu.gesedu.evaluacion.infraestructura.AsignacionDocenteRepository;
import pe.edu.gesedu.evaluacion.infraestructura.BloqueosEvaluacion;
import pe.edu.gesedu.evaluacion.infraestructura.ConsolidacionBimestralRepository;
import pe.edu.gesedu.evaluacion.infraestructura.LibretaRepository;
import pe.edu.gesedu.evaluacion.infraestructura.NotaConsolidadaRepository;
import pe.edu.gesedu.evaluacion.infraestructura.PesoBimestreRepository;
import pe.edu.gesedu.evaluacion.infraestructura.RegistroLogroRepository;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService.AlumnoDeSeccion;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;

/**
 * UC-18, Consolidar notas bimestrales del alumno (RF-27, RF-28, RF-35, RF-37). El administrador ve el avance de las notas de
 * cada curso en cada sección y, cuando todas están completas, consolida el bimestre: la nota bimestral de cada alumno y
 * competencia (la regla de su curso aplicada al logro del bimestre) pasa a su libreta. En el último bimestre calcula además
 * las notas anuales de todos los cursos y deja la libreta FINAL (FA-1, C-26) y exige que los bimestres anteriores estén
 * consolidados y al día. Con notas pendientes se bloquea con la lista de docentes (FA-2). Reconsolidar reemplaza las notas
 * del bimestre, nunca las duplica. Puede hacerse después de la fecha de fin del bimestre (S-69).
 */
@Service
public class ConsolidacionBimestralService {

    static final String SIN_CONSOLIDAR = "SIN_CONSOLIDAR";

    private final AsignacionDocenteRepository asignaciones;
    private final RegistroLogroRepository registros;
    private final NotaConsolidadaRepository notas;
    private final PesoBimestreRepository pesos;
    private final ConsolidacionBimestralRepository consolidaciones;
    private final LibretaRepository libretas;
    private final BloqueosEvaluacion bloqueos;
    private final ConsolidacionService consolidacion;
    private final CalculadorNotaAnual calculador;
    private final RegistroNotasAnuales registroAnual;
    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final ConsultaMatriculaService matriculas;
    private final DirectorioDocentes directorio;
    private final ContextoUsuario contexto;
    private final Clock reloj;

    ConsolidacionBimestralService(AsignacionDocenteRepository asignaciones, RegistroLogroRepository registros, NotaConsolidadaRepository notas,
                                  PesoBimestreRepository pesos, ConsolidacionBimestralRepository consolidaciones, LibretaRepository libretas,
                                  BloqueosEvaluacion bloqueos, ConsolidacionService consolidacion, CalculadorNotaAnual calculador,
                                  RegistroNotasAnuales registroAnual, AnioEscolarService anios, CatalogoAcademicoService catalogo,
                                  ConsultaMatriculaService matriculas, DirectorioDocentes directorio, ContextoUsuario contexto, Clock reloj) {
        this.asignaciones = asignaciones;
        this.registros = registros;
        this.notas = notas;
        this.pesos = pesos;
        this.consolidaciones = consolidaciones;
        this.libretas = libretas;
        this.bloqueos = bloqueos;
        this.consolidacion = consolidacion;
        this.calculador = calculador;
        this.registroAnual = registroAnual;
        this.anios = anios;
        this.catalogo = catalogo;
        this.matriculas = matriculas;
        this.directorio = directorio;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    /** Todo lo necesario para evaluar y consolidar un bimestre de un año. */
    private record Datos(AnioEscolarResumen anio, List<BimestreResumen> bimestres, BimestreResumen bimestre, boolean ultimo,
                         List<AsignacionDocente> asignaciones, Map<String, SeccionResumen> secciones, Map<String, String> cursos,
                         Map<String, List<CompetenciaResumen>> competencias, Map<String, List<AlumnoDeSeccion>> alumnos,
                         Map<String, DocenteResumen> docentes) {
    }

    // ---- avance (pasos 1 a 3) ----

    /** Paso 2: el avance de las notas del bimestre por curso y sección, y si ya se puede consolidar. */
    @Transactional(readOnly = true)
    public AvanceDto avance(int anio, int numero) {
        Datos d = cargar(anio, numero);
        return armarAvance(d, logrosPorAsignacion(d), null);
    }

    // ---- consolidación (pasos 3 y 4) ----

    /**
     * Consolida el bimestre. Con cursos incompletos: 422 {@code BIMESTRE_INCOMPLETO} con la lista de docentes pendientes
     * (FA-2); el último bimestre con anteriores sin consolidar o desactualizados: 422 {@code BIMESTRES_ANTERIORES_PENDIENTES};
     * año cerrado: 422. Todo ocurre en una transacción con el año bloqueado.
     */
    @Transactional
    @Auditable(accion = "CONSOLIDAR_BIMESTRE", modulo = "EVALUACION", tipoEntidad = "ConsolidacionBimestral",
            idEntidad = "#anio + '/' + #numero", descripcion = "Consolidación de las notas de un bimestre")
    public AvanceDto consolidar(int anio, int numero) {
        AnioEscolarResumen resumen = anios.obtenerPorAnio(anio);
        if ("CERRADO".equals(resumen.estado())) {
            throw new ExcepcionNegocio(CodigoError.ANIO_CERRADO);
        }
        // Dos consolidaciones del mismo año se ejecutan una tras otra; y se bloquean las asignaciones, así una edición de
        // logros que llegue durante la consolidación espera y después ve la constancia (y la marca como desactualizada)
        bloqueos.bloquearPorClave("consolidacion:" + resumen.id());
        Datos d = cargar(anio, numero);
        asignaciones.bloquearTodas(d.asignaciones().stream().map(AsignacionDocente::getId).toList());

        Map<String, List<RegistroLogro>> logros = logrosPorAsignacion(d);
        AvanceDto antes = armarAvance(d, logros, null);
        List<CursoAvanceDto> incompletos = antes.cursos().stream().filter(c -> !c.completo()).toList();
        if (!incompletos.isEmpty()) {
            throw new ExcepcionNegocio(CodigoError.BIMESTRE_INCOMPLETO, "No se puede consolidar el Bimestre " + romano(numero) + ": "
                    + incompletos.size() + (incompletos.size() == 1 ? " sección incompleta" : " secciones incompletas"))
                    .con("pendientes", incompletos).con("docentes", antes.docentesPendientes());
        }
        if (d.ultimo()) {
            List<EstadoBimestreDto> pendientes = antes.bimestres().stream()
                    .filter(b -> b.numero() != numero && !ConsolidacionBimestral.COMPLETADA.equals(b.estado())).toList();
            if (!pendientes.isEmpty()) {
                throw new ExcepcionNegocio(CodigoError.BIMESTRES_ANTERIORES_PENDIENTES,
                        "No se puede consolidar el último bimestre: hay bimestres anteriores sin consolidar o desactualizados")
                        .con("bimestres", pendientes);
            }
        }

        Instant ahora = reloj.instant();
        Map<String, Libreta> libretasPorAlumno = libretasDelAnio(d, ahora);
        Map<String, String> idsLibreta = new HashMap<>();
        libretasPorAlumno.forEach((alumno, libreta) -> idsLibreta.put(alumno, libreta.getId()));

        int creadas = 0;
        int actualizadas = 0;
        int sinCambios = 0;
        Map<String, NotaConsolidada> existentes = notas.findByAnioEscolarIdAndBimestreId(d.anio().id(), d.bimestre().id()).stream()
                .collect(Collectors.toMap(n -> CalculadorNotaAnual.clave(n.getAlumnoId(), n.getCompetenciaId()), Function.identity()));
        for (AsignacionDocente a : d.asignaciones()) {
            Map<String, NivelLogro> nivelesDelBimestre = logros.getOrDefault(a.getId(), List.of()).stream()
                    .collect(Collectors.toMap(l -> CalculadorNotaAnual.clave(l.getAlumnoId(), l.getCompetenciaId()), RegistroLogro::getNivelLogro, (x, y) -> x));
            for (AlumnoDeSeccion alumno : d.alumnos().get(a.getSeccionId())) {
                for (CompetenciaResumen competencia : d.competencias().getOrDefault(a.getCursoId(), List.of())) {
                    String celda = CalculadorNotaAnual.clave(alumno.alumnoId(), competencia.id());
                    NivelLogro logro = nivelesDelBimestre.get(celda);
                    if (logro == null) {
                        continue;
                    }
                    // RF-27: la regla del curso aplicada a los logros del bimestre (S-66: hoy, uno)
                    NivelLogro nivel = consolidacion.consolidar(a.tipoRegla(), List.of(LogroPonderado.de(logro, numero))).orElseThrow();
                    NotaConsolidada existente = existentes.get(celda);
                    String libreta = idsLibreta.get(alumno.alumnoId());
                    if (existente == null) {
                        NotaConsolidada nueva = NotaConsolidada.bimestral(alumno.alumnoId(), competencia.id(), a.getId(), d.anio().id(),
                                d.bimestre().id(), nivel, ahora);
                        nueva.vincularLibreta(libreta);
                        notas.save(nueva);
                        creadas++;
                    } else {
                        existente.vincularLibreta(libreta);
                        if (existente.reemplazar(nivel, ahora)) {
                            actualizadas++;
                        } else {
                            sinCambios++;
                        }
                    }
                }
            }
        }

        CalculoDto anuales = null;
        if (d.ultimo()) {
            anuales = calcularAnuales(d, idsLibreta, ahora);
        }
        boolean finalizada = d.ultimo();
        int libretasAlDia = 0;
        for (Libreta libreta : libretasPorAlumno.values()) {
            // Una libreta que ya es FINAL no vuelve a parcial si se reconsolida un bimestre anterior
            libreta.actualizar(finalizada || Libreta.FINAL.equals(libreta.getEstado()), ahora);
            libretasAlDia++;
        }

        ConsolidacionBimestral constancia = consolidaciones.findByBimestreId(d.bimestre().id()).orElse(null);
        String estadoAnterior = constancia == null ? SIN_CONSOLIDAR : constancia.getEstado();
        String usuarioId = contexto.idUsuarioActual().orElse(null);
        if (constancia == null) {
            consolidaciones.save(new ConsolidacionBimestral(d.bimestre().id(), ahora, usuarioId));
        } else {
            constancia.reconsolidar(ahora, usuarioId);
        }
        ContextoAuditoria.registrarCambio("bimestre " + numero, estadoAnterior, ConsolidacionBimestral.COMPLETADA);
        ContextoAuditoria.registrarCambio("notas.bimestrales", null,
                creadas + " nuevas, " + actualizadas + " actualizadas, " + sinCambios + " sin cambios");
        ContextoAuditoria.registrarCambio("libretas", null, libretasAlDia + (finalizada ? " finales" : " al día"));
        if (anuales != null) {
            ContextoAuditoria.registrarCambio("notas.anuales", null,
                    anuales.creadas() + " nuevas, " + anuales.actualizadas() + " actualizadas, " + anuales.sinCambios() + " sin cambios");
        }
        notas.flush();
        return armarAvance(d, logros, new ResultadoDto(creadas, actualizadas, sinCambios, libretasAlDia, finalizada, anuales));
    }

    /** FA-1: las notas anuales de todas las asignaciones del año, con el mismo guardado que el cálculo del docente (UC-17). */
    private CalculoDto calcularAnuales(Datos d, Map<String, String> idsLibreta, Instant ahora) {
        Map<String, Integer> numeroPorId = d.bimestres().stream().collect(Collectors.toMap(BimestreResumen::id, BimestreResumen::numero));
        int creadas = 0;
        int actualizadas = 0;
        int sinCambios = 0;
        int sinNota = 0;
        for (AsignacionDocente a : d.asignaciones()) {
            Map<String, BigDecimal> pesosDeLaAsignacion = pesos.findByAsignacionId(a.getId()).stream()
                    .collect(Collectors.toMap(PesoBimestre::getBimestreId, PesoBimestre::getPeso));
            Map<String, NivelLogro> esperadas = calculador.calcular(a.tipoRegla(), registros.findByAsignacionId(a.getId()), numeroPorId,
                    pesosDeLaAsignacion);
            CalculoDto r = registroAnual.guardar(a, d.anio().id(), d.alumnos().get(a.getSeccionId()),
                    d.competencias().getOrDefault(a.getCursoId(), List.of()), esperadas, idsLibreta, ahora);
            creadas += r.creadas();
            actualizadas += r.actualizadas();
            sinCambios += r.sinCambios();
            sinNota += r.sinNota();
        }
        return new CalculoDto(creadas, actualizadas, sinCambios, sinNota);
    }

    /** Una libreta por alumno del año con asignaciones; crea las que faltan. */
    private Map<String, Libreta> libretasDelAnio(Datos d, Instant ahora) {
        Map<String, Libreta> porAlumno = libretas.findByAnioEscolarId(d.anio().id()).stream()
                .collect(Collectors.toMap(Libreta::getAlumnoId, Function.identity()));
        Set<String> alumnos = new LinkedHashSet<>();
        d.alumnos().values().forEach(lista -> lista.forEach(a -> alumnos.add(a.alumnoId())));
        Map<String, Libreta> resultado = new HashMap<>();
        for (String alumnoId : alumnos) {
            Libreta libreta = porAlumno.get(alumnoId);
            if (libreta == null) {
                libreta = libretas.save(new Libreta(alumnoId, d.anio().id(), ahora));
            }
            resultado.put(alumnoId, libreta);
        }
        return resultado;
    }

    // ---- armado ----

    private Datos cargar(int anio, int numero) {
        AnioEscolarResumen resumen = anios.obtenerPorAnio(anio);
        List<BimestreResumen> bimestres = anios.bimestres(resumen.id());
        BimestreResumen bimestre = bimestres.stream().filter(b -> b.numero() == numero).findFirst().orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el Bimestre " + numero + " del año escolar " + anio));
        boolean ultimo = bimestres.stream().mapToInt(BimestreResumen::numero).max().orElse(numero) == numero;
        Map<String, SeccionResumen> secciones = catalogo.secciones(resumen.id()).stream()
                .collect(Collectors.toMap(SeccionResumen::id, Function.identity()));
        List<AsignacionDocente> lista = secciones.isEmpty() ? List.of() : asignaciones.findBySeccionIdIn(secciones.keySet()).stream()
                .sorted(Comparator.comparing(AsignacionDocente::getId)).toList();
        Map<String, String> cursos = catalogo.cursos(resumen.id()).stream()
                .collect(Collectors.toMap(c -> c.id(), c -> c.nombre()));
        Set<String> cursoIds = lista.stream().map(AsignacionDocente::getCursoId).collect(Collectors.toSet());
        Map<String, List<CompetenciaResumen>> competencias = catalogo.competenciasDe(cursoIds);
        Map<String, List<AlumnoDeSeccion>> alumnos = new HashMap<>();
        for (AsignacionDocente a : lista) {
            alumnos.computeIfAbsent(a.getSeccionId(), matriculas::alumnosDeSeccion);
        }
        Map<String, DocenteResumen> docentes = directorio.porIds(lista.stream().map(AsignacionDocente::getDocenteId).collect(Collectors.toSet()));
        return new Datos(resumen, bimestres, bimestre, ultimo, lista, secciones, cursos, competencias, alumnos, docentes);
    }

    private Map<String, List<RegistroLogro>> logrosPorAsignacion(Datos d) {
        return registros.findByBimestreId(d.bimestre().id()).stream().collect(Collectors.groupingBy(RegistroLogro::getAsignacionId));
    }

    private AvanceDto armarAvance(Datos d, Map<String, List<RegistroLogro>> logros, ResultadoDto resultado) {
        List<CursoAvanceDto> cursos = new ArrayList<>();
        for (AsignacionDocente a : d.asignaciones()) {
            List<AlumnoDeSeccion> alumnos = d.alumnos().get(a.getSeccionId());
            List<CompetenciaResumen> competencias = d.competencias().getOrDefault(a.getCursoId(), List.of());
            Set<String> alumnoIds = alumnos.stream().map(AlumnoDeSeccion::alumnoId).collect(Collectors.toSet());
            Set<String> competenciaIds = competencias.stream().map(CompetenciaResumen::id).collect(Collectors.toSet());
            Set<String> hechas = new HashSet<>();
            for (RegistroLogro l : logros.getOrDefault(a.getId(), List.of())) {
                if (alumnoIds.contains(l.getAlumnoId()) && competenciaIds.contains(l.getCompetenciaId())) {
                    hechas.add(CalculadorNotaAnual.clave(l.getAlumnoId(), l.getCompetenciaId()));
                }
            }
            int total = alumnos.size() * competencias.size();
            int completas = hechas.size();
            DocenteResumen docente = d.docentes().get(a.getDocenteId());
            cursos.add(new CursoAvanceDto(a.getId(), d.cursos().getOrDefault(a.getCursoId(), "—"), d.secciones().get(a.getSeccionId()).nombre(),
                    docente == null ? "—" : docente.nombreCompleto(), total, completas,
                    total == 0 ? 100 : completas * 100 / total, completas == total));
        }
        cursos.sort(Comparator.comparing(CursoAvanceDto::completo).thenComparing(CursoAvanceDto::porcentaje)
                .thenComparing(CursoAvanceDto::curso).thenComparing(CursoAvanceDto::seccion));
        List<CursoAvanceDto> pendientes = cursos.stream().filter(c -> !c.completo()).toList();
        List<String> docentesPendientes = pendientes.stream().map(CursoAvanceDto::docente).distinct().sorted().toList();

        List<EstadoBimestreDto> estados = new ArrayList<>();
        ConsolidacionBimestral propia = null;
        for (BimestreResumen b : d.bimestres()) {
            Optional<ConsolidacionBimestral> constancia = consolidaciones.findByBimestreId(b.id());
            estados.add(new EstadoBimestreDto(b.numero(), constancia.map(ConsolidacionBimestral::getEstado).orElse(SIN_CONSOLIDAR)));
            if (b.numero() == d.bimestre().numero()) {
                propia = constancia.orElse(null);
            }
        }
        boolean cerrado = "CERRADO".equals(d.anio().estado());
        boolean anterioresAlDia = !d.ultimo() || estados.stream().filter(e -> e.numero() != d.bimestre().numero())
                .allMatch(e -> ConsolidacionBimestral.COMPLETADA.equals(e.estado()));
        String motivo = cerrado ? "El año escolar está cerrado: ya no se consolida."
                : !pendientes.isEmpty() ? "No se puede consolidar el Bimestre " + romano(d.bimestre().numero()) + ": " + pendientes.size()
                        + (pendientes.size() == 1 ? " sección incompleta" : " secciones incompletas")
                : !anterioresAlDia ? "Antes del último bimestre hay que consolidar los bimestres anteriores y dejarlos al día."
                : null;
        return new AvanceDto(d.anio().anio(), new BimestreAvanceDto(d.bimestre().numero(), d.bimestre().fechaInicio(), d.bimestre().fechaFin(), d.ultimo()),
                estados, propia == null ? SIN_CONSOLIDAR : propia.getEstado(),
                propia == null ? null : propia.getFechaConsolidacion().atZone(reloj.getZone()).toOffsetDateTime(),
                new TotalesAvanceDto(cursos.size(), cursos.size() - pendientes.size(), pendientes.size()), cursos, motivo == null, motivo,
                docentesPendientes, !cerrado, resultado);
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
}
