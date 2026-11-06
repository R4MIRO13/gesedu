package pe.edu.gesedu.academico.aplicacion;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.ConfiguracionAcademicaDtos.AnioDetalleDto;
import pe.edu.gesedu.academico.aplicacion.ConfiguracionAcademicaDtos.AnioResumenDto;
import pe.edu.gesedu.academico.aplicacion.ConfiguracionAcademicaDtos.BimestreDto;
import pe.edu.gesedu.academico.aplicacion.ConfiguracionAcademicaDtos.DuracionDto;
import pe.edu.gesedu.academico.aplicacion.ConfiguracionAcademicaDtos.ProblemaDto;
import pe.edu.gesedu.academico.aplicacion.ConfiguracionAcademicaDtos.RangoBimestreSolicitud;
import pe.edu.gesedu.academico.aplicacion.ConfiguracionAcademicaDtos.ValidacionAnioDto;
import pe.edu.gesedu.academico.dominio.AnioEscolar;
import pe.edu.gesedu.academico.dominio.Bimestre;
import pe.edu.gesedu.academico.dominio.Grado;
import pe.edu.gesedu.academico.dominio.ValidadorAnioEscolar;
import pe.edu.gesedu.academico.dominio.ValidadorAnioEscolar.Problema;
import pe.edu.gesedu.academico.dominio.ValidadorAnioEscolar.RangoBimestre;
import pe.edu.gesedu.academico.infraestructura.AnioEscolarRepository;
import pe.edu.gesedu.academico.infraestructura.BimestreRepository;
import pe.edu.gesedu.academico.infraestructura.GradoRepository;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;

/**
 * Creación del año escolar con sus cuatro bimestres (UC-13, RF-03, RF-25). El año queda PLANIFICADO y con sus cinco
 * grados (1° a 5°); las secciones, los cursos y los horarios se configuran después (UC-14, RF-04, RF-46).
 */
@Service
public class ConfiguracionAnioService {

    private static final int ANIO_MINIMO = 2000;
    private static final int ANIO_MAXIMO = 2999;
    private static final int GRADOS = 5;

    private final AnioEscolarRepository anios;
    private final BimestreRepository bimestres;
    private final GradoRepository grados;

    ConfiguracionAnioService(AnioEscolarRepository anios, BimestreRepository bimestres, GradoRepository grados) {
        this.anios = anios;
        this.bimestres = bimestres;
        this.grados = grados;
    }

    /** Años escolares del más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public List<AnioResumenDto> listar() {
        return anios.findAll().stream()
                .sorted(Comparator.comparingInt(AnioEscolar::getAnio).reversed())
                .map(a -> new AnioResumenDto(a.getId(), a.getAnio(), a.getFechaInicio(), a.getFechaFin(), a.getEstado()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AnioDetalleDto obtener(int anio) {
        AnioEscolar a = anios.findByAnio(anio).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el año escolar " + anio));
        return detalle(a, bimestres.findByAnioEscolarIdOrderByNumero(a.getId()));
    }

    /**
     * Pasos 4 y 7: valida las fechas sin guardar nada. Sin bimestres solo valida el año (paso 1 de la pantalla);
     * con ellos, valida también cada bimestre y los cruces.
     */
    @Transactional(readOnly = true)
    public ValidacionAnioDto validar(LocalDate inicio, LocalDate fin, List<RangoBimestreSolicitud> bimestresPedidos) {
        List<Problema> problemas = problemas(inicio, fin, bimestresPedidos);
        return new ValidacionAnioDto(problemas.isEmpty(), duracion(inicio, fin),
                problemas.stream().map(ConfiguracionAnioService::aDto).toList());
    }

    /**
     * Pasos 3 a 8: registra el año con sus cuatro bimestres si las fechas son válidas. Los problemas viajan en
     * {@code problemas[]}: 422 RANGO_ANIO_INVALIDO (FA-1) o BIMESTRES_SUPERPUESTOS (FA-2).
     */
    @Transactional
    @Auditable(accion = "CREAR_ANIO_ESCOLAR", modulo = "ACADEMICO", tipoEntidad = "AnioEscolar", idEntidad = "#result.id()")
    public AnioDetalleDto crear(LocalDate inicio, LocalDate fin, List<RangoBimestreSolicitud> bimestresPedidos) {
        Set<Integer> numeros = bimestresPedidos.stream().map(RangoBimestreSolicitud::numero).collect(Collectors.toSet());
        if (bimestresPedidos.size() != 4 || !numeros.equals(Set.of(1, 2, 3, 4))) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                    "Se necesitan exactamente los bimestres 1, 2, 3 y 4");
        }
        List<Problema> problemas = problemas(inicio, fin, bimestresPedidos);
        if (!problemas.isEmpty()) {
            throw rechazo(problemas);
        }

        int numeroAnio = inicio.getYear();
        if (anios.findByAnio(numeroAnio).isPresent()) {
            throw new ExcepcionNegocio(CodigoError.ANIO_ESCOLAR_DUPLICADO, "Ya existe el año escolar " + numeroAnio);
        }
        AnioEscolar nuevo = new AnioEscolar(numeroAnio, inicio, fin, "PLANIFICADO");
        try {
            anios.saveAndFlush(nuevo);
        } catch (DataIntegrityViolationException e) {
            // Otro directivo lo creó justo ahora (índice único del año)
            throw new ExcepcionNegocio(CodigoError.ANIO_ESCOLAR_DUPLICADO, "Ya existe el año escolar " + numeroAnio);
        }
        List<Bimestre> creados = bimestresPedidos.stream().sorted(Comparator.comparingInt(RangoBimestreSolicitud::numero))
                .map(b -> new Bimestre(nuevo.getId(), b.numero(), b.fechaInicio(), b.fechaFin())).toList();
        try {
            bimestres.saveAllAndFlush(creados);
        } catch (DataIntegrityViolationException e) {
            // La restricción EXCLUDE de la base es la última defensa contra los cruces
            throw new ExcepcionNegocio(CodigoError.BIMESTRES_SUPERPUESTOS,
                    "Los bimestres se superponen; corrige las fechas");
        }
        grados.saveAll(IntStream.rangeClosed(1, GRADOS).mapToObj(n -> new Grado(nuevo.getId(), n)).toList());

        ContextoAuditoria.registrarCambio("anio", null, numeroAnio);
        ContextoAuditoria.registrarCambio("fechaInicio", null, inicio);
        ContextoAuditoria.registrarCambio("fechaFin", null, fin);
        creados.forEach(b -> ContextoAuditoria.registrarCambio("bimestre " + b.getNumero(), null,
                b.getFechaInicio() + " a " + b.getFechaFin()));
        return detalle(nuevo, creados);
    }

    /** El año escolar ACTIVO, el que usa la SPA como año de trabajo (C-47); 404 si ninguno lo está. */
    @Transactional(readOnly = true)
    public AnioResumenDto activo() {
        return anios.findByEstado("ACTIVO").stream().findFirst()
                .map(a -> new AnioResumenDto(a.getId(), a.getAnio(), a.getFechaInicio(), a.getFechaFin(), a.getEstado()))
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO,
                        "No hay un año escolar activo"));
    }

    /**
     * Activa un año PLANIFICADO y cierra el ACTIVO anterior, todo en una sola transacción (C-47, extensión de UC-13).
     * Un año CERRADO no se reabre (422 ANIO_CERRADO) y activar el que ya está activo es un error. El índice único
     * de la base es la última defensa: dos activaciones simultáneas de años distintos dejan uno solo (la otra, 409).
     */
    @Transactional
    @Auditable(accion = "ACTIVAR_ANIO_ESCOLAR", modulo = "ACADEMICO", tipoEntidad = "AnioEscolar", idEntidad = "#result.id()",
            descripcion = "Activación del año escolar y cierre del anterior")
    public AnioResumenDto activar(int anio) {
        AnioEscolar nuevo = anios.findByAnio(anio).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el año escolar " + anio));
        if ("CERRADO".equals(nuevo.getEstado())) {
            throw new ExcepcionNegocio(CodigoError.ANIO_CERRADO, "El año escolar " + anio + " está cerrado y no se reactiva");
        }
        if ("ACTIVO".equals(nuevo.getEstado())) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA, "El año escolar " + anio + " ya está activo");
        }
        try {
            for (AnioEscolar anterior : anios.findByEstado("ACTIVO")) {
                anterior.cerrar();
                anios.saveAndFlush(anterior);
                ContextoAuditoria.registrarCambio("anio " + anterior.getAnio(), "ACTIVO", "CERRADO");
            }
            nuevo.activar();
            anios.saveAndFlush(nuevo);
        } catch (DataIntegrityViolationException e) {
            throw new ExcepcionNegocio(CodigoError.CONFLICTO_CONCURRENCIA,
                    "Otro administrador activó un año escolar al mismo tiempo; revisa el estado e inténtalo de nuevo");
        }
        ContextoAuditoria.registrarCambio("anio " + anio, "PLANIFICADO", "ACTIVO");
        return new AnioResumenDto(nuevo.getId(), nuevo.getAnio(), nuevo.getFechaInicio(), nuevo.getFechaFin(), nuevo.getEstado());
    }

    private List<Problema> problemas(LocalDate inicio, LocalDate fin, List<RangoBimestreSolicitud> pedidos) {
        List<Problema> problemas = new java.util.ArrayList<>();
        if (inicio.getYear() < ANIO_MINIMO || inicio.getYear() > ANIO_MAXIMO) {
            problemas.add(new Problema(ValidadorAnioEscolar.Tipo.ANIO_FUERA_DE_DURACION, null, null, inicio, fin,
                    "El año escolar debe empezar entre los años " + ANIO_MINIMO + " y " + ANIO_MAXIMO));
        }
        problemas.addAll(ValidadorAnioEscolar.validarAnio(inicio, fin));
        // Sin un año válido no se pueden juzgar los bimestres contra él (FA-1: el flujo sigue en el paso 5)
        if (pedidos != null && !pedidos.isEmpty() && problemas.isEmpty()) {
            problemas.addAll(ValidadorAnioEscolar.validarBimestres(inicio, fin, pedidos.stream()
                    .map(b -> new RangoBimestre(b.numero(), b.fechaInicio(), b.fechaFin())).toList()));
        }
        return problemas;
    }

    private static ExcepcionNegocio rechazo(List<Problema> problemas) {
        CodigoError codigo = problemas.stream().anyMatch(Problema::esDelAnio)
                ? CodigoError.RANGO_ANIO_INVALIDO : CodigoError.BIMESTRES_SUPERPUESTOS;
        return new ExcepcionNegocio(codigo, problemas.getFirst().mensaje())
                .con("problemas", problemas.stream().map(ConfiguracionAnioService::aDto).toList());
    }

    private static DuracionDto duracion(LocalDate inicio, LocalDate fin) {
        if (!fin.isAfter(inicio)) {
            return new DuracionDto(0, 0);
        }
        ValidadorAnioEscolar.Duracion d = ValidadorAnioEscolar.duracion(inicio, fin);
        return new DuracionDto(d.meses(), d.dias());
    }

    private static ProblemaDto aDto(Problema p) {
        return new ProblemaDto(p.tipo().name(), p.bimestre(), p.otroBimestre(), p.desde(), p.hasta(), p.mensaje());
    }

    private static AnioDetalleDto detalle(AnioEscolar a, List<Bimestre> bimestres) {
        return new AnioDetalleDto(a.getId(), a.getAnio(), a.getFechaInicio(), a.getFechaFin(), a.getEstado(),
                bimestres.stream().map(b -> new BimestreDto(b.getNumero(), b.getFechaInicio(), b.getFechaFin())).toList());
    }
}
