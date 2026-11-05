package pe.edu.gesedu.asistencia.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.asistencia.dominio.CierreDiario;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaDocenteRepository;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaProperties;
import pe.edu.gesedu.asistencia.infraestructura.CierreDiarioRepository;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.horarios.aplicacion.ClasesDocenteService;
import pe.edu.gesedu.horarios.aplicacion.ClasesDocenteService.JornadaDelDia;

/**
 * UC-12, Registrar faltas de docentes (RF-13, RF-19, RF-20): al llegar {@code SchoolDayClosingTime}, cada docente con
 * clases programadas ese día que no marcó recibe una FALTA, sin hora de registro. Solo completa a los docentes: nunca
 * toca una marcación existente ni crea registros de alumnos (la ausencia de registro de un alumno no es falta).
 * <p>
 * Es idempotente y tolera reintentos y ejecuciones paralelas: la unicidad docente-fecha de la base evita duplicados
 * y la constancia de {@code cierre_diario} evita repetir el trabajo de una fecha ya procesada. Si falta el calendario o
 * el horario publicado (FA-4) no inventa faltas: anota el fallo y la fecha se vuelve a intentar en el siguiente disparo.
 */
@Service
public class CierreDiarioService {

    /** Qué hizo el cierre con una fecha. */
    public enum Resultado {
        /** La jornada de esa fecha aún no cierra: no se hace nada ni se anota nada. */
        JORNADA_ABIERTA,
        /** La fecha ya se había procesado con éxito: no se repite el trabajo. */
        YA_PROCESADO,
        /** No era día lectivo: termina sin crear registros (FA-1). */
        DIA_NO_LECTIVO,
        /** Faltó el calendario o el horario publicado: se anotó el fallo y no se crearon faltas (FA-4). */
        FALLIDO,
        /** Se completaron las faltas de los docentes que no marcaron (puede ser ninguna: FA-2). */
        COMPLETADO
    }

    /** Resultado del cierre de una fecha; {@code faltasCreadas} cuenta solo las creadas en esta ejecución. */
    public record ResultadoCierre(LocalDate fecha, Resultado resultado, int faltasCreadas, int docentesConClases, String detalle) {
    }

    private final AsistenciaProperties propiedades;
    private final AsistenciaDocenteRepository asistencias;
    private final CierreDiarioRepository cierres;
    private final AnioEscolarService anios;
    private final ClasesDocenteService clasesDocente;
    private final Clock reloj;

    CierreDiarioService(AsistenciaProperties propiedades, AsistenciaDocenteRepository asistencias, CierreDiarioRepository cierres,
                        AnioEscolarService anios, ClasesDocenteService clasesDocente, Clock reloj) {
        this.propiedades = propiedades;
        this.asistencias = asistencias;
        this.cierres = cierres;
        this.anios = anios;
        this.clasesDocente = clasesDocente;
        this.reloj = reloj;
    }

    /**
     * Procesa el cierre de la fecha. Se audita como «Sistema» solo si creó faltas: lo que no cambia nada no deja rastro en
     * la bitácora (el avance queda en {@code cierre_diario}).
     */
    @Transactional
    @Auditable(accion = "CERRAR_ASISTENCIA_DOCENTE", modulo = "ASISTENCIA", tipoEntidad = "AsistenciaDocente",
            descripcion = "Cierre diario: faltas de los docentes con clases que no marcaron (proceso automático)",
            condicion = "#result.faltasCreadas() > 0")
    public ResultadoCierre procesar(LocalDate fecha) {
        Instant ahora = reloj.instant();
        ZonedDateTime local = ahora.atZone(reloj.getZone());
        LocalTime cierre = propiedades.schoolDayClosingTime();
        // Precondición de UC-12: se ha alcanzado SchoolDayClosingTime en la zona institucional
        if (fecha.isAfter(local.toLocalDate()) || (fecha.equals(local.toLocalDate()) && local.toLocalTime().isBefore(cierre))) {
            return new ResultadoCierre(fecha, Resultado.JORNADA_ABIERTA, 0, 0, "La jornada aún no cierra");
        }
        Optional<CierreDiario> previo = cierres.findByFecha(fecha);
        if (previo.isPresent() && CierreDiario.EXITOSO.equals(previo.get().getEstado())) {
            return new ResultadoCierre(fecha, Resultado.YA_PROCESADO, 0, 0, previo.get().getDetalle());
        }

        if (!anios.existeAnioActivo()) {
            return fallo(fecha, ahora, "No hay un año escolar activo: no se puede determinar si la fecha es lectiva");
        }
        Optional<AnioEscolarResumen> anio = anios.anioLectivoEn(fecha);
        if (anio.isEmpty()) {
            return exito(fecha, ahora, Resultado.DIA_NO_LECTIVO, 0, 0, "Día no lectivo: no se crean registros");
        }
        JornadaDelDia jornada = clasesDocente.jornadaDelDia(anio.get().anio(), fecha.getDayOfWeek());
        if (!jornada.diaConBloques()) {
            return exito(fecha, ahora, Resultado.DIA_NO_LECTIVO, 0, 0, "Día no lectivo: no se crean registros");
        }
        if (!jornada.horarioPublicado()) {
            return fallo(fecha, ahora, "No hay un horario publicado para el año " + anio.get().anio()
                    + ": no se crean faltas basadas en supuestos");
        }

        int creadas = 0;
        for (String docenteId : jornada.primerBloquePorDocente().keySet()) {
            creadas += asistencias.insertarFaltaSiNoExiste(docenteId, fecha);
        }
        int conClases = jornada.primerBloquePorDocente().size();
        return exito(fecha, ahora, Resultado.COMPLETADO, creadas, conClases,
                creadas + (creadas == 1 ? " falta creada" : " faltas creadas") + " de " + conClases
                        + (conClases == 1 ? " docente con clases" : " docentes con clases"));
    }

    private ResultadoCierre exito(LocalDate fecha, Instant ahora, Resultado resultado, int creadas, int conClases, String detalle) {
        cierres.registrar(fecha, CierreDiario.EXITOSO, detalle, ahora);
        return new ResultadoCierre(fecha, resultado, creadas, conClases, detalle);
    }

    private ResultadoCierre fallo(LocalDate fecha, Instant ahora, String detalle) {
        cierres.registrar(fecha, CierreDiario.FALLIDO, detalle, ahora);
        return new ResultadoCierre(fecha, Resultado.FALLIDO, 0, 0, detalle);
    }
}
