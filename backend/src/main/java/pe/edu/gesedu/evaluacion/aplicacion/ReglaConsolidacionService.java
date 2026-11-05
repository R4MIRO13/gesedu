package pe.edu.gesedu.evaluacion.aplicacion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import pe.edu.gesedu.evaluacion.aplicacion.RegistroLogrosDtos.CompetenciaCursoDto;
import pe.edu.gesedu.evaluacion.aplicacion.ReglaConsolidacionDtos.ConfiguracionDto;
import pe.edu.gesedu.evaluacion.aplicacion.ReglaConsolidacionDtos.EjemploDto;
import pe.edu.gesedu.evaluacion.aplicacion.ReglaConsolidacionDtos.PesoDto;
import pe.edu.gesedu.evaluacion.aplicacion.ReglaConsolidacionDtos.PesoSolicitud;
import pe.edu.gesedu.evaluacion.aplicacion.ReglaConsolidacionDtos.ReglaDto;
import pe.edu.gesedu.evaluacion.aplicacion.ReglaConsolidacionDtos.SolicitudConfiguracion;
import pe.edu.gesedu.evaluacion.dominio.AsignacionDocente;
import pe.edu.gesedu.evaluacion.dominio.LogroPonderado;
import pe.edu.gesedu.evaluacion.dominio.PesoBimestre;
import pe.edu.gesedu.evaluacion.dominio.TipoRegla;
import pe.edu.gesedu.evaluacion.infraestructura.AsignacionDocenteRepository;
import pe.edu.gesedu.evaluacion.infraestructura.PesoBimestreRepository;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;

/**
 * UC-16, Definir regla de consolidación (RF-26, RF-38). El docente elige cómo se obtiene la nota final de cada competencia
 * de su curso en su sección: promedio ponderado, moda o último registro. Sin elegir rige el promedio ponderado (FA-2) y
 * elegir de nuevo cambia la regla (FA-1). Para el promedio ponderado puede ajustar el peso de cada bimestre (por omisión,
 * todos pesan 1). Solo el docente de la asignación; el guardado se serializa con la asignación bloqueada y queda auditado.
 */
@Service
public class ReglaConsolidacionService {

    /** Niveles de los bimestres I a IV del ejemplo que se muestra con cada regla: las tres reglas dan un resultado distinto. */
    private static final List<NivelLogro> NIVELES_EJEMPLO = List.of(NivelLogro.AD, NivelLogro.AD, NivelLogro.A, NivelLogro.B);

    private static final BigDecimal PESO_MAXIMO = new BigDecimal("100.00");

    private final AsignacionDocenteRepository asignaciones;
    private final PesoBimestreRepository pesos;
    private final ConsolidacionService consolidacion;
    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final DirectorioDocentes directorio;
    private final ContextoUsuario contexto;

    ReglaConsolidacionService(AsignacionDocenteRepository asignaciones, PesoBimestreRepository pesos,
                              ConsolidacionService consolidacion, AnioEscolarService anios, CatalogoAcademicoService catalogo,
                              DirectorioDocentes directorio, ContextoUsuario contexto) {
        this.asignaciones = asignaciones;
        this.pesos = pesos;
        this.consolidacion = consolidacion;
        this.anios = anios;
        this.catalogo = catalogo;
        this.directorio = directorio;
        this.contexto = contexto;
    }

    /** Pasos 1 a 4: la regla vigente, las tres opciones con su ejemplo y los pesos por bimestre. */
    @Transactional(readOnly = true)
    public ConfiguracionDto consultar(String asignacionId) {
        AsignacionDocente asignacion = alcance(asignaciones.findById(asignacionId).orElse(null));
        return armar(asignacion, 0);
    }

    /**
     * Pasos 5 y 6 (y FA-1): guarda la regla y, si viene, el conjunto de pesos. Una regla desconocida o un peso inválido
     * (cero, negativo, más de dos decimales, mayor que 100, bimestre inexistente o repetido) es 400 con la lista y no se
     * guarda nada. Un año cerrado ya no se configura (422). Guardar lo mismo no cambia nada ni se audita.
     */
    @Transactional
    @Auditable(accion = "CONFIGURAR_REGLA_CONSOLIDACION", modulo = "EVALUACION", tipoEntidad = "AsignacionDocente",
            idEntidad = "#asignacionId", descripcion = "Regla de consolidación y pesos por bimestre de un curso",
            condicion = "#result.cambios() > 0")
    public ConfiguracionDto guardar(String asignacionId, SolicitudConfiguracion solicitud) {
        // La asignación se bloquea: dos guardados del mismo curso se ejecutan uno tras otro
        AsignacionDocente asignacion = alcance(asignaciones.buscarBloqueando(asignacionId).orElse(null));
        String anioId = catalogo.anioEscolarDe(asignacion.getSeccionId());
        if ("CERRADO".equals(anios.obtenerPorId(anioId).estado())) {
            throw new ExcepcionNegocio(CodigoError.ANIO_CERRADO);
        }
        List<BimestreResumen> bimestres = anios.bimestres(anioId);
        List<Map<String, String>> errores = new ArrayList<>();
        TipoRegla regla = reglaDe(solicitud == null ? null : solicitud.regla(), errores);
        Map<String, BigDecimal> nuevosPesos = solicitud != null && solicitud.pesos() != null
                ? pesosDe(solicitud.pesos(), bimestres, errores) : null;
        if (!errores.isEmpty()) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "La configuración no es válida: no se guardó nada")
                    .con("errores", errores);
        }

        int cambios = 0;
        TipoRegla anterior = asignacion.tipoRegla();
        if (asignacion.cambiarRegla(regla)) {
            ContextoAuditoria.registrarCambio("regla", anterior, regla);
            cambios++;
        }
        if (nuevosPesos != null) {
            cambios += reemplazarPesos(asignacion, bimestres, nuevosPesos);
        }
        asignaciones.flush();
        return armar(asignacion, cambios);
    }

    // ---- pesos ----

    /** Deja exactamente los pesos pedidos (los distintos de 1); devuelve cuántos cambiaron. */
    private int reemplazarPesos(AsignacionDocente asignacion, List<BimestreResumen> bimestres, Map<String, BigDecimal> nuevos) {
        Map<String, Integer> numeroPorId = bimestres.stream().collect(Collectors.toMap(BimestreResumen::id, BimestreResumen::numero));
        Map<String, PesoBimestre> existentes = pesos.findByAsignacionId(asignacion.getId()).stream()
                .collect(Collectors.toMap(PesoBimestre::getBimestreId, Function.identity()));
        int cambios = 0;
        for (BimestreResumen bimestre : bimestres) {
            PesoBimestre fila = existentes.get(bimestre.id());
            BigDecimal anterior = fila == null ? PesoBimestre.PESO_POR_DEFECTO : fila.getPeso();
            BigDecimal peso = nuevos.getOrDefault(bimestre.id(), PesoBimestre.PESO_POR_DEFECTO);
            if (anterior.compareTo(peso) == 0) {
                continue;
            }
            ContextoAuditoria.registrarCambio("peso.bimestre" + numeroPorId.get(bimestre.id()), anterior.toPlainString(),
                    peso.toPlainString());
            cambios++;
            if (peso.compareTo(PesoBimestre.PESO_POR_DEFECTO) == 0) {
                pesos.delete(fila);
            } else if (fila == null) {
                pesos.save(new PesoBimestre(asignacion.getId(), bimestre.id(), peso));
            } else {
                fila.cambiarPeso(peso);
            }
        }
        return cambios;
    }

    private static TipoRegla reglaDe(String texto, List<Map<String, String>> errores) {
        if (texto != null) {
            for (TipoRegla tipo : TipoRegla.values()) {
                if (tipo.name().equals(texto)) {
                    return tipo;
                }
            }
        }
        errores.add(Map.of("campo", "regla", "mensaje", "Elige PROMEDIO_PONDERADO, MODA o ULTIMO_REGISTRO"));
        return null;
    }

    /** Pesos por id de bimestre ya validados; los errores se acumulan con su campo {@code pesos.<bimestre>}. */
    private static Map<String, BigDecimal> pesosDe(List<PesoSolicitud> pedidos, List<BimestreResumen> bimestres,
                                                    List<Map<String, String>> errores) {
        Map<Integer, BimestreResumen> porNumero = bimestres.stream().collect(Collectors.toMap(BimestreResumen::numero, Function.identity()));
        Map<String, BigDecimal> resultado = new LinkedHashMap<>();
        Set<Integer> vistos = new HashSet<>();
        for (PesoSolicitud pedido : pedidos) {
            String campo = "pesos." + pedido.bimestre();
            BimestreResumen bimestre = pedido.bimestre() == null ? null : porNumero.get(pedido.bimestre());
            if (bimestre == null) {
                errores.add(Map.of("campo", campo, "mensaje", "El bimestre no existe en el año escolar"));
            } else if (!vistos.add(pedido.bimestre())) {
                errores.add(Map.of("campo", campo, "mensaje", "El bimestre aparece más de una vez"));
            } else {
                BigDecimal peso = decimal(pedido.peso());
                if (peso == null || peso.signum() <= 0 || peso.stripTrailingZeros().scale() > 2) {
                    errores.add(Map.of("campo", campo, "mensaje", "El peso debe ser un número mayor que cero, con hasta dos decimales"));
                } else if (peso.compareTo(PESO_MAXIMO) > 0) {
                    errores.add(Map.of("campo", campo, "mensaje", "El peso no puede ser mayor que 100"));
                } else {
                    resultado.put(bimestre.id(), peso.setScale(2));
                }
            }
        }
        return resultado;
    }

    private static BigDecimal decimal(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- armado y alcance ----

    private AsignacionDocente alcance(AsignacionDocente asignacion) {
        String usuarioId = contexto.idUsuarioActual().orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO));
        DocenteResumen docente = directorio.docenteDeUsuario(usuarioId).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo un docente define la regla de consolidación de su curso"));
        if (asignacion == null || !docente.id().equals(asignacion.getDocenteId())) {
            throw new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, "Solo el docente del curso define su regla de consolidación");
        }
        return asignacion;
    }

    private ConfiguracionDto armar(AsignacionDocente asignacion, int cambios) {
        SeccionResumen seccion = catalogo.seccion(asignacion.getSeccionId());
        AnioEscolarResumen anio = anios.obtenerPorId(catalogo.anioEscolarDe(seccion.id()));
        List<BimestreResumen> bimestres = anios.bimestres(anio.id());
        Map<String, BigDecimal> guardados = pesos.findByAsignacionId(asignacion.getId()).stream()
                .collect(Collectors.toMap(PesoBimestre::getBimestreId, PesoBimestre::getPeso));
        List<PesoDto> listaPesos = bimestres.stream().map(b -> {
            BigDecimal peso = guardados.getOrDefault(b.id(), PesoBimestre.PESO_POR_DEFECTO);
            return new PesoDto(b.numero(), peso.setScale(2).toPlainString(), peso.compareTo(PesoBimestre.PESO_POR_DEFECTO) != 0);
        }).toList();
        List<CompetenciaResumen> competencias = catalogo.competenciasDe(List.of(asignacion.getCursoId()))
                .getOrDefault(asignacion.getCursoId(), List.of());
        return new ConfiguracionDto(asignacion.getId(), catalogo.curso(asignacion.getCursoId()).nombre(), seccion.nombre(),
                anio.anio(), asignacion.tipoRegla(), reglas(), listaPesos,
                competencias.stream().map(c -> new CompetenciaCursoDto(c.id(), c.codigo(), c.nombre(), c.nivelEsperado())).toList(),
                !"CERRADO".equals(anio.estado()), cambios);
    }

    /** Las tres reglas de RF-26 con su ejemplo calculado por el motor real (DA-09), con los bimestres pesando lo mismo. */
    private List<ReglaDto> reglas() {
        return List.of(
                regla(TipoRegla.PROMEDIO_PONDERADO, "Promedio ponderado",
                        "Convierte AD=4, A=3, B=2, C=1, promedia y redondea al nivel más cercano."),
                regla(TipoRegla.MODA, "Moda", "Toma el nivel que más se repite entre los bimestres."),
                regla(TipoRegla.ULTIMO_REGISTRO, "Último registro", "Toma el nivel obtenido en el último bimestre."));
    }

    private ReglaDto regla(TipoRegla tipo, String nombre, String descripcion) {
        List<LogroPonderado> logros = new ArrayList<>();
        for (int i = 0; i < NIVELES_EJEMPLO.size(); i++) {
            logros.add(LogroPonderado.de(NIVELES_EJEMPLO.get(i), i + 1));
        }
        NivelLogro resultado = consolidacion.consolidar(tipo, logros).orElseThrow();
        return new ReglaDto(tipo, nombre, descripcion, tipo == TipoRegla.POR_DEFECTO, new EjemploDto(NIVELES_EJEMPLO, resultado));
    }
}
