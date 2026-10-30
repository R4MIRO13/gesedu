package pe.edu.gesedu.horarios.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.PeriodoDto;
import pe.edu.gesedu.horarios.dominio.PeriodoRegistroDisponibilidad;
import pe.edu.gesedu.horarios.infraestructura.PeriodoDisponibilidadRepository;

/** Periodo de registro de la disponibilidad docente del año (RF-46, UC-20 FE-1). */
@Service
public class PeriodoDisponibilidadService {

    private final AnioEscolarService anios;
    private final PeriodoDisponibilidadRepository periodos;
    private final Clock reloj;

    PeriodoDisponibilidadService(AnioEscolarService anios, PeriodoDisponibilidadRepository periodos, Clock reloj) {
        this.anios = anios;
        this.periodos = periodos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public PeriodoDto obtener(int anio) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        return dto(a.anio(), periodos.findByAnioEscolarId(a.id()));
    }

    /** El periodo del año si existe (UC-20 lo consulta para saber si el docente puede registrar). */
    @Transactional(readOnly = true)
    public PeriodoDto obtenerPorId(String anioEscolarId, int anio) {
        return dto(anio, periodos.findByAnioEscolarId(anioEscolarId));
    }

    @Transactional
    @Auditable(accion = "CONFIGURAR_PERIODO_DISPONIBILIDAD", modulo = "HORARIOS", tipoEntidad = "PeriodoRegistroDisponibilidad",
            idEntidad = "#anio")
    public PeriodoDto guardar(int anio, OffsetDateTime inicio, OffsetDateTime fin, boolean habilitado) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        if ("CERRADO".equals(a.estado())) {
            throw new ExcepcionNegocio(CodigoError.ANIO_CERRADO);
        }
        if (!fin.isAfter(inicio)) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "El periodo debe terminar después de empezar");
        }
        Optional<PeriodoRegistroDisponibilidad> existente = periodos.findByAnioEscolarId(a.id());
        PeriodoRegistroDisponibilidad periodo = existente.orElseGet(() ->
                new PeriodoRegistroDisponibilidad(a.id(), inicio.toInstant(), fin.toInstant(), habilitado));
        existente.ifPresent(p -> {
            ContextoAuditoria.registrarCambio("inicio", local(p.getFechaHoraInicio()), inicio);
            ContextoAuditoria.registrarCambio("fin", local(p.getFechaHoraFin()), fin);
            ContextoAuditoria.registrarCambio("estado", p.getEstado(), habilitado ? "HABILITADO" : "CERRADO");
            p.actualizar(inicio.toInstant(), fin.toInstant(), habilitado);
        });
        if (existente.isEmpty()) {
            ContextoAuditoria.registrarCambio("inicio", null, inicio);
            ContextoAuditoria.registrarCambio("fin", null, fin);
            ContextoAuditoria.registrarCambio("estado", null, habilitado ? "HABILITADO" : "CERRADO");
        }
        try {
            periodos.saveAndFlush(periodo);
        } catch (DataIntegrityViolationException e) {
            // Otro administrador lo creó al mismo tiempo (un solo periodo por año)
            throw new ExcepcionNegocio(CodigoError.CONFLICTO_CONCURRENCIA,
                    "Otra persona configuró el periodo al mismo tiempo; vuelve a intentarlo");
        }
        return dto(a.anio(), Optional.of(periodo));
    }

    private PeriodoDto dto(int anio, Optional<PeriodoRegistroDisponibilidad> periodo) {
        return periodo
                .map(p -> new PeriodoDto(anio, true, local(p.getFechaHoraInicio()), local(p.getFechaHoraFin()),
                        p.getEstado(), p.estaAbierto(reloj.instant())))
                .orElseGet(() -> new PeriodoDto(anio, false, null, null, null, false));
    }

    private OffsetDateTime local(Instant instante) {
        return instante.atZone(reloj.getZone()).toOffsetDateTime();
    }
}
