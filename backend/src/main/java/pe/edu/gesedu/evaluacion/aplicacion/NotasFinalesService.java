package pe.edu.gesedu.evaluacion.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import pe.edu.gesedu.evaluacion.aplicacion.NotasFinalesDtos.CalculoDto;
import pe.edu.gesedu.evaluacion.aplicacion.NotasFinalesDtos.CeldaNotaDto;
import pe.edu.gesedu.evaluacion.aplicacion.NotasFinalesDtos.FilaNotasDto;
import pe.edu.gesedu.evaluacion.aplicacion.NotasFinalesDtos.NotasFinalesDto;
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.CompetenciaDto;
import pe.edu.gesedu.evaluacion.dominio.AsignacionDocente;
import pe.edu.gesedu.evaluacion.dominio.NotaConsolidada;
import pe.edu.gesedu.evaluacion.dominio.PesoBimestre;
import pe.edu.gesedu.evaluacion.dominio.RegistroLogro;
import pe.edu.gesedu.evaluacion.infraestructura.AsignacionDocenteRepository;
import pe.edu.gesedu.evaluacion.infraestructura.NotaConsolidadaRepository;
import pe.edu.gesedu.evaluacion.infraestructura.PesoBimestreRepository;
import pe.edu.gesedu.evaluacion.infraestructura.RegistroLogroRepository;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService.AlumnoDeSeccion;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;

/**
 * UC-17, Cálculo de notas finales (RF-28, RF-29, RF-38). El docente ve los logros de cada bimestre de sus alumnos y calcula
 * la nota anual de cada competencia con la regla de su curso (UC-16). El cálculo es idempotente: repetirlo no duplica ni
 * cambia nada si los datos son los mismos, y si cambiaron reemplaza la nota anterior. Las competencias sin ningún logro no
 * reciben nota. Solo el docente de la asignación; un año cerrado ya no se calcula.
 */
@Service
public class NotasFinalesService {

    private final AsignacionDocenteRepository asignaciones;
    private final RegistroLogroRepository registros;
    private final NotaConsolidadaRepository notas;
    private final PesoBimestreRepository pesos;
    private final CalculadorNotaAnual calculador;
    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final ConsultaMatriculaService matriculas;
    private final DirectorioDocentes directorio;
    private final ContextoUsuario contexto;
    private final Clock reloj;

    NotasFinalesService(AsignacionDocenteRepository asignaciones, RegistroLogroRepository registros, NotaConsolidadaRepository notas,
                        PesoBimestreRepository pesos, CalculadorNotaAnual calculador, AnioEscolarService anios,
                        CatalogoAcademicoService catalogo, ConsultaMatriculaService matriculas, DirectorioDocentes directorio,
                        ContextoUsuario contexto, Clock reloj) {
        this.asignaciones = asignaciones;
        this.registros = registros;
        this.notas = notas;
        this.pesos = pesos;
        this.calculador = calculador;
        this.anios = anios;
        this.catalogo = catalogo;
        this.matriculas = matriculas;
        this.directorio = directorio;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    /** Paso 2: la tabla de alumnos con los logros de cada bimestre y las notas anuales ya calculadas. */
    @Transactional(readOnly = true)
    public NotasFinalesDto consultar(String asignacionId) {
        AsignacionDocente asignacion = alcance(asignaciones.findById(asignacionId).orElse(null));
        return armar(asignacion, null);
    }

    /**
     * Pasos 3 y 4: calcula la nota anual de cada alumno y competencia con la regla de la asignación y la guarda,
     * reemplazando la anterior. Sin ningún logro registrado: 422 {@code SIN_LOGROS_REGISTRADOS}; año cerrado: 422. Con
     * logros parciales se calcula con lo que hay y la respuesta cuenta las celdas sin nota.
     */
    @Transactional
    @Auditable(accion = "CALCULAR_NOTAS_FINALES", modulo = "EVALUACION", tipoEntidad = "NotaConsolidada",
            idEntidad = "#asignacionId", descripcion = "Cálculo de las notas finales de un curso",
            condicion = "#result.calculo().creadas() + #result.calculo().actualizadas() > 0")
    public NotasFinalesDto calcular(String asignacionId) {
        // La asignación se bloquea: dos cálculos del mismo curso se ejecutan uno tras otro
        AsignacionDocente asignacion = alcance(asignaciones.buscarBloqueando(asignacionId).orElse(null));
        String anioId = catalogo.anioEscolarDe(asignacion.getSeccionId());
        if ("CERRADO".equals(anios.obtenerPorId(anioId).estado())) {
            throw new ExcepcionNegocio(CodigoError.ANIO_CERRADO);
        }
        List<RegistroLogro> logros = registros.findByAsignacionId(asignacionId);
        if (logros.isEmpty()) {
            throw new ExcepcionNegocio(CodigoError.SIN_LOGROS_REGISTRADOS,
                    "Aún no hay logros registrados en este curso: registra las notas de al menos un bimestre.");
        }
        List<BimestreResumen> bimestres = anios.bimestres(anioId);
        Map<String, BigDecimal> pesosPorBimestre = pesosDe(asignacionId);
        Map<String, NivelLogro> esperadas = calculador.calcular(asignacion.tipoRegla(), logros, numeroPorId(bimestres), pesosPorBimestre);

        Map<String, AlumnoDeSeccion> alumnos = matriculas.alumnosDeSeccion(asignacion.getSeccionId()).stream()
                .collect(Collectors.toMap(AlumnoDeSeccion::alumnoId, Function.identity()));
        Map<String, CompetenciaResumen> competencias = competenciasDe(asignacion).stream()
                .collect(Collectors.toMap(CompetenciaResumen::id, Function.identity()));
        Map<String, NotaConsolidada> existentes = notas.findByAsignacionIdAndTipo(asignacionId, NotaConsolidada.ANUAL).stream()
                .collect(Collectors.toMap(n -> CalculadorNotaAnual.clave(n.getAlumnoId(), n.getCompetenciaId()), Function.identity()));
        var ahora = reloj.instant();
        int creadas = 0;
        int actualizadas = 0;
        int sinCambios = 0;
        // Solo los alumnos matriculados hoy en la sección y las competencias vigentes del curso reciben nota
        for (AlumnoDeSeccion alumno : alumnos.values()) {
            for (CompetenciaResumen competencia : competencias.values()) {
                String celda = CalculadorNotaAnual.clave(alumno.alumnoId(), competencia.id());
                NivelLogro nivel = esperadas.get(celda);
                if (nivel == null) {
                    continue;
                }
                NotaConsolidada existente = existentes.get(celda);
                String campo = "anual " + alumno.codigo() + " " + competencia.codigo();
                if (existente == null) {
                    notas.save(NotaConsolidada.anual(alumno.alumnoId(), competencia.id(), asignacionId, anioId, nivel, ahora));
                    ContextoAuditoria.registrarCambio(campo, null, nivel);
                    creadas++;
                } else {
                    NivelLogro anterior = existente.getNivelLogro();
                    if (existente.reemplazar(nivel, ahora)) {
                        ContextoAuditoria.registrarCambio(campo, anterior, nivel);
                        actualizadas++;
                    } else {
                        sinCambios++;
                    }
                }
            }
        }
        int sinNota = alumnos.size() * competencias.size() - creadas - actualizadas - sinCambios;
        notas.flush();
        return armar(asignacion, new CalculoDto(creadas, actualizadas, sinCambios, sinNota));
    }

    // ---- armado y alcance ----

    private NotasFinalesDto armar(AsignacionDocente asignacion, CalculoDto calculo) {
        SeccionResumen seccion = catalogo.seccion(asignacion.getSeccionId());
        AnioEscolarResumen anio = anios.obtenerPorId(catalogo.anioEscolarDe(seccion.id()));
        List<BimestreResumen> bimestres = anios.bimestres(anio.id());
        Map<String, Integer> numeroPorId = numeroPorId(bimestres);
        List<CompetenciaResumen> competencias = competenciasDe(asignacion);
        List<AlumnoDeSeccion> alumnos = matriculas.alumnosDeSeccion(seccion.id());
        List<RegistroLogro> logros = registros.findByAsignacionId(asignacion.getId());
        Map<String, NotaConsolidada> anuales = notas.findByAsignacionIdAndTipo(asignacion.getId(), NotaConsolidada.ANUAL).stream()
                .collect(Collectors.toMap(n -> CalculadorNotaAnual.clave(n.getAlumnoId(), n.getCompetenciaId()), Function.identity()));
        Map<String, Map<Integer, NivelLogro>> porBimestre = new HashMap<>();
        for (RegistroLogro l : logros) {
            Integer numero = numeroPorId.get(l.getBimestreId());
            if (numero != null) {
                porBimestre.computeIfAbsent(CalculadorNotaAnual.clave(l.getAlumnoId(), l.getCompetenciaId()), k -> new LinkedHashMap<>())
                        .put(numero, l.getNivelLogro());
            }
        }
        List<FilaNotasDto> filas = new ArrayList<>();
        int sinNota = 0;
        for (AlumnoDeSeccion alumno : alumnos) {
            Map<String, CeldaNotaDto> celdas = new LinkedHashMap<>();
            for (CompetenciaResumen competencia : competencias) {
                String clave = CalculadorNotaAnual.clave(alumno.alumnoId(), competencia.id());
                Map<Integer, NivelLogro> bimestresDeLaCelda = new java.util.TreeMap<>(porBimestre.getOrDefault(clave, Map.of()));
                if (bimestresDeLaCelda.isEmpty()) {
                    sinNota++;
                }
                NotaConsolidada anual = anuales.get(clave);
                celdas.put(competencia.id(), new CeldaNotaDto(bimestresDeLaCelda, anual == null ? null : anual.getNivelLogro()));
            }
            filas.add(new FilaNotasDto(alumno.alumnoId(), alumno.codigo(), alumno.apellidos(), alumno.nombres(), celdas));
        }
        // Desactualizada: volver a calcular hoy daría otra cosa que lo guardado (nuevos logros, otra regla u otros pesos)
        Map<String, NivelLogro> esperadas = calculador.calcular(asignacion.tipoRegla(), logros, numeroPorId, pesosDe(asignacion.getId()));
        boolean desactualizada = false;
        if (!anuales.isEmpty()) {
            for (AlumnoDeSeccion alumno : alumnos) {
                for (CompetenciaResumen competencia : competencias) {
                    String clave = CalculadorNotaAnual.clave(alumno.alumnoId(), competencia.id());
                    NotaConsolidada guardada = anuales.get(clave);
                    if (esperadas.get(clave) != (guardada == null ? null : guardada.getNivelLogro())) {
                        desactualizada = true;
                    }
                }
            }
        }
        return new NotasFinalesDto(asignacion.getId(), catalogo.curso(asignacion.getCursoId()).nombre(), seccion.nombre(),
                anio.anio(), asignacion.tipoRegla(), bimestres.stream().map(BimestreResumen::numero).toList(),
                competencias.stream().map(c -> new CompetenciaDto(c.id(), c.codigo(), c.nombre(), c.nivelEsperado())).toList(),
                filas, !anuales.isEmpty(), desactualizada, !"CERRADO".equals(anio.estado()), sinNota, calculo);
    }

    private List<CompetenciaResumen> competenciasDe(AsignacionDocente asignacion) {
        return catalogo.competenciasDe(List.of(asignacion.getCursoId())).getOrDefault(asignacion.getCursoId(), List.of());
    }

    private Map<String, BigDecimal> pesosDe(String asignacionId) {
        return pesos.findByAsignacionId(asignacionId).stream().collect(Collectors.toMap(PesoBimestre::getBimestreId, PesoBimestre::getPeso));
    }

    private static Map<String, Integer> numeroPorId(List<BimestreResumen> bimestres) {
        return bimestres.stream().collect(Collectors.toMap(BimestreResumen::id, BimestreResumen::numero));
    }

    private AsignacionDocente alcance(AsignacionDocente asignacion) {
        String usuarioId = contexto.idUsuarioActual().orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO));
        DocenteResumen docente = directorio.docenteDeUsuario(usuarioId).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo un docente calcula las notas finales de su curso"));
        if (asignacion == null || !docente.id().equals(asignacion.getDocenteId())) {
            throw new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo el docente del curso calcula sus notas finales");
        }
        return asignacion;
    }
}
