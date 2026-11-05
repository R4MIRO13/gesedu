package pe.edu.gesedu.asistencia.aplicacion;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.SeccionResumen;
import pe.edu.gesedu.asistencia.aplicacion.ReporteAsistenciaDtos.Ambito;
import pe.edu.gesedu.asistencia.aplicacion.ReporteAsistenciaDtos.ArchivoReporte;
import pe.edu.gesedu.asistencia.aplicacion.ReporteAsistenciaDtos.FilaReporteDto;
import pe.edu.gesedu.asistencia.aplicacion.ReporteAsistenciaDtos.Formato;
import pe.edu.gesedu.asistencia.aplicacion.ReporteAsistenciaDtos.ReporteAsistenciaDto;
import pe.edu.gesedu.asistencia.aplicacion.ReporteAsistenciaDtos.ResumenAsistenciaAlumnoDto;
import pe.edu.gesedu.asistencia.dominio.AsistenciaAlumno;
import pe.edu.gesedu.asistencia.dominio.AsistenciaDocente;
import pe.edu.gesedu.asistencia.dominio.CalculoResumenMensual;
import pe.edu.gesedu.asistencia.dominio.CalculoResumenMensual.Resumen;
import pe.edu.gesedu.asistencia.dominio.EstadoAsistencia;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaAlumnoRepository;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaDocenteRepository;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaProperties;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.horarios.aplicacion.ClasesDocenteService;
import pe.edu.gesedu.horarios.aplicacion.ClasesDocenteService.JornadaDelDia;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService.AlumnoDeSeccion;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias;
import pe.edu.gesedu.transversal.reportes.GeneradorExcel;
import pe.edu.gesedu.transversal.reportes.GeneradorPdf;

/**
 * UC-10 y UC-11, Exportar reportes de asistencia y Calcular resumen mensual (RF-16, RF-17, RF-21): los totales y el
 * porcentaje se calculan al momento de pedir el reporte a partir de los registros diarios; no se guarda ningún consolidado
 * ni se marca el mes como cerrado. Los reportes de alumnos y de docentes se procesan por separado.
 * <p>
 * Alcance: el tutor solo ve alumnos de su sección; el administrador, alumnos de cualquier sección y el personal docente.
 * Días considerados: los lectivos del periodo (S-57) hasta la fecha de corte; no hay fechas futuras y el día actual solo
 * entra con la jornada cerrada. En docentes cuentan los días con clases del docente en el horario publicado. Un día
 * esperado sin registro se muestra como «no registrado» y no se convierte en falta ni entra en el porcentaje.
 */
@Service
public class ReporteAsistenciaService {

    private static final String REPORTE_SECCION = "ASISTENCIA_REPORTE_SECCION";
    private static final String REPORTE_GENERAL = "ASISTENCIA_REPORTE_GENERAL";
    private static final Locale ES = Locale.of("es", "PE");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AsistenciaProperties propiedades;
    private final AsistenciaAlumnoRepository alumnos;
    private final AsistenciaDocenteRepository docentes;
    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final ConsultaMatriculaService matriculas;
    private final ClasesDocenteService clasesDocente;
    private final DirectorioDocentes directorio;
    private final DirectorioFamilias familias;
    private final ContextoUsuario contexto;
    private final GeneradorExcel excel;
    private final GeneradorPdf pdf;
    private final Clock reloj;

    ReporteAsistenciaService(AsistenciaProperties propiedades, AsistenciaAlumnoRepository alumnos, AsistenciaDocenteRepository docentes,
                             AnioEscolarService anios, CatalogoAcademicoService catalogo, ConsultaMatriculaService matriculas,
                             ClasesDocenteService clasesDocente, DirectorioDocentes directorio, DirectorioFamilias familias,
                             ContextoUsuario contexto, GeneradorExcel excel, GeneradorPdf pdf, Clock reloj) {
        this.propiedades = propiedades;
        this.alumnos = alumnos;
        this.docentes = docentes;
        this.anios = anios;
        this.catalogo = catalogo;
        this.matriculas = matriculas;
        this.clasesDocente = clasesDocente;
        this.directorio = directorio;
        this.familias = familias;
        this.contexto = contexto;
        this.excel = excel;
        this.pdf = pdf;
        this.reloj = reloj;
    }

    /** El periodo y el alcance resueltos: los filtros ya validados y los días que entran en el cálculo. */
    private record Calendario(YearMonth periodo, LocalDate corte, boolean parcial, List<LocalDate> dias, AnioEscolarResumen anio) {
    }

    // ---- UC-10: validar filtros, alcance y entregar ----

    /** Pasos 2 a 7 en JSON: valida filtros y permisos en el servidor y calcula el reporte (UC-11). */
    @Transactional(readOnly = true)
    public ReporteAsistenciaDto generar(Ambito ambito, Integer anio, Integer mes, String seccion) {
        ZonedDateTime ahora = reloj.instant().atZone(reloj.getZone());
        YearMonth pedido = periodoPedido(anio, mes, ahora);
        Calendario calendario = calendario(pedido.getYear(), pedido.getMonthValue(), ahora);
        ReporteAsistenciaDto reporte = switch (ambito) {
            case ALUMNOS -> reporteAlumnos(calendario, seccion, ahora);
            case DOCENTES -> reporteDocentes(calendario, ahora);
        };
        if (reporte.filas().stream().mapToInt(FilaReporteDto::registrados).sum() == 0) {
            // FA-2: sin asistencias registradas no se genera archivo ni se muestran porcentajes
            throw new ExcepcionNegocio(CodigoError.SIN_REGISTROS_ASISTENCIA, "No hay asistencias registradas para " + reporte.alcance()
                    + " en " + nombreMes(calendario.periodo()) + ".");
        }
        return reporte;
    }

    /** El reporte como archivo descargable (Excel o PDF), con la fecha de generación, el periodo, el alcance y los resultados. */
    @Transactional(readOnly = true)
    public ArchivoReporte archivo(Ambito ambito, Integer anio, Integer mes, String seccion, Formato formato) {
        ReporteAsistenciaDto r = generar(ambito, anio, mes, seccion);
        boolean alumnosAmbito = ambito == Ambito.ALUMNOS;
        List<String> encabezados = alumnosAmbito
                ? List.of("Código", "Apellidos y nombres", "Sección", "Puntual", "Tardanza", "Falta", "Días registrados", "Días esperados",
                        "No registrados", "% de asistencia")
                : List.of("Docente", "Puntual", "Tardanza", "Falta", "Días registrados", "Días esperados", "No registrados", "% de asistencia");
        List<List<Object>> filas = new ArrayList<>();
        for (FilaReporteDto f : r.filas()) {
            Object porcentaje = f.porcentaje() == null ? "No disponible" : new java.math.BigDecimal(f.porcentaje());
            filas.add(alumnosAmbito
                    ? List.of(f.codigo(), f.nombre(), f.seccion(), f.puntual(), f.tardanza(), f.falta(), f.registrados(), f.diasEsperados(),
                            f.noRegistrados(), porcentaje)
                    : List.of(f.nombre(), f.puntual(), f.tardanza(), f.falta(), f.registrados(), f.diasEsperados(), f.noRegistrados(), porcentaje));
        }
        String titulo = "Reporte de asistencia de " + (alumnosAmbito ? "alumnos" : "docentes");
        List<String> resumen = resumen(r);
        String base = "asistencia-" + (alumnosAmbito ? "alumnos" : "docentes") + (r.alcance().matches("\\d° [A-Z]") ? "-" + r.alcance().replace("° ", "") : "")
                + "-%04d-%02d".formatted(r.anio(), r.mes());
        return switch (formato) {
            case XLSX -> new ArchivoReporte(excel.generar("Asistencia", resumen, encabezados, filas), GeneradorExcel.TIPO_CONTENIDO, base + ".xlsx");
            case PDF -> new ArchivoReporte(pdf.generar(titulo, resumen, encabezados, filas), GeneradorPdf.TIPO_CONTENIDO, base + ".pdf");
            case JSON -> throw new IllegalArgumentException("JSON no es un archivo");
        };
    }

    private List<String> resumen(ReporteAsistenciaDto r) {
        List<String> lineas = new ArrayList<>();
        lineas.add("Alcance: " + r.alcance());
        lineas.add("Periodo: " + nombreMes(YearMonth.of(r.anio(), r.mes())) + (r.parcial() ? " (reporte parcial, con corte al " + FECHA.format(r.fechaCorte()) + ")" : ""));
        lineas.add("Generado el " + FECHA_HORA.format(r.generadoEn()));
        lineas.add("Días lectivos considerados: " + r.diasLectivos());
        lineas.add("% de asistencia = 100 × (puntual + tardanza) / días registrados. La tardanza cuenta como presencia.");
        if (r.totalNoRegistrados() > 0) {
            lineas.add("Información incompleta: " + r.totalNoRegistrados() + " días esperados sin registro; no se consideran faltas y quedan fuera del porcentaje.");
        }
        return lineas;
    }

    // ---- inicio del alumno: su propio resumen ----

    /**
     * El resumen de asistencia del alumno en el mes (por omisión, el actual), calculado al pedirlo con las mismas reglas
     * que el reporte (UC-11). Solo el propio alumno: otro id → 403. Sin registros no es un error: los conteos van en
     * cero y el porcentaje es «No disponible».
     */
    @Transactional(readOnly = true)
    public ResumenAsistenciaAlumnoDto resumenDeAlumno(String alumnoId, YearMonth mes) {
        String usuarioId = contexto.idUsuarioActual().orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO));
        boolean esElPropio = familias.alumnoDeUsuario(usuarioId).map(a -> a.id().equals(alumnoId)).orElse(false);
        if (!esElPropio) {
            throw new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo puedes consultar tu propia asistencia");
        }
        ZonedDateTime ahora = reloj.instant().atZone(reloj.getZone());
        YearMonth periodo = mes != null ? mes : YearMonth.from(ahora);
        Calendario c = calendario(periodo.getYear(), periodo.getMonthValue(), ahora);
        Set<LocalDate> dias = new HashSet<>(c.dias());
        Map<EstadoAsistencia, Integer> conteo = new EnumMap<>(EstadoAsistencia.class);
        for (AsistenciaAlumno a : alumnos.findByFechaBetweenAndAlumnoIdIn(periodo.atDay(1), c.corte(), List.of(alumnoId))) {
            if (dias.contains(a.getFecha())) {
                conteo.merge(a.getEstado(), 1, Integer::sum);
            }
        }
        Resumen r = resumenDe(conteo, c.dias().size());
        return new ResumenAsistenciaAlumnoDto(alumnoId, periodo.getYear(), periodo.getMonthValue(), r.puntual(), r.tardanza(), r.falta(),
                r.registrados(), r.diasEsperados(), r.noRegistrados(), r.porcentaje() == null ? null : r.porcentaje().toPlainString(),
                c.parcial(), c.corte());
    }

    // ---- UC-11: cálculo por ámbito ----

    private ReporteAsistenciaDto reporteAlumnos(Calendario c, String codigoSeccion, ZonedDateTime ahora) {
        List<SeccionResumen> visibles = seccionesVisibles(c.anio(), codigoSeccion);
        Set<LocalDate> dias = new HashSet<>(c.dias());
        List<FilaReporteDto> filas = new ArrayList<>();
        for (SeccionResumen seccion : visibles) {
            List<AlumnoDeSeccion> deLaSeccion = matriculas.alumnosDeSeccion(seccion.id());
            if (deLaSeccion.isEmpty()) {
                continue;
            }
            Map<String, Map<EstadoAsistencia, Integer>> conteo = new HashMap<>();
            for (AsistenciaAlumno a : alumnos.findByFechaBetweenAndAlumnoIdIn(c.periodo().atDay(1), c.corte(),
                    deLaSeccion.stream().map(AlumnoDeSeccion::alumnoId).toList())) {
                if (dias.contains(a.getFecha())) {
                    conteo.computeIfAbsent(a.getAlumnoId(), k -> new EnumMap<>(EstadoAsistencia.class)).merge(a.getEstado(), 1, Integer::sum);
                }
            }
            for (AlumnoDeSeccion alumno : deLaSeccion) {
                Resumen resumen = resumenDe(conteo.get(alumno.alumnoId()), c.dias().size());
                filas.add(fila(alumno.alumnoId(), alumno.codigo(), alumno.apellidos() + ", " + alumno.nombres(), seccion.nombre(), resumen));
            }
        }
        String alcance = visibles.size() == 1 ? visibles.getFirst().nombre() : "Todas las secciones";
        return reporte(Ambito.ALUMNOS, c, alcance, ahora, filas);
    }

    private ReporteAsistenciaDto reporteDocentes(Calendario c, ZonedDateTime ahora) {
        if (!contexto.tienePermiso(REPORTE_GENERAL)) {
            throw new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo el administrador exporta reportes de docentes");
        }
        // Qué docentes tenían clases cada día lectivo, según el horario publicado (FA-4: sin él no hay universo de días esperados)
        Map<DayOfWeek, JornadaDelDia> jornadas = new HashMap<>();
        for (LocalDate dia : c.dias()) {
            jornadas.computeIfAbsent(dia.getDayOfWeek(), d -> clasesDocente.jornadaDelDia(c.anio().anio(), d));
        }
        if (jornadas.values().stream().anyMatch(j -> !j.horarioPublicado())) {
            throw new ExcepcionNegocio(CodigoError.CALENDARIO_NO_DISPONIBLE,
                    "No hay un horario publicado para el año " + c.anio().anio() + ": no puede determinarse el universo de días esperados de los docentes.");
        }
        Map<String, Integer> esperados = new HashMap<>();
        for (LocalDate dia : c.dias()) {
            for (String docenteId : jornadas.get(dia.getDayOfWeek()).primerBloquePorDocente().keySet()) {
                esperados.merge(docenteId, 1, Integer::sum);
            }
        }
        Set<LocalDate> dias = new HashSet<>(c.dias());
        Map<String, Map<EstadoAsistencia, Integer>> conteo = new HashMap<>();
        for (AsistenciaDocente a : docentes.findByFechaBetween(c.periodo().atDay(1), c.corte())) {
            if (dias.contains(a.getFecha())) {
                conteo.computeIfAbsent(a.getDocenteId(), k -> new EnumMap<>(EstadoAsistencia.class)).merge(a.getEstado(), 1, Integer::sum);
            }
        }
        Set<String> ids = new HashSet<>(esperados.keySet());
        ids.addAll(conteo.keySet());
        Map<String, DocenteResumen> nombres = directorio.porIds(ids);
        List<FilaReporteDto> filas = new ArrayList<>();
        for (String id : ids) {
            DocenteResumen d = nombres.get(id);
            filas.add(fila(id, null, d == null ? "—" : d.apellidos() + ", " + d.nombres(), null,
                    resumenDe(conteo.get(id), esperados.getOrDefault(id, 0))));
        }
        filas.sort(Comparator.comparing(FilaReporteDto::nombre, String.CASE_INSENSITIVE_ORDER));
        return reporte(Ambito.DOCENTES, c, "Personal docente", ahora, filas);
    }

    private static Resumen resumenDe(Map<EstadoAsistencia, Integer> conteo, int esperados) {
        Map<EstadoAsistencia, Integer> c = conteo == null ? Map.of() : conteo;
        return new Resumen(c.getOrDefault(EstadoAsistencia.PUNTUAL, 0), c.getOrDefault(EstadoAsistencia.TARDANZA, 0),
                c.getOrDefault(EstadoAsistencia.FALTA, 0), esperados);
    }

    private static FilaReporteDto fila(String id, String codigo, String nombre, String seccion, Resumen r) {
        return new FilaReporteDto(id, codigo, nombre, seccion, r.puntual(), r.tardanza(), r.falta(), r.registrados(), r.diasEsperados(),
                r.noRegistrados(), r.porcentaje() == null ? null : r.porcentaje().toPlainString());
    }

    private ReporteAsistenciaDto reporte(Ambito ambito, Calendario c, String alcance, ZonedDateTime ahora, List<FilaReporteDto> filas) {
        return new ReporteAsistenciaDto(ambito, c.periodo().getYear(), c.periodo().getMonthValue(), alcance,
                OffsetDateTime.from(ahora), c.corte(), c.parcial(), c.dias().size(),
                filas.stream().mapToInt(FilaReporteDto::noRegistrados).sum(), filas);
    }

    // ---- alcance y calendario ----

    /** El tutor, solo su sección; el administrador, la pedida o todas. Cualquier otra cosa: 403 (FA-3). */
    private List<SeccionResumen> seccionesVisibles(AnioEscolarResumen anio, String codigoSeccion) {
        List<SeccionResumen> todas = catalogo.secciones(anio.id());
        if (contexto.tienePermiso(REPORTE_GENERAL)) {
            if (codigoSeccion == null || codigoSeccion.isBlank()) {
                return todas;
            }
            return List.of(todas.stream().filter(s -> codigo(s).equalsIgnoreCase(codigoSeccion)).findFirst()
                    .orElseThrow(() -> new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe la sección " + codigoSeccion)));
        }
        if (!contexto.tienePermiso(REPORTE_SECCION)) {
            throw new ExcepcionNegocio(CodigoError.ACCESO_DENEGADO);
        }
        DocenteResumen docente = contexto.idUsuarioActual().flatMap(directorio::docenteDeUsuario).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo un docente tutor exporta el reporte de su sección"));
        SeccionResumen propia = todas.stream().filter(s -> docente.id().equals(s.tutorDocenteId())).findFirst()
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "No tienes una sección a cargo como tutor este año."));
        if (codigoSeccion != null && !codigoSeccion.isBlank() && !codigo(propia).equalsIgnoreCase(codigoSeccion)) {
            throw new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo puedes exportar el reporte de tu sección " + propia.nombre());
        }
        return List.of(propia);
    }

    /**
     * El periodo pedido. Sin mes ni año es el mes actual del servidor (la hora del dispositivo no se usa); con uno solo de
     * los dos, la solicitud es inválida.
     */
    private static YearMonth periodoPedido(Integer anio, Integer mes, ZonedDateTime ahora) {
        if (anio == null && mes == null) {
            return YearMonth.from(ahora);
        }
        if (anio == null || mes == null) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "Indica el mes y el año, o ninguno para el mes actual")
                    .con("errores", List.of(Map.of("campo", anio == null ? "anio" : "mes", "mensaje", "Falta " + (anio == null ? "el año" : "el mes"))));
        }
        if (mes < 1 || mes > 12 || anio < 2000 || anio > 2999) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "El mes o el año no son válidos")
                    .con("errores", List.of(Map.of("campo", mes < 1 || mes > 12 ? "mes" : "anio", "mensaje", "Indica un mes de 1 a 12 y un año entre 2000 y 2999")));
        }
        return YearMonth.of(anio, mes);
    }

    /**
     * Valida el periodo (no futuro, FA-3), exige un año activo (FA-4) y decide los días: los lectivos del mes hasta la fecha
     * de corte. El día actual solo cuenta con la jornada cerrada; el periodo en curso es parcial (FA-1).
     */
    private Calendario calendario(int anio, int mes, ZonedDateTime ahora) {
        YearMonth periodo = YearMonth.of(anio, mes);
        LocalDate hoy = ahora.toLocalDate();
        if (periodo.atDay(1).isAfter(hoy)) {
            throw new ExcepcionNegocio(CodigoError.PERIODO_INVALIDO, "No se puede pedir el reporte de un periodo futuro: corrige el mes o el año.");
        }
        AnioEscolarResumen activo = anios.anioActivo().orElseThrow(() -> new ExcepcionNegocio(CodigoError.CALENDARIO_NO_DISPONIBLE,
                "No hay un año escolar activo: no puede determinarse el universo de días esperados."));
        boolean jornadaCerrada = !ahora.toLocalTime().isBefore(propiedades.schoolDayClosingTime());
        LocalDate ultimoPermitido = jornadaCerrada ? hoy : hoy.minusDays(1);
        LocalDate corte = periodo.atEndOfMonth().isBefore(ultimoPermitido) ? periodo.atEndOfMonth() : ultimoPermitido;
        List<LocalDate> dias = new ArrayList<>();
        Map<DayOfWeek, Boolean> conJornada = new HashMap<>();
        for (LocalDate d = periodo.atDay(1); !d.isAfter(corte); d = d.plusDays(1)) {
            if (anios.anioLectivoEn(d).isPresent()
                    && conJornada.computeIfAbsent(d.getDayOfWeek(), dow -> clasesDocente.hayJornada(activo.anio(), dow))) {
                dias.add(d);
            }
        }
        return new Calendario(periodo, corte, corte.isBefore(periodo.atEndOfMonth()), dias, activo);
    }

    private static String nombreMes(YearMonth periodo) {
        return Month.of(periodo.getMonthValue()).getDisplayName(TextStyle.FULL, ES) + " " + periodo.getYear();
    }

    private static String codigo(SeccionResumen s) {
        return s.numeroGrado() + s.letra();
    }
}
