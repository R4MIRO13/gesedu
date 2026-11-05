package pe.edu.gesedu.asistencia.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.asistencia.aplicacion.AsistenciaSeccionDtos.RegistroAlumnoDto;
import pe.edu.gesedu.asistencia.dominio.AsistenciaAlumno;
import pe.edu.gesedu.asistencia.dominio.EstadoAsistencia;
import pe.edu.gesedu.asistencia.dominio.PlazoTardanza;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaAlumnoRepository;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaProperties;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;

/**
 * UC-09, Actualizar estado de asistencia (RF-15). El tutor cambia de FALTA a TARDANZA al alumno de su sección que se
 * incorpora después de pasar lista, siempre que sea la asistencia de hoy, la hora del servidor no pase de
 * {@code StudentLateCutoffTime} y la jornada siga abierta. Se modifica el mismo registro y su {@code updated_at}: no hay
 * historial de estados en el módulo (C-07), pero el cambio sí queda en la auditoría (RF-49).
 */
@Service
public class ActualizacionAsistenciaService {

    private final AsistenciaProperties propiedades;
    private final AsistenciaAlumnoRepository registros;
    private final AsistenciaSeccionService secciones;
    private final Clock reloj;

    ActualizacionAsistenciaService(AsistenciaProperties propiedades, AsistenciaAlumnoRepository registros,
                                   AsistenciaSeccionService secciones, Clock reloj) {
        this.propiedades = propiedades;
        this.registros = registros;
        this.secciones = secciones;
        this.reloj = reloj;
    }

    /**
     * Pasos 2 a 5. Un registro de otra sección: 403 (FA-3); que no es de hoy o ya es PUNTUAL: 400 sin cambios (FA-2); que
     * ya es TARDANZA: 200 con el resultado existente y {@code modificado} falso (FA-2); pasada la hora límite o con la
     * jornada cerrada: 422 {@code PLAZO_TARDANZA_EXCEDIDO} y sigue FALTA (FA-1).
     */
    @Transactional
    @Auditable(accion = "CAMBIAR_ASISTENCIA_A_TARDANZA", modulo = "ASISTENCIA", tipoEntidad = "AsistenciaAlumno",
            idEntidad = "#id", descripcion = "Cambio de asistencia de alumno de Falta a Tardanza", condicion = "#result.modificado()")
    public RegistroAlumnoDto cambiarATardanza(String id, EstadoAsistencia nuevoEstado) {
        if (nuevoEstado != EstadoAsistencia.TARDANZA) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "Solo se puede actualizar a Tardanza")
                    .con("errores", List.of(Map.of("campo", "estado", "mensaje", "Solo se admite TARDANZA")));
        }
        Instant ahora = reloj.instant();
        ZonedDateTime local = ahora.atZone(reloj.getZone());
        LocalDate hoy = local.toLocalDate();
        LocalTime hora = local.toLocalTime();

        // El alcance se verifica antes de mirar el registro: un tutor no distingue «no existe» de «no es de mi sección»
        var alumnosDeMiSeccion = secciones.alumnoIdsDeMiSeccion();
        AsistenciaAlumno registro = registros.findById(id).filter(r -> alumnosDeMiSeccion.contains(r.getAlumnoId()))
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE,
                        "El registro no corresponde a un alumno de tu sección"));

        if (!registro.getFecha().equals(hoy)) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA,
                    "Solo se puede actualizar la asistencia de hoy: este registro no se modifica.");
        }
        if (registro.getEstado() == EstadoAsistencia.TARDANZA) {
            return aDto(registro, ahora, false);
        }
        if (registro.getEstado() != EstadoAsistencia.FALTA) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA,
                    "Solo se puede cambiar a Tardanza una asistencia en Falta: este registro no se modifica.");
        }
        // El límite es inclusivo; como StudentLateCutoffTime < SchoolDayClosingTime, pasado el cierre también se rechaza
        if (!PlazoTardanza.admite(hora, propiedades.studentLateCutoffTime())) {
            throw new ExcepcionNegocio(CodigoError.PLAZO_TARDANZA_EXCEDIDO, "Ya no se admite la actualización: el plazo para "
                    + "cambiar a Tardanza venció a las " + propiedades.studentLateCutoffTime().toString().substring(0, 5)
                    + " y el alumno permanece en Falta.");
        }
        if (registros.cambiarFaltaATardanza(id, hoy, ahora) == 0) {
            // Otro cambio simultáneo lo atendió primero: si ya está en TARDANZA se muestra, no es un error
            AsistenciaAlumno vigente = registros.findById(id).orElseThrow();
            if (vigente.getEstado() == EstadoAsistencia.TARDANZA) {
                return aDto(vigente, ahora, false);
            }
            throw new ExcepcionNegocio(CodigoError.CONFLICTO_CONCURRENCIA, "El registro cambió: recarga e inténtalo de nuevo");
        }
        ContextoAuditoria.registrarCambio("estado", EstadoAsistencia.FALTA, EstadoAsistencia.TARDANZA);
        return new RegistroAlumnoDto(id, registro.getAlumnoId(), hoy, EstadoAsistencia.TARDANZA, enZona(ahora), true);
    }

    private RegistroAlumnoDto aDto(AsistenciaAlumno r, Instant ahora, boolean modificado) {
        Instant actualizado = r.getUpdatedAt() != null ? r.getUpdatedAt() : ahora;
        return new RegistroAlumnoDto(r.getId(), r.getAlumnoId(), r.getFecha(), r.getEstado(), enZona(actualizado), modificado);
    }

    private OffsetDateTime enZona(Instant instante) {
        return instante.atZone(reloj.getZone()).toOffsetDateTime();
    }
}
