package pe.edu.gesedu.asistencia.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
import pe.edu.gesedu.asistencia.dominio.AsistenciaDocente;
import pe.edu.gesedu.asistencia.dominio.CierreDiario;
import pe.edu.gesedu.asistencia.dominio.EstadoAsistencia;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaDocenteRepository;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaProperties;
import pe.edu.gesedu.asistencia.infraestructura.CierreDiarioRepository;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.horarios.aplicacion.ClasesDocenteService;
import pe.edu.gesedu.horarios.aplicacion.ClasesDocenteService.JornadaDelDia;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;

/**
 * Asistencia de los docentes por día (UC-12, consulta del administrador): quién tenía clases, a qué hora empezaba su
 * primera clase, cómo marcó y cuáles son las faltas que generó el cierre. Solo lectura. Un docente programado que aún no
 * tiene registro figura «sin registrar»; nunca se convierte en falta al consultar.
 */
@Service
public class ConsultaAsistenciaDocenteService {

    /** Cómo está el cierre diario de la fecha. */
    public enum EstadoCierre {
        /** El cierre procesó la fecha con éxito. */
        PROCESADO,
        /** El cierre falló (por ejemplo, sin horario publicado) y se reintenta. */
        FALLIDO,
        /** Todavía no se procesó: la jornada sigue abierta o el cierre aún no corrió. */
        PENDIENTE
    }

    public record FilaDocenteDto(String docenteId, String nombre, LocalTime primerBloque, OffsetDateTime horaRegistro,
                                 EstadoAsistencia estado, boolean generadaAlCierre) {
    }

    public record TotalesDocentesDto(int puntual, int tardanza, int falta, int sinRegistrar) {
    }

    /**
     * La asistencia docente de una fecha. {@code diaLectivo} falso = sin jornada (S-57). {@code cierre} y
     * {@code detalleCierre} cuentan qué hizo el cierre diario.
     */
    public record AsistenciaDocenteDiaDto(LocalDate fecha, boolean diaLectivo, EstadoCierre cierre, String detalleCierre,
                                          TotalesDocentesDto totales, List<FilaDocenteDto> docentes) {
    }

    private final AsistenciaProperties propiedades;
    private final AsistenciaDocenteRepository asistencias;
    private final CierreDiarioRepository cierres;
    private final AnioEscolarService anios;
    private final ClasesDocenteService clasesDocente;
    private final DirectorioDocentes directorio;
    private final Clock reloj;

    ConsultaAsistenciaDocenteService(AsistenciaProperties propiedades, AsistenciaDocenteRepository asistencias,
                                     CierreDiarioRepository cierres, AnioEscolarService anios, ClasesDocenteService clasesDocente,
                                     DirectorioDocentes directorio, Clock reloj) {
        this.propiedades = propiedades;
        this.asistencias = asistencias;
        this.cierres = cierres;
        this.anios = anios;
        this.clasesDocente = clasesDocente;
        this.directorio = directorio;
        this.reloj = reloj;
    }

    /** @param fecha el día a consultar; {@code null} = hoy. No se consultan fechas futuras. */
    @Transactional(readOnly = true)
    public AsistenciaDocenteDiaDto delDia(LocalDate fecha) {
        ZonedDateTime ahora = reloj.instant().atZone(reloj.getZone());
        LocalDate dia = fecha != null ? fecha : ahora.toLocalDate();
        if (dia.isAfter(ahora.toLocalDate())) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA, "No se consulta la asistencia de una fecha futura");
        }

        Optional<AnioEscolarResumen> anio = anios.anioLectivoEn(dia);
        JornadaDelDia jornada = anio.map(a -> clasesDocente.jornadaDelDia(a.anio(), dia.getDayOfWeek())).orElse(null);
        boolean lectivo = jornada != null && jornada.diaConBloques();
        Map<String, LocalTime> programados = lectivo ? jornada.primerBloquePorDocente() : Map.of();
        Map<String, AsistenciaDocente> registros = asistencias.findByFecha(dia).stream()
                .collect(Collectors.toMap(AsistenciaDocente::getDocenteId, Function.identity()));

        Set<String> ids = new HashSet<>(programados.keySet());
        ids.addAll(registros.keySet());
        Map<String, DocenteResumen> nombres = directorio.porIds(ids);
        List<FilaDocenteDto> filas = new ArrayList<>();
        for (String id : ids) {
            AsistenciaDocente r = registros.get(id);
            DocenteResumen d = nombres.get(id);
            filas.add(new FilaDocenteDto(id, d == null ? "—" : d.apellidos() + ", " + d.nombres(), programados.get(id),
                    r == null || r.getHoraRegistro() == null ? null : enZona(r.getHoraRegistro()),
                    r == null ? null : r.getEstado(), r != null && r.getEstado() == EstadoAsistencia.FALTA && r.getHoraRegistro() == null));
        }
        filas.sort(Comparator.comparing(FilaDocenteDto::nombre, String.CASE_INSENSITIVE_ORDER));

        int puntual = contar(filas, EstadoAsistencia.PUNTUAL);
        int tardanza = contar(filas, EstadoAsistencia.TARDANZA);
        int falta = contar(filas, EstadoAsistencia.FALTA);
        TotalesDocentesDto totales = new TotalesDocentesDto(puntual, tardanza, falta, (int) filas.stream().filter(f -> f.estado() == null).count());

        Optional<CierreDiario> cierre = cierres.findByFecha(dia);
        EstadoCierre estadoCierre = cierre.map(c -> CierreDiario.EXITOSO.equals(c.getEstado()) ? EstadoCierre.PROCESADO : EstadoCierre.FALLIDO)
                .orElse(EstadoCierre.PENDIENTE);
        return new AsistenciaDocenteDiaDto(dia, lectivo, estadoCierre, cierre.map(CierreDiario::getDetalle).orElse(null), totales, filas);
    }

    private static int contar(List<FilaDocenteDto> filas, EstadoAsistencia estado) {
        return (int) filas.stream().filter(f -> f.estado() == estado).count();
    }

    private OffsetDateTime enZona(Instant instante) {
        return instante.atZone(reloj.getZone()).toOffsetDateTime();
    }
}
