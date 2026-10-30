package pe.edu.gesedu.horarios.aplicacion;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
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

    /**
     * Elimina un bloque del año que nadie usa. Si ya hay disponibilidades, clases del borrador o horarios publicados que
     * lo referencian, las llaves foráneas de la base lo impiden y responde 409 {@code BLOQUE_EN_USO}.
     */
    @Transactional
    @Auditable(accion = "ELIMINAR_BLOQUE_HORARIO", modulo = "HORARIOS", tipoEntidad = "BloqueHorario", idEntidad = "#bloqueId")
    public void eliminar(int anio, String bloqueId) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        if ("CERRADO".equals(a.estado())) {
            throw new ExcepcionNegocio(CodigoError.ANIO_CERRADO);
        }
        BloqueHorario bloque = bloques.findById(bloqueId).filter(b -> b.getAnioEscolarId().equals(a.id()))
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el bloque en el año " + anio));
        try {
            bloques.delete(bloque);
            bloques.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ExcepcionNegocio(CodigoError.BLOQUE_EN_USO,
                    "El bloque ya tiene clases o disponibilidades registradas y no se puede eliminar");
        }
        ContextoAuditoria.registrarCambio("anio", anio, null);
        ContextoAuditoria.registrarCambio("diaSemana", bloque.getDiaSemana(), null);
        ContextoAuditoria.registrarCambio("horaInicio", bloque.getHoraInicio(), null);
        ContextoAuditoria.registrarCambio("horaFin", bloque.getHoraFin(), null);
    }

    private ExcepcionNegocio conflicto(List<BloqueHorarioDto> conflictos) {
        return new ExcepcionNegocio(CodigoError.CONFLICTO_HORARIO, "El bloque se superpone con otro del mismo día")
                .con("conflictos", conflictos);
    }

    private BloqueHorarioDto aDto(BloqueHorario b) {
        return new BloqueHorarioDto(b.getId(), b.getDiaSemana(), b.getHoraInicio(), b.getHoraFin());
    }
}
