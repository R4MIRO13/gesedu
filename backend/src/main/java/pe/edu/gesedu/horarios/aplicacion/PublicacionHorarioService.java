package pe.edu.gesedu.horarios.aplicacion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.CursoResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.SeccionResumen;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.compartido.web.Etags;
import pe.edu.gesedu.evaluacion.aplicacion.SincronizacionAsignaciones;
import pe.edu.gesedu.horarios.aplicacion.PublicacionDtos.AdvertenciaDto;
import pe.edu.gesedu.horarios.aplicacion.PublicacionDtos.CompletitudDto;
import pe.edu.gesedu.horarios.aplicacion.PublicacionDtos.DiferenciaDto;
import pe.edu.gesedu.horarios.aplicacion.PublicacionDtos.PublicacionDto;
import pe.edu.gesedu.horarios.aplicacion.PublicacionDtos.SeccionCompletitudDto;
import pe.edu.gesedu.horarios.dominio.AsignacionHorario;
import pe.edu.gesedu.horarios.dominio.AsignacionPublicada;
import pe.edu.gesedu.horarios.dominio.HorarioBorrador;
import pe.edu.gesedu.horarios.dominio.HorarioPublicado;
import pe.edu.gesedu.horarios.dominio.ResponsablesPorCurso;
import pe.edu.gesedu.horarios.dominio.ResponsablesPorCurso.Clase;
import pe.edu.gesedu.horarios.dominio.ValidadorCompletitud;
import pe.edu.gesedu.horarios.dominio.ValidadorCompletitud.CursoEnSeccion;
import pe.edu.gesedu.horarios.dominio.VinculacionDocente;
import pe.edu.gesedu.horarios.infraestructura.AsignacionHorarioRepository;
import pe.edu.gesedu.horarios.infraestructura.AsignacionPublicadaRepository;
import pe.edu.gesedu.horarios.infraestructura.CargaHorariaCursoRepository;
import pe.edu.gesedu.horarios.infraestructura.DisponibilidadDocenteRepository;
import pe.edu.gesedu.horarios.infraestructura.HorarioBorradorRepository;
import pe.edu.gesedu.horarios.infraestructura.HorarioPublicadoRepository;
import pe.edu.gesedu.horarios.infraestructura.VinculacionDocenteRepository;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioDocentes.DocenteResumen;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioUsuarios;

/**
 * UC-22, Publicar horario (RF-43, RF-44, RF-45): valida que cada curso de cada sección tenga exactamente sus bloques
 * semanales, publica una copia inmutable del borrador como nueva versión vigente (la anterior deja de serlo) y
 * sincroniza los responsables de cada curso con la evaluación (S-13).
 * <p>
 * Concurrencia: la publicación bloquea el borrador del año ({@code SELECT … FOR UPDATE}), exige su versión en
 * {@code If-Match} y la sube al terminar; dos publicaciones simultáneas no producen dos versiones (la segunda recibe
 * 409), y un administrador que edita el borrador mientras otro publica recibe 409 al guardar.
 */
@Service
public class PublicacionHorarioService {

    @PersistenceContext
    private EntityManager em;

    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final DirectorioDocentes directorio;
    private final DirectorioUsuarios usuarios;
    private final ContextoUsuario contexto;
    private final HorarioBorradorRepository borradores;
    private final AsignacionHorarioRepository asignaciones;
    private final CargaHorariaCursoRepository cargas;
    private final VinculacionDocenteRepository vinculaciones;
    private final DisponibilidadDocenteRepository disponibilidades;
    private final HorarioPublicadoRepository publicados;
    private final AsignacionPublicadaRepository publicadas;
    private final SincronizacionAsignaciones sincronizacion;
    private final CacheHorarioVigente cache;
    private final Clock reloj;

    PublicacionHorarioService(AnioEscolarService anios, CatalogoAcademicoService catalogo, DirectorioDocentes directorio,
                              DirectorioUsuarios usuarios, ContextoUsuario contexto, HorarioBorradorRepository borradores,
                              AsignacionHorarioRepository asignaciones, CargaHorariaCursoRepository cargas,
                              VinculacionDocenteRepository vinculaciones, DisponibilidadDocenteRepository disponibilidades,
                              HorarioPublicadoRepository publicados, AsignacionPublicadaRepository publicadas,
                              SincronizacionAsignaciones sincronizacion, CacheHorarioVigente cache, Clock reloj) {
        this.anios = anios;
        this.catalogo = catalogo;
        this.directorio = directorio;
        this.usuarios = usuarios;
        this.contexto = contexto;
        this.borradores = borradores;
        this.asignaciones = asignaciones;
        this.cargas = cargas;
        this.vinculaciones = vinculaciones;
        this.disponibilidades = disponibilidades;
        this.publicados = publicados;
        this.publicadas = publicadas;
        this.sincronizacion = sincronizacion;
        this.cache = cache;
        this.reloj = reloj;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Paso 2: validar la completitud
    // ---------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CompletitudDto completitud(int anio) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        Optional<HorarioBorrador> borrador = borradores.findByAnioEscolarId(a.id());
        return calcular(a, borrador.map(HorarioBorrador::getVersion).orElse(0L),
                borrador.map(b -> asignaciones.findByHorarioBorradorId(b.getId())).orElse(List.of()));
    }

    /** Versiones publicadas del año, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<PublicacionDto> versiones(int anio) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        List<HorarioPublicado> lista = publicados.findByAnioEscolarIdOrderByNumeroVersionDesc(a.id());
        Map<String, String> nombres = usuarios.nombresCompletos(lista.stream().map(HorarioPublicado::getPublicadoPor).toList());
        return lista.stream().map(h -> dto(h, nombres.get(h.getPublicadoPor()), (int) publicadas.countByHorarioPublicadoId(h.getId())))
                .toList();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Pasos 3 a 6: publicar
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Publica el borrador como nueva versión vigente. Exige la versión del borrador que se revisó en {@code If-Match}
     * (409 si cambió). Con observaciones responde 422 {@code HORARIO_INCOMPLETO} y el borrador queda como estaba (FA-1).
     */
    @Transactional
    @Auditable(accion = "PUBLICAR_HORARIO", modulo = "HORARIOS", tipoEntidad = "HorarioPublicado", idEntidad = "#result.id()")
    public PublicacionDto publicar(int anio, String ifMatch) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        if ("CERRADO".equals(a.estado())) {
            throw new ExcepcionNegocio(CodigoError.ANIO_CERRADO);
        }
        String usuarioId = contexto.idUsuarioActual().orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO));
        HorarioBorrador borrador = borradores.bloquearPorAnio(a.id()).orElse(null);
        if (borrador == null) {
            throw incompleto(calcular(a, 0, List.of()));
        }
        Etags.verificar(borrador.getVersion(), ifMatch);

        List<AsignacionHorario> clases = asignaciones.findByHorarioBorradorId(borrador.getId());
        CompletitudDto estado = calcular(a, borrador.getVersion(), clases);
        if (!estado.publicable()) {
            throw incompleto(estado);
        }

        int anterior = publicados.ultimaVersion(a.id());
        publicados.retirarVigente(a.id());
        HorarioPublicado nuevo = new HorarioPublicado(a.id(), anterior + 1, reloj.instant(), usuarioId);
        try {
            publicados.saveAndFlush(nuevo);
        } catch (DataIntegrityViolationException e) {
            // Los índices únicos (versión y vigente) son la última defensa contra dos publicaciones a la vez
            throw new ExcepcionNegocio(CodigoError.CONFLICTO_CONCURRENCIA,
                    "Otra persona publicó el horario al mismo tiempo; revisa la versión vigente");
        }
        publicadas.saveAll(clases.stream().map(c -> new AsignacionPublicada(nuevo.getId(), c.getBloqueId(),
                c.getSeccionId(), c.getCursoId(), c.getDocenteId())).toList());
        publicadas.flush();

        sincronizarResponsables(clases);

        // La versión del borrador sube: quien lo editaba, o quien pedía publicarlo, con la anterior recibe 409
        borrador.tocar(reloj.instant());
        em.flush();
        cache.invalidarTrasCommit(a.anio());

        ContextoAuditoria.registrarCambio("versión vigente", anterior == 0 ? null : anterior, nuevo.getNumeroVersion());
        ContextoAuditoria.registrarCambio("clases", null, clases.size());
        ContextoAuditoria.registrarCambio("secciones", null, estado.secciones().size());
        return dto(nuevo, usuarios.nombresCompletos(List.of(usuarioId)).get(usuarioId), clases.size());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Apoyo
    // ---------------------------------------------------------------------------------------------------------

    private void sincronizarResponsables(List<AsignacionHorario> clases) {
        Set<String> secciones = clases.stream().map(AsignacionHorario::getSeccionId).collect(Collectors.toSet());
        List<ResponsablesPorCurso.Responsable> elegidos = ResponsablesPorCurso.elegir(
                clases.stream().map(c -> new Clase(c.getCursoId(), c.getSeccionId(), c.getDocenteId())).toList(),
                sincronizacion.responsablesActuales(secciones));
        sincronizacion.sincronizar(elegidos.stream()
                .map(r -> new SincronizacionAsignaciones.Responsable(r.cursoId(), r.seccionId(), r.docenteId())).toList());
    }

    private CompletitudDto calcular(AnioEscolarResumen a, long version, List<AsignacionHorario> clases) {
        List<SeccionResumen> secciones = catalogo.secciones(a.id());
        List<CursoResumen> cursos = catalogo.cursos(a.id());
        Map<String, Integer> requeridas = secciones.isEmpty() ? Map.of()
                : cargas.findByGradoIdIn(secciones.stream().map(SeccionResumen::gradoId).distinct().toList()).stream()
                        .collect(Collectors.toMap(c -> c.getGradoId() + "|" + c.getCursoId(), c -> c.getBloquesSemanales()));
        Map<String, Long> asignadas = clases.stream()
                .collect(Collectors.groupingBy(c -> c.getSeccionId() + "|" + c.getCursoId(), Collectors.counting()));

        List<ValidadorCompletitud.Resultado> resultados = new ArrayList<>();
        for (SeccionResumen s : secciones) {
            List<CursoEnSeccion> enSeccion = cursos.stream().map(c -> new CursoEnSeccion(c.id(), c.nombre(),
                    asignadas.getOrDefault(s.id() + "|" + c.id(), 0L).intValue(),
                    requeridas.getOrDefault(s.gradoId() + "|" + c.id(), c.horasLectivas()))).toList();
            resultados.add(ValidadorCompletitud.evaluar(s.id(), s.nombre(), enSeccion));
        }
        List<SeccionCompletitudDto> dtos = resultados.stream().map(r -> new SeccionCompletitudDto(r.seccionId(), r.seccion(),
                r.asignadas(), r.requeridas(), r.estado().name(), r.faltan(), r.sobran(),
                r.diferencias().stream().map(d -> new DiferenciaDto(d.cursoId(), d.curso(), d.asignadas(), d.requeridas())).toList()))
                .toList();
        int completas = (int) resultados.stream().filter(ValidadorCompletitud.Resultado::estaCompleta).count();

        Optional<HorarioPublicado> vigente = publicados.findByAnioEscolarIdAndVigenteTrue(a.id());
        PublicacionDto vigenteDto = vigente.map(h -> dto(h,
                usuarios.nombresCompletos(List.of(h.getPublicadoPor())).get(h.getPublicadoPor()),
                (int) publicadas.countByHorarioPublicadoId(h.getId()))).orElse(null);
        boolean sinCambios = vigente.isPresent() && igualAlVigente(clases, vigente.get());
        return new CompletitudDto(a.anio(), version, dtos, completas, dtos.size() - completas,
                ValidadorCompletitud.publicable(resultados), advertencias(a), vigenteDto, sinCambios);
    }

    private boolean igualAlVigente(List<AsignacionHorario> clases, HorarioPublicado vigente) {
        Set<String> borrador = clases.stream().map(c -> clave(c.getBloqueId(), c.getSeccionId(), c.getCursoId(), c.getDocenteId()))
                .collect(Collectors.toSet());
        List<AsignacionPublicada> publicadasVigentes = publicadas.findByHorarioPublicadoId(vigente.getId());
        Set<String> publicado = publicadasVigentes.stream()
                .map(c -> clave(c.getBloqueId(), c.getSeccionId(), c.getCursoId(), c.getDocenteId())).collect(Collectors.toSet());
        return borrador.equals(publicado) && clases.size() == publicadasVigentes.size();
    }

    private static String clave(String bloque, String seccion, String curso, String docente) {
        return bloque + "|" + seccion + "|" + curso + "|" + docente;
    }

    /** C-40: docentes habilitados que aún no confirmaron su disponibilidad. Es un aviso, no impide publicar. */
    private List<AdvertenciaDto> advertencias(AnioEscolarResumen a) {
        List<VinculacionDocente> habilitadas = vinculaciones.findByAnioEscolarIdAndHabilitadoTrue(a.id());
        if (habilitadas.isEmpty()) {
            return List.of();
        }
        Set<String> confirmadas = disponibilidades.findByVinculacionIdIn(habilitadas.stream().map(VinculacionDocente::getId).toList())
                .stream().map(d -> d.getVinculacionId()).collect(Collectors.toSet());
        Set<String> sinConfirmar = new HashSet<>();
        habilitadas.stream().filter(v -> !confirmadas.contains(v.getId())).forEach(v -> sinConfirmar.add(v.getDocenteId()));
        if (sinConfirmar.isEmpty()) {
            return List.of();
        }
        List<String> nombres = directorio.porIds(sinConfirmar).values().stream()
                .sorted(Comparator.comparing(DocenteResumen::apellidos).thenComparing(DocenteResumen::nombres))
                .map(DocenteResumen::nombreCompleto).toList();
        String mensaje = nombres.size() == 1 ? "1 docente habilitado no confirmó su disponibilidad: " + nombres.getFirst() + "."
                : nombres.size() + " docentes habilitados no confirmaron su disponibilidad: " + String.join(", ", nombres) + ".";
        return List.of(new AdvertenciaDto("DISPONIBILIDAD_SIN_CONFIRMAR", mensaje, nombres));
    }

    private static ExcepcionNegocio incompleto(CompletitudDto estado) {
        List<SeccionCompletitudDto> observadas = estado.secciones().stream().filter(s -> !"COMPLETO".equals(s.estado())).toList();
        String detalle = observadas.isEmpty() ? "El año no tiene secciones con clases para publicar"
                : "Hay " + observadas.size() + (observadas.size() == 1 ? " sección con observaciones" : " secciones con observaciones")
                + ": corrígelas antes de publicar";
        return new ExcepcionNegocio(CodigoError.HORARIO_INCOMPLETO, detalle).con("secciones", observadas);
    }

    private PublicacionDto dto(HorarioPublicado h, String publicadoPor, int clases) {
        OffsetDateTime fecha = h.getFechaHoraPublicacion().atZone(reloj.getZone()).toOffsetDateTime();
        return new PublicacionDto(h.getId(), h.getNumeroVersion(), fecha, h.isVigente(), publicadoPor, clases);
    }
}
