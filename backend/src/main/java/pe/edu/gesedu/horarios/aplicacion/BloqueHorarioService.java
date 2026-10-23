package pe.edu.gesedu.horarios.aplicacion;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.horarios.dominio.BloqueHorario;
import pe.edu.gesedu.horarios.dominio.DiaSemana;
import pe.edu.gesedu.horarios.infraestructura.BloqueHorarioRepository;

/** Bloques horarios de un año escolar (RF-46). */
@Service
public class BloqueHorarioService {

    private static final Comparator<BloqueHorario> POR_DIA_Y_HORA =
            Comparator.comparing(BloqueHorario::getDiaSemana).thenComparing(BloqueHorario::getHoraInicio);

    private final BloqueHorarioRepository bloques;
    private final AnioEscolarService anios;

    BloqueHorarioService(BloqueHorarioRepository bloques, AnioEscolarService anios) {
        this.bloques = bloques;
        this.anios = anios;
    }

    @Transactional(readOnly = true)
    public List<BloqueHorarioDto> listar(int anio) {
        String anioId = anios.obtenerPorAnio(anio).id();
        return bloques.findByAnioEscolarId(anioId).stream().sorted(POR_DIA_Y_HORA).map(this::aDto).toList();
    }

    @Transactional
    @Auditable(accion = "CREAR_BLOQUE_HORARIO", modulo = "HORARIOS", tipoEntidad = "BloqueHorario",
            idEntidad = "#result.id")
    public BloqueHorarioDto crear(int anio, DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFin) {
        String anioId = anios.obtenerPorAnio(anio).id();
        BloqueHorario nuevo = new BloqueHorario(anioId, diaSemana, horaInicio, horaFin);

        List<BloqueHorarioDto> cruzados = bloques.findByAnioEscolarIdAndDiaSemana(anioId, diaSemana).stream()
                .filter(nuevo::seSuperponeCon).sorted(POR_DIA_Y_HORA).map(this::aDto).toList();
        if (!cruzados.isEmpty()) {
            throw conflicto(cruzados);
        }
        try {
            bloques.saveAndFlush(nuevo);
        } catch (DataIntegrityViolationException e) {
            // Otro administrador creó un bloque que se cruza justo ahora: la restricción EXCLUDE de la base lo detiene
            throw conflicto(List.of());
        }
        ContextoAuditoria.registrarCambio("anio", null, anio);
        ContextoAuditoria.registrarCambio("diaSemana", null, diaSemana);
        ContextoAuditoria.registrarCambio("horaInicio", null, horaInicio);
        ContextoAuditoria.registrarCambio("horaFin", null, horaFin);
        return aDto(nuevo);
    }

    private ExcepcionNegocio conflicto(List<BloqueHorarioDto> conflictos) {
        return new ExcepcionNegocio(CodigoError.CONFLICTO_HORARIO, "El bloque se superpone con otro del mismo día")
                .con("conflictos", conflictos);
    }

    private BloqueHorarioDto aDto(BloqueHorario b) {
        return new BloqueHorarioDto(b.getId(), b.getDiaSemana(), b.getHoraInicio(), b.getHoraFin());
    }
}
