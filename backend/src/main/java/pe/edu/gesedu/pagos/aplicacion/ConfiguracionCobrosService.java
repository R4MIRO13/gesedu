package pe.edu.gesedu.pagos.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.pagos.dominio.ConceptoPago;
import pe.edu.gesedu.pagos.dominio.CronogramaCobro;
import pe.edu.gesedu.pagos.dominio.EstadoObligacion;
import pe.edu.gesedu.pagos.dominio.PoliticaCobro;
import pe.edu.gesedu.pagos.infraestructura.CronogramaCobroRepository;
import pe.edu.gesedu.pagos.infraestructura.ObligacionPagoRepository;
import pe.edu.gesedu.pagos.infraestructura.PoliticaCobroRepository;

/**
 * Configuración de costos y política de penalidad del año escolar (RF-06). El cronograma se guarda completo:
 * lo que llega reemplaza lo anterior (las líneas ausentes se eliminan). Al guardar, las obligaciones PENDIENTES del año
 * se actualizan en la misma transacción (RF-07).
 */
@Service
public class ConfiguracionCobrosService {

    /** Una línea del cronograma; el monto viaja como string decimal. */
    public record LineaCobro(ConceptoPago concepto, String periodo, Dinero monto, LocalDate fechaVencimiento) {
    }

    /**
     * Cronograma y política del año. {@code obligacionesActualizadas} cuenta las obligaciones PENDIENTES que cambiaron
     * de monto o de vencimiento al guardar (RF-07); en una consulta es siempre cero.
     */
    public record ConfiguracionCobros(int anio, BigDecimal porcentajePenalidad, List<LineaCobro> lineas,
                                      boolean configurado, int obligacionesActualizadas) {
    }

    private final CronogramaCobroRepository cronograma;
    private final PoliticaCobroRepository politicas;
    private final ObligacionPagoRepository obligaciones;
    private final AnioEscolarService anios;
    private final Clock reloj;

    ConfiguracionCobrosService(CronogramaCobroRepository cronograma, PoliticaCobroRepository politicas,
                               ObligacionPagoRepository obligaciones, AnioEscolarService anios, Clock reloj) {
        this.cronograma = cronograma;
        this.politicas = politicas;
        this.obligaciones = obligaciones;
        this.anios = anios;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public ConfiguracionCobros obtener(int anio) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        List<LineaCobro> lineas = ordenadas(cronograma.findByAnioEscolarId(a.id())).stream()
                .map(ConfiguracionCobrosService::aLinea).toList();
        BigDecimal porcentaje = politicas.findByAnioEscolarId(a.id()).map(PoliticaCobro::getPorcentajePenalidad)
                .orElse(null);
        return new ConfiguracionCobros(anio, porcentaje, lineas, !lineas.isEmpty() && porcentaje != null, 0);
    }

    /** Guarda el cronograma y la política del año; el resultado es la configuración vigente. */
    @Transactional
    @Auditable(accion = "CONFIGURAR_COBROS", modulo = "PAGOS", tipoEntidad = "CronogramaCobro", idEntidad = "#anio",
            descripcion = "Configuración del cronograma y la política de cobros del año, y actualización de las obligaciones pendientes")
    public ConfiguracionCobros guardar(int anio, BigDecimal porcentajePenalidad, List<LineaCobro> lineas) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        validar(anio, porcentajePenalidad, lineas);

        Map<String, CronogramaCobro> actuales = cronograma.findByAnioEscolarId(a.id()).stream()
                .collect(Collectors.toMap(ConfiguracionCobrosService::clave, Function.identity()));
        Set<String> nuevas = new HashSet<>();
        for (LineaCobro linea : lineas) {
            String clave = clave(linea.concepto(), linea.periodo());
            nuevas.add(clave);
            CronogramaCobro existente = actuales.get(clave);
            if (existente == null) {
                cronograma.save(new CronogramaCobro(a.id(), linea.concepto(), linea.periodo(),
                        linea.monto().valor(), linea.fechaVencimiento()));
                ContextoAuditoria.registrarCambio(clave, null, descripcion(linea.monto(), linea.fechaVencimiento()));
            } else if (existente.getMonto().compareTo(linea.monto().valor()) != 0
                    || !existente.getFechaVencimiento().equals(linea.fechaVencimiento())) {
                ContextoAuditoria.registrarCambio(clave,
                        descripcion(Dinero.de(existente.getMonto()), existente.getFechaVencimiento()),
                        descripcion(linea.monto(), linea.fechaVencimiento()));
                existente.actualizar(linea.monto().valor(), linea.fechaVencimiento());
            }
        }
        actuales.forEach((clave, existente) -> {
            if (!nuevas.contains(clave)) {
                ContextoAuditoria.registrarCambio(clave,
                        descripcion(Dinero.de(existente.getMonto()), existente.getFechaVencimiento()), null);
                cronograma.delete(existente);
            }
        });

        PoliticaCobro politica = politicas.findByAnioEscolarId(a.id()).orElse(null);
        if (politica == null) {
            politicas.save(new PoliticaCobro(a.id(), porcentajePenalidad));
            ContextoAuditoria.registrarCambio("porcentajePenalidad", null, porcentajePenalidad);
        } else if (politica.getPorcentajePenalidad().compareTo(porcentajePenalidad) != 0) {
            ContextoAuditoria.registrarCambio("porcentajePenalidad", politica.getPorcentajePenalidad(),
                    porcentajePenalidad);
            politica.actualizar(porcentajePenalidad);
        }
        cronograma.flush();
        int actualizadas = actualizarObligaciones(a.id(), lineas);
        ContextoAuditoria.registrarCambio("obligacionesPendientesActualizadas", null, actualizadas);
        ConfiguracionCobros vigente = obtener(anio);
        return new ConfiguracionCobros(vigente.anio(), vigente.porcentajePenalidad(), vigente.lineas(),
                vigente.configurado(), actualizadas);
    }

    /**
     * RF-07: las obligaciones PENDIENTES del año toman el monto y el vencimiento del cronograma. Las PAGADAS y las
     * VENCIDAS no cambian, las líneas nuevas no generan obligaciones retroactivas para quienes ya se matricularon y las
     * líneas eliminadas no borran las obligaciones ya generadas (S-28).
     */
    private int actualizarObligaciones(String anioEscolarId, List<LineaCobro> lineas) {
        Instant ahora = reloj.instant();
        int total = 0;
        for (LineaCobro l : lineas) {
            int n = obligaciones.actualizarPendientes(anioEscolarId, l.concepto(), l.periodo(), l.monto().valor(),
                    l.fechaVencimiento(), EstadoObligacion.PENDIENTE, ahora);
            if (n > 0) {
                ContextoAuditoria.registrarCambio("obligaciones " + clave(l.concepto(), l.periodo()), null,
                        n + " actualizadas a " + descripcion(l.monto(), l.fechaVencimiento()));
            }
            total += n;
        }
        return total;
    }

    private static void validar(int anio, BigDecimal porcentaje, List<LineaCobro> lineas) {
        if (porcentaje == null || porcentaje.signum() < 0 || porcentaje.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                    "El porcentaje de penalidad debe estar entre 0 y 100");
        }
        if (lineas.isEmpty()) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "El cronograma necesita al menos una línea");
        }
        Set<String> vistas = new HashSet<>();
        for (LineaCobro l : lineas) {
            if (!vistas.add(clave(l.concepto(), l.periodo()))) {
                throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                        "La línea " + l.concepto() + " " + l.periodo() + " está repetida");
            }
            if (l.monto().esMenorQue(Dinero.CERO)) {
                throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "El monto no puede ser negativo");
            }
            String esperado = l.concepto() == ConceptoPago.PENSION ? "^" + anio + "-(0[1-9]|1[0-2])$" : "^" + anio + "$";
            if (!l.periodo().matches(esperado)) {
                throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "El periodo " + l.periodo()
                        + " no corresponde al año " + anio + (l.concepto() == ConceptoPago.PENSION
                        ? " (formato AAAA-MM)" : " (el periodo es el año)"));
            }
        }
    }

    private static List<CronogramaCobro> ordenadas(List<CronogramaCobro> lineas) {
        return lineas.stream().sorted(Comparator.comparing(CronogramaCobro::getFechaVencimiento)
                .thenComparing(l -> l.getConcepto().ordinal())).toList();
    }

    private static LineaCobro aLinea(CronogramaCobro l) {
        return new LineaCobro(l.getConcepto(), l.getPeriodo(), Dinero.de(l.getMonto()), l.getFechaVencimiento());
    }

    private static String clave(CronogramaCobro l) {
        return clave(l.getConcepto(), l.getPeriodo());
    }

    private static String clave(ConceptoPago concepto, String periodo) {
        return concepto + " " + periodo;
    }

    private static String descripcion(Dinero monto, LocalDate vencimiento) {
        return "S/ " + monto + " · vence " + vencimiento;
    }
}
