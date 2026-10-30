package pe.edu.gesedu.horarios.aplicacion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.CursoResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.SeccionResumen;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.AsignacionDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.DocenteDto;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.Perspectiva;
import pe.edu.gesedu.horarios.aplicacion.BorradorHorarioDtos.SeccionDto;
import pe.edu.gesedu.horarios.dominio.AsignacionPublicada;
import pe.edu.gesedu.horarios.dominio.BloqueHorario;
import pe.edu.gesedu.horarios.dominio.HorarioPublicado;
import pe.edu.gesedu.horarios.infraestructura.AsignacionPublicadaRepository;
import pe.edu.gesedu.horarios.infraestructura.BloqueHorarioRepository;
import pe.edu.gesedu.horarios.infraestructura.HorarioPublicadoRepository;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService.SeccionDeAlumno;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;

/**
 * UC-23, Consultar horario (RF-39, RF-45, RNF-03, RNF-07): la última versión publicada, vista por sección o por docente
 * según el alcance de quien consulta. El administrador ve todo; el docente, sus clases y las secciones donde dicta o es
 * tutor; el alumno y el apoderado, solo la sección de su alcance. Nunca se muestra el borrador (RF-44).
 * <p>
 * El horario publicado es inmutable, así que se arma una vez por año y se guarda en caché; la publicación de una
 * versión nueva la invalida. El alcance se aplica sobre la copia en memoria, por petición.
 */
@Service
public class ConsultaHorarioService {

    /** Todo el horario vigente de un año, ya con los nombres resueltos (es lo que vive en la caché). */
    record HorarioVigenteCompleto(int anio, int numeroVersion, OffsetDateTime vigenteDesde, List<BloqueHorarioDto> bloques,
                                  List<AsignacionDto> asignaciones, Map<String, String> tutorPorSeccion) {
    }

    /**
     * Lo que ve quien consulta. {@code perspectivas} son las que su rol permite; {@code secciones} y {@code docentes}
     * son las opciones que puede elegir; {@code asignaciones} son las de la selección actual.
     */
    public record HorarioVigenteDto(int anio, int numeroVersion, OffsetDateTime vigenteDesde, Perspectiva perspectiva,
                                    List<Perspectiva> perspectivas, String seccionId, String docenteId,
                                    List<BloqueHorarioDto> bloques, List<SeccionDto> secciones, List<DocenteDto> docentes,
                                    List<AsignacionDto> asignaciones) {
    }

    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final DirectorioDocentes directorio;
    private final ContextoUsuario contexto;
    private final ConsultaMatriculaService matriculas;
    private final HorarioPublicadoRepository publicados;
    private final AsignacionPublicadaRepository publicadas;
    private final BloqueHorarioRepository bloques;
    private final CacheHorarioVigente cache;
    private final Clock reloj;

    ConsultaHorarioService(AnioEscolarService anios, CatalogoAcademicoService catalogo, DirectorioDocentes directorio,
                           ContextoUsuario contexto, ConsultaMatriculaService matriculas, HorarioPublicadoRepository publicados,
                           AsignacionPublicadaRepository publicadas, BloqueHorarioRepository bloques, CacheHorarioVigente cache,
                           Clock reloj) {
        this.anios = anios;
        this.catalogo = catalogo;
        this.directorio = directorio;
        this.contexto = contexto;
        this.matriculas = matriculas;
        this.publicados = publicados;
        this.publicadas = publicadas;
        this.bloques = bloques;
        this.cache = cache;
        this.reloj = reloj;
    }

    /**
     * @param perspectiva    SECCION o DOCENTE; sin indicar, la principal del rol
     * @param seccionId      sección elegida; sin indicar, la primera de las que puede ver
     * @param docenteId      docente elegido (solo el administrador elige otro distinto de sí mismo)
     * @throws ExcepcionNegocio HORARIO_NO_PUBLICADO (404) si el año no tiene una versión vigente (FA-1);
     *                          FUERA_DE_ALCANCE (403) si pide algo que su rol no le permite
     */
    @Transactional(readOnly = true)
    public HorarioVigenteDto consultar(int anio, Perspectiva perspectiva, String seccionId, String docenteId) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        HorarioVigenteCompleto horario = cache.<Optional<HorarioVigenteCompleto>>obtener(anio, () -> cargar(a))
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.HORARIO_NO_PUBLICADO,
                        "El horario " + anio + " aún no ha sido publicado"));
        String usuarioId = contexto.idUsuarioActual().orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO));

        Alcance alcance = alcance(usuarioId, horario);
        Perspectiva elegida = perspectiva != null ? perspectiva : alcance.perspectivas().getFirst();
        if (!alcance.perspectivas().contains(elegida)) {
            throw fuera("Tu rol no puede ver el horario por " + (elegida == Perspectiva.DOCENTE ? "docente" : "sección"));
        }

        String seccionElegida = null;
        String docenteElegido = null;
        List<AsignacionDto> visibles;
        if (elegida == Perspectiva.SECCION) {
            final String seccion = seccionId != null ? seccionId
                    : alcance.secciones().stream().findFirst().map(SeccionDto::id).orElse(null);
            if (seccion != null && alcance.secciones().stream().noneMatch(s -> s.id().equals(seccion))) {
                throw fuera("La sección no está dentro de tu alcance");
            }
            seccionElegida = seccion;
            visibles = horario.asignaciones().stream().filter(x -> x.seccionId().equals(seccion)).toList();
        } else {
            final String docente = docenteId != null ? docenteId
                    : alcance.docentes().stream().findFirst().map(DocenteDto::id).orElse(null);
            if (docente != null && alcance.docentes().stream().noneMatch(d -> d.id().equals(docente))) {
                throw fuera("Solo puedes ver tus propias clases");
            }
            docenteElegido = docente;
            visibles = horario.asignaciones().stream().filter(x -> x.docenteId().equals(docente)).toList();
        }
        return new HorarioVigenteDto(horario.anio(), horario.numeroVersion(), horario.vigenteDesde(), elegida, alcance.perspectivas(),
                seccionElegida, docenteElegido, horario.bloques(), alcance.secciones(), alcance.docentes(), visibles);
    }

    // ---------------------------------------------------------------------------------------------------------

    private record Alcance(List<Perspectiva> perspectivas, List<SeccionDto> secciones, List<DocenteDto> docentes) {
    }

    private Alcance alcance(String usuarioId, HorarioVigenteCompleto horario) {
        List<SeccionDto> todasSecciones = horario.asignaciones().stream()
                .map(x -> new SeccionDto(x.seccionId(), x.seccion())).distinct()
                .sorted(Comparator.comparing(SeccionDto::nombre)).toList();
        List<DocenteDto> todosDocentes = horario.asignaciones().stream()
                .map(x -> new DocenteDto(x.docenteId(), x.docente())).distinct()
                .sorted(Comparator.comparing(DocenteDto::nombre)).toList();

        if (contexto.tienePermiso("HORARIO_GESTIONAR")) {
            return new Alcance(List.of(Perspectiva.SECCION, Perspectiva.DOCENTE), todasSecciones, todosDocentes);
        }
        Optional<DocenteResumen> docente = directorio.docenteDeUsuario(usuarioId);
        if (docente.isPresent()) {
            String yo = docente.get().id();
            Set<String> mias = horario.asignaciones().stream().filter(x -> x.docenteId().equals(yo))
                    .map(AsignacionDto::seccionId).collect(Collectors.toSet());
            horario.tutorPorSeccion().forEach((seccion, tutor) -> {
                if (yo.equals(tutor)) {
                    mias.add(seccion);
                }
            });
            List<SeccionDto> secciones = new ArrayList<>(todasSecciones.stream().filter(s -> mias.contains(s.id())).toList());
            // Una sección donde es tutor y todavía no tiene clases publicadas también se ofrece, con su nombre del catálogo
            Set<String> ofrecidas = secciones.stream().map(SeccionDto::id).collect(Collectors.toSet());
            for (String id : mias) {
                if (!ofrecidas.contains(id)) {
                    secciones.add(new SeccionDto(id, catalogo.seccion(id).nombre()));
                }
            }
            secciones.sort(Comparator.comparing(SeccionDto::nombre));
            return new Alcance(List.of(Perspectiva.DOCENTE, Perspectiva.SECCION), secciones,
                    List.of(new DocenteDto(yo, docente.get().nombreCompleto())));
        }
        // Alumno o apoderado: solo la sección (o las secciones) de su alcance
        List<SeccionDeAlumno> propias = matriculas.seccionesDeMiAlcance();
        boolean varios = propias.size() > 1;
        List<SeccionDto> secciones = propias.stream()
                .map(p -> new SeccionDto(p.seccionId(), varios ? p.seccion() + " · " + p.alumno() : p.seccion())).toList();
        return new Alcance(List.of(Perspectiva.SECCION), secciones, List.of());
    }

    private static ExcepcionNegocio fuera(String detalle) {
        return new ExcepcionNegocio(CodigoError.FUERA_DE_ALCANCE, detalle);
    }

    private Optional<HorarioVigenteCompleto> cargar(AnioEscolarResumen a) {
        Optional<HorarioPublicado> vigente = publicados.findByAnioEscolarIdAndVigenteTrue(a.id());
        if (vigente.isEmpty()) {
            return Optional.empty();
        }
        HorarioPublicado h = vigente.get();
        List<AsignacionPublicada> clases = publicadas.findByHorarioPublicadoId(h.getId());
        Map<String, SeccionResumen> secciones = catalogo.secciones(a.id()).stream().collect(Collectors.toMap(SeccionResumen::id, s -> s));
        Map<String, CursoResumen> cursos = catalogo.cursos(a.id()).stream().collect(Collectors.toMap(CursoResumen::id, c -> c));
        Map<String, DocenteResumen> docentes = directorio.porIds(clases.stream().map(AsignacionPublicada::getDocenteId).collect(Collectors.toSet()));
        Map<String, BloqueHorario> bloquesPorId = bloques.findByAnioEscolarId(a.id()).stream().collect(Collectors.toMap(BloqueHorario::getId, b -> b));

        List<AsignacionDto> asignaciones = clases.stream()
                .sorted(Comparator.comparing((AsignacionPublicada c) -> bloquesPorId.get(c.getBloqueId()).getDiaSemana())
                        .thenComparing(c -> bloquesPorId.get(c.getBloqueId()).getHoraInicio()).thenComparing(AsignacionPublicada::getSeccionId))
                .map(c -> new AsignacionDto(c.getId(), c.getBloqueId(), c.getSeccionId(), secciones.get(c.getSeccionId()).nombre(),
                        c.getCursoId(), cursos.get(c.getCursoId()).nombre(), c.getDocenteId(),
                        Optional.ofNullable(docentes.get(c.getDocenteId())).map(DocenteResumen::nombreCompleto).orElse("")))
                .toList();
        List<BloqueHorarioDto> bloquesDto = bloquesPorId.values().stream()
                .sorted(Comparator.comparing(BloqueHorario::getDiaSemana).thenComparing(BloqueHorario::getHoraInicio))
                .map(b -> new BloqueHorarioDto(b.getId(), b.getDiaSemana(), b.getHoraInicio(), b.getHoraFin())).toList();
        Map<String, String> tutores = secciones.values().stream().filter(s -> s.tutorDocenteId() != null)
                .collect(Collectors.toMap(SeccionResumen::id, SeccionResumen::tutorDocenteId));
        return Optional.of(new HorarioVigenteCompleto(a.anio(), h.getNumeroVersion(),
                h.getFechaHoraPublicacion().atZone(reloj.getZone()).toOffsetDateTime(), bloquesDto, asignaciones, tutores));
    }

    @SuppressWarnings("unused")
    private static boolean mismo(String a, String b) {
        return Objects.equals(a, b);
    }
}
