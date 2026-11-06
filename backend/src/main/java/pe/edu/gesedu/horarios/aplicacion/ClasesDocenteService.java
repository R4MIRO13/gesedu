package pe.edu.gesedu.horarios.aplicacion;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.horarios.dominio.AsignacionPublicada;
import pe.edu.gesedu.horarios.dominio.BloqueHorario;
import pe.edu.gesedu.horarios.dominio.DiaSemana;
import pe.edu.gesedu.horarios.dominio.HorarioPublicado;
import pe.edu.gesedu.horarios.infraestructura.AsignacionPublicadaRepository;
import pe.edu.gesedu.horarios.infraestructura.BloqueHorarioRepository;
import pe.edu.gesedu.horarios.infraestructura.HorarioPublicadoRepository;

/**
 * Lo que el módulo de asistencia necesita saber del horario publicado (UC-07, UC-12): si un día de la semana tiene
 * bloques configurados y, para un docente, cuándo empieza su primera clase. Solo lee la versión vigente, nunca el
 * borrador (RF-44).
 */
@Service
public class ClasesDocenteService {

    /**
     * Las clases de un docente en un día de la semana de un año.
     *
     * @param diaConBloques    el año tiene bloques configurados ese día de la semana (sin ellos no hay jornada)
     * @param horarioPublicado el año tiene una versión vigente del horario
     * @param primerBloque     hora de inicio de la primera clase del docente ese día; {@code null} si no dicta ese día
     */
    public record ClasesEnDia(boolean diaConBloques, boolean horarioPublicado, LocalTime primerBloque) {

        public boolean tieneClases() {
            return primerBloque != null;
        }
    }

    private final AnioEscolarService anios;
    private final BloqueHorarioRepository bloques;
    private final HorarioPublicadoRepository publicados;
    private final AsignacionPublicadaRepository publicadas;
    private final Clock reloj;

    ClasesDocenteService(AnioEscolarService anios, BloqueHorarioRepository bloques, HorarioPublicadoRepository publicados,
                         AsignacionPublicadaRepository publicadas, Clock reloj) {
        this.anios = anios;
        this.bloques = bloques;
        this.publicados = publicados;
        this.publicadas = publicadas;
        this.reloj = reloj;
    }

    /** Clases del docente el día de la semana indicado, según la versión vigente del horario del año. */
    @Transactional(readOnly = true)
    public ClasesEnDia clasesDelDocente(int anio, String docenteId, DayOfWeek dia) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        List<BloqueHorario> delDia = bloques.findByAnioEscolarIdAndDiaSemana(a.id(), aDiaSemana(dia));
        if (delDia.isEmpty()) {
            return new ClasesEnDia(false, false, null);
        }
        Optional<HorarioPublicado> vigente = publicados.findByAnioEscolarIdAndVigenteTrue(a.id());
        if (vigente.isEmpty()) {
            return new ClasesEnDia(true, false, null);
        }
        Map<String, BloqueHorario> porId = delDia.stream().collect(Collectors.toMap(BloqueHorario::getId, Function.identity()));
        LocalTime primero = publicadas.findByHorarioPublicadoIdAndDocenteId(vigente.get().getId(), docenteId).stream()
                .map(AsignacionPublicada::getBloqueId)
                .map(porId::get)
                .filter(b -> b != null)
                .map(BloqueHorario::getHoraInicio)
                .min(Comparator.naturalOrder())
                .orElse(null);
        return new ClasesEnDia(true, true, primero);
    }

    /**
     * Las clases de un día de la semana según la versión vigente del horario: qué docentes dictan y a qué hora empieza su
     * primera clase (UC-12, consulta de docentes por día).
     *
     * @param primerBloquePorDocente docente → inicio de su primera clase; vacío si no hay horario publicado o nadie dicta
     */
    public record JornadaDelDia(boolean diaConBloques, boolean horarioPublicado, Map<String, LocalTime> primerBloquePorDocente) {
    }

    @Transactional(readOnly = true)
    public JornadaDelDia jornadaDelDia(int anio, DayOfWeek dia) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        List<BloqueHorario> delDia = bloques.findByAnioEscolarIdAndDiaSemana(a.id(), aDiaSemana(dia));
        if (delDia.isEmpty()) {
            return new JornadaDelDia(false, false, Map.of());
        }
        Optional<HorarioPublicado> vigente = publicados.findByAnioEscolarIdAndVigenteTrue(a.id());
        if (vigente.isEmpty()) {
            return new JornadaDelDia(true, false, Map.of());
        }
        Map<String, BloqueHorario> porId = delDia.stream().collect(Collectors.toMap(BloqueHorario::getId, Function.identity()));
        Map<String, LocalTime> primeros = new java.util.TreeMap<>();
        for (AsignacionPublicada ap : publicadas.findByHorarioPublicadoId(vigente.get().getId())) {
            BloqueHorario bloque = porId.get(ap.getBloqueId());
            if (bloque != null) {
                primeros.merge(ap.getDocenteId(), bloque.getHoraInicio(), (x, y) -> x.isBefore(y) ? x : y);
            }
        }
        return new JornadaDelDia(true, true, primeros);
    }

    /**
     * Las jornadas de varios días según la versión del horario que estaba vigente en cada fecha (UC-10, meses pasados):
     * la última versión publicada hasta el final de ese día, en la zona institucional. Las versiones son inmutables, así
     * que basta resolver «versión vigente el día D». Un día anterior a la primera publicación no tiene horario
     * ({@code horarioPublicado = false}) y no aporta días esperados a ningún docente.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, JornadaDelDia> jornadasDelPeriodo(int anio, List<LocalDate> dias) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        List<HorarioPublicado> versiones = publicados.findByAnioEscolarIdOrderByNumeroVersionDesc(a.id());
        Map<DiaSemana, List<BloqueHorario>> bloquesPorDia = bloques.findByAnioEscolarId(a.id()).stream()
                .collect(Collectors.groupingBy(BloqueHorario::getDiaSemana));
        Map<String, List<AsignacionPublicada>> asignaciones = new java.util.HashMap<>();
        Map<LocalDate, JornadaDelDia> resultado = new java.util.LinkedHashMap<>();
        for (LocalDate dia : dias) {
            List<BloqueHorario> delDia = bloquesPorDia.getOrDefault(aDiaSemana(dia.getDayOfWeek()), List.of());
            if (delDia.isEmpty()) {
                resultado.put(dia, new JornadaDelDia(false, false, Map.of()));
                continue;
            }
            Instant finDelDia = dia.plusDays(1).atStartOfDay(reloj.getZone()).toInstant();
            Optional<HorarioPublicado> version = versiones.stream()
                    .filter(v -> v.getFechaHoraPublicacion().isBefore(finDelDia)).findFirst();
            if (version.isEmpty()) {
                resultado.put(dia, new JornadaDelDia(true, false, Map.of()));
                continue;
            }
            Map<String, BloqueHorario> porId = delDia.stream().collect(Collectors.toMap(BloqueHorario::getId, Function.identity()));
            Map<String, LocalTime> primeros = new java.util.TreeMap<>();
            for (AsignacionPublicada ap : asignaciones.computeIfAbsent(version.get().getId(), publicadas::findByHorarioPublicadoId)) {
                BloqueHorario bloque = porId.get(ap.getBloqueId());
                if (bloque != null) {
                    primeros.merge(ap.getDocenteId(), bloque.getHoraInicio(), (x, y) -> x.isBefore(y) ? x : y);
                }
            }
            resultado.put(dia, new JornadaDelDia(true, true, primeros));
        }
        return resultado;
    }

    /** El año tiene bloques configurados ese día de la semana, es decir, hay jornada escolar (S-57). */
    @Transactional(readOnly = true)
    public boolean hayJornada(int anio, DayOfWeek dia) {
        return !bloques.findByAnioEscolarIdAndDiaSemana(anios.obtenerPorAnio(anio).id(), aDiaSemana(dia)).isEmpty();
    }

    static DiaSemana aDiaSemana(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY -> DiaSemana.LUNES;
            case TUESDAY -> DiaSemana.MARTES;
            case WEDNESDAY -> DiaSemana.MIERCOLES;
            case THURSDAY -> DiaSemana.JUEVES;
            case FRIDAY -> DiaSemana.VIERNES;
            case SATURDAY -> DiaSemana.SABADO;
            case SUNDAY -> DiaSemana.DOMINGO;
        };
    }
}
