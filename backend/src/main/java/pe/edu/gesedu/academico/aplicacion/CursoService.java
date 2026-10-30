package pe.edu.gesedu.academico.aplicacion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.CursosDtos.CompetenciaDto;
import pe.edu.gesedu.academico.aplicacion.CursosDtos.CompetenciaSolicitud;
import pe.edu.gesedu.academico.aplicacion.CursosDtos.CopiaAnteriorDto;
import pe.edu.gesedu.academico.aplicacion.CursosDtos.CursoCopiable;
import pe.edu.gesedu.academico.aplicacion.CursosDtos.CursoDto;
import pe.edu.gesedu.academico.aplicacion.CursosDtos.CursoSolicitud;
import pe.edu.gesedu.academico.aplicacion.CursosDtos.CursosAnioDto;
import pe.edu.gesedu.academico.aplicacion.CursosDtos.ResultadoCopiaDto;
import pe.edu.gesedu.academico.dominio.AnioEscolar;
import pe.edu.gesedu.academico.dominio.Competencia;
import pe.edu.gesedu.academico.dominio.Curso;
import pe.edu.gesedu.academico.dominio.ReglasCurso;
import pe.edu.gesedu.academico.infraestructura.AnioEscolarRepository;
import pe.edu.gesedu.academico.infraestructura.CompetenciaRepository;
import pe.edu.gesedu.academico.infraestructura.CursoRepository;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;

/**
 * Cursos de un año escolar con sus competencias (UC-14, RF-23, RF-24): alta, edición, baja y copia de la
 * configuración del año anterior. Los cursos son comunes a todos los grados. Un año CERRADO no se modifica.
 */
@Service
public class CursoService {

    private static final int MAXIMO_COMPETENCIAS = 12;

    private final CursoRepository cursos;
    private final CompetenciaRepository competencias;
    private final AnioEscolarRepository anios;

    CursoService(CursoRepository cursos, CompetenciaRepository competencias, AnioEscolarRepository anios) {
        this.cursos = cursos;
        this.competencias = competencias;
        this.anios = anios;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Consulta (pasos 2, 3 y 14)
    // ---------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CursosAnioDto listar(int anio) {
        AnioEscolar a = anio(anio);
        List<CursoDto> dtos = dtos(cursos.findByAnioEscolarIdOrderByNombre(a.getId()));
        return new CursosAnioDto(a.getAnio(), a.getEstado(), dtos.stream().mapToInt(CursoDto::horasLectivas).sum(), dtos);
    }

    @Transactional(readOnly = true)
    public CursoDto obtener(String id) {
        return dtos(List.of(curso(id))).getFirst();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Alta, edición y baja (pasos 4 a 12)
    // ---------------------------------------------------------------------------------------------------------

    @Transactional
    @Auditable(accion = "CREAR_CURSO", modulo = "ACADEMICO", tipoEntidad = "Curso", idEntidad = "#result.id()")
    public CursoDto crear(int anio, CursoSolicitud solicitud) {
        AnioEscolar a = abierto(anio(anio));
        String nombre = solicitud.nombre().trim();
        validar(solicitud);
        exigirNombreLibre(a.getId(), null, nombre, ReglasCurso.slug(nombre));
        Curso nuevo = new Curso(a.getId(), nombre, ReglasCurso.slug(nombre), solicitud.horasLectivas());
        guardarCurso(nuevo, nombre);

        List<Competencia> creadas = new ArrayList<>();
        for (CompetenciaSolicitud c : solicitud.competencias()) {
            creadas.add(new Competencia(nuevo.getId(), ReglasCurso.siguienteCodigo(
                    creadas.stream().map(Competencia::getCodigo).toList()), c.nombre().trim(), limpio(c.nivelEsperado()),
                    creadas.size() + 1));
        }
        competencias.saveAll(creadas);

        ContextoAuditoria.registrarCambio("curso", null, nombre);
        ContextoAuditoria.registrarCambio("horasLectivas", null, solicitud.horasLectivas());
        creadas.forEach(c -> ContextoAuditoria.registrarCambio("competencia " + c.getCodigo(), null, c.getNombre()));
        return dto(nuevo, creadas);
    }

    @Transactional
    @Auditable(accion = "MODIFICAR_CURSO", modulo = "ACADEMICO", tipoEntidad = "Curso", idEntidad = "#id")
    public CursoDto modificar(String id, CursoSolicitud solicitud) {
        Curso curso = curso(id);
        abierto(anioDe(curso));
        validar(solicitud);
        String nombre = solicitud.nombre().trim();

        if (!curso.getNombre().equals(nombre)) {
            ContextoAuditoria.registrarCambio("curso", curso.getNombre(), nombre);
        }
        if (curso.getHorasLectivas() != solicitud.horasLectivas()) {
            ContextoAuditoria.registrarCambio("horasLectivas", curso.getHorasLectivas(), solicitud.horasLectivas());
        }
        exigirNombreLibre(curso.getAnioEscolarId(), curso.getId(), nombre, ReglasCurso.slug(nombre));
        curso.actualizar(nombre, ReglasCurso.slug(nombre), solicitud.horasLectivas());
        guardarCurso(curso, nombre);

        List<Competencia> actuales = competencias.findByCursoIdOrderByOrden(id);
        Map<String, Competencia> porId = actuales.stream().collect(Collectors.toMap(Competencia::getId, Function.identity()));
        Set<String> conservadas = new HashSet<>();
        List<Competencia> resultado = new ArrayList<>();
        List<String> codigos = new ArrayList<>(actuales.stream().map(Competencia::getCodigo).toList());
        int orden = 1;
        for (CompetenciaSolicitud pedida : solicitud.competencias()) {
            String nombreCompetencia = pedida.nombre().trim();
            if (pedida.id() == null || pedida.id().isBlank()) {
                String codigo = ReglasCurso.siguienteCodigo(codigos);
                codigos.add(codigo);
                Competencia nueva = new Competencia(id, codigo, nombreCompetencia, limpio(pedida.nivelEsperado()), orden);
                competencias.save(nueva);
                resultado.add(nueva);
                ContextoAuditoria.registrarCambio("competencia " + codigo, null, nombreCompetencia);
            } else {
                Competencia existente = porId.get(pedida.id());
                if (existente == null) {
                    throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                            "La competencia indicada no pertenece al curso");
                }
                if (!conservadas.add(existente.getId())) {
                    throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "Una competencia viene repetida");
                }
                if (!existente.getNombre().equals(nombreCompetencia)) {
                    ContextoAuditoria.registrarCambio("competencia " + existente.getCodigo(), existente.getNombre(),
                            nombreCompetencia);
                }
                existente.actualizar(nombreCompetencia, limpio(pedida.nivelEsperado()), orden);
                resultado.add(existente);
            }
            orden++;
        }
        List<Competencia> quitadas = actuales.stream().filter(c -> !conservadas.contains(c.getId())).toList();
        if (!quitadas.isEmpty()) {
            try {
                competencias.deleteAll(quitadas);
                competencias.flush();
            } catch (DataIntegrityViolationException e) {
                throw new ExcepcionNegocio(CodigoError.CURSO_EN_USO,
                        "Una competencia que quieres quitar ya tiene logros registrados");
            }
            quitadas.forEach(c -> ContextoAuditoria.registrarCambio("competencia " + c.getCodigo(), c.getNombre(), null));
        }
        competencias.flush();
        return dto(curso, resultado);
    }

    @Transactional
    @Auditable(accion = "ELIMINAR_CURSO", modulo = "ACADEMICO", tipoEntidad = "Curso", idEntidad = "#id")
    public void eliminar(String id) {
        Curso curso = curso(id);
        abierto(anioDe(curso));
        List<Competencia> suyas = competencias.findByCursoIdOrderByOrden(id);
        try {
            competencias.deleteAll(suyas);
            cursos.delete(curso);
            cursos.flush();
        } catch (DataIntegrityViolationException e) {
            // Las llaves foráneas de horarios y evaluación son la defensa: el curso ya se usa
            throw new ExcepcionNegocio(CodigoError.CURSO_EN_USO,
                    "El curso ya tiene docentes, cargas, clases o logros asociados y no se puede eliminar");
        }
        ContextoAuditoria.registrarCambio("curso", curso.getNombre(), null);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Copia del año anterior (FA-1)
    // ---------------------------------------------------------------------------------------------------------

    /** FA-1, paso 2: la configuración de cursos y competencias del año anterior más reciente que tenga cursos. */
    @Transactional(readOnly = true)
    public CopiaAnteriorDto copiaAnterior(int anio) {
        AnioEscolar destino = anio(anio);
        AnioEscolar origen = anioOrigen(destino);
        if (origen == null) {
            return new CopiaAnteriorDto(null, List.of());
        }
        Set<String> existentes = cursos.findByAnioEscolarIdOrderByNombre(destino.getId()).stream()
                .map(Curso::getSlug).collect(Collectors.toSet());
        List<CursoCopiable> copiables = dtos(cursos.findByAnioEscolarIdOrderByNombre(origen.getId())).stream()
                .map(c -> new CursoCopiable(c.id(), c.nombre(), c.horasLectivas(), c.competencias(),
                        existentes.contains(c.slug())))
                .toList();
        return new CopiaAnteriorDto(origen.getAnio(), copiables);
    }

    /**
     * FA-1, pasos 3 y 4: copia los cursos elegidos (todos si no se indica ninguno) con sus competencias. Los que el
     * año ya tiene se omiten, de modo que repetir la operación no duplica nada.
     */
    @Transactional
    @Auditable(accion = "COPIAR_CURSOS_ANIO_ANTERIOR", modulo = "ACADEMICO", tipoEntidad = "AnioEscolar",
            idEntidad = "#result.anioOrigen()")
    public ResultadoCopiaDto copiar(int anio, List<String> cursoIds) {
        AnioEscolar destino = abierto(anio(anio));
        AnioEscolar origen = anioOrigen(destino);
        if (origen == null) {
            throw new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO,
                    "Ningún año escolar anterior tiene cursos configurados");
        }
        List<Curso> delOrigen = cursos.findByAnioEscolarIdOrderByNombre(origen.getId());
        List<Curso> elegidos = delOrigen;
        if (cursoIds != null && !cursoIds.isEmpty()) {
            Set<String> pedidos = new HashSet<>(cursoIds);
            if (!delOrigen.stream().map(Curso::getId).collect(Collectors.toSet()).containsAll(pedidos)) {
                throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                        "Algún curso elegido no pertenece al año " + origen.getAnio());
            }
            elegidos = delOrigen.stream().filter(c -> pedidos.contains(c.getId())).toList();
        }
        Set<String> existentes = cursos.findByAnioEscolarIdOrderByNombre(destino.getId()).stream()
                .map(Curso::getSlug).collect(Collectors.toCollection(HashSet::new));
        Map<String, List<Competencia>> competenciasDeOrigen = competencias
                .findByCursoIdInOrderByOrden(elegidos.stream().map(Curso::getId).toList()).stream()
                .collect(Collectors.groupingBy(Competencia::getCursoId));

        List<CursoDto> copiados = new ArrayList<>();
        List<String> omitidos = new ArrayList<>();
        for (Curso c : elegidos) {
            if (!existentes.add(c.getSlug())) {
                omitidos.add(c.getNombre());
                continue;
            }
            Curso copia = new Curso(destino.getId(), c.getNombre(), c.getSlug(), c.getHorasLectivas());
            guardarCurso(copia, c.getNombre());
            List<Competencia> nuevas = competenciasDeOrigen.getOrDefault(c.getId(), List.of()).stream()
                    .map(k -> new Competencia(copia.getId(), k.getCodigo(), k.getNombre(), k.getNivelEsperado(), k.getOrden()))
                    .toList();
            competencias.saveAll(nuevas);
            copiados.add(dto(copia, nuevas));
            ContextoAuditoria.registrarCambio("curso", null, c.getNombre());
        }
        ContextoAuditoria.registrarCambio("anioOrigen", null, origen.getAnio());
        ContextoAuditoria.registrarCambio("anioDestino", null, destino.getAnio());
        return new ResultadoCopiaDto(origen.getAnio(), copiados, omitidos);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Apoyo
    // ---------------------------------------------------------------------------------------------------------

    private AnioEscolar anioOrigen(AnioEscolar destino) {
        return anios.findByAnioLessThan(destino.getAnio()).stream()
                .sorted(java.util.Comparator.comparingInt(AnioEscolar::getAnio).reversed())
                .filter(a -> !cursos.findByAnioEscolarIdOrderByNombre(a.getId()).isEmpty())
                .findFirst().orElse(null);
    }

    /** El nombre y su versión «sin tildes» (slug) son únicos por año; se comprueba antes de tocar la entidad. */
    private void exigirNombreLibre(String anioId, String cursoId, String nombre, String slug) {
        boolean repetido = cursos.findByAnioEscolarIdOrderByNombre(anioId).stream()
                .anyMatch(c -> !Objects.equals(c.getId(), cursoId)
                        && (c.getSlug().equals(slug) || c.getNombre().equalsIgnoreCase(nombre)));
        if (repetido) {
            throw duplicado(nombre);
        }
    }

    private void guardarCurso(Curso curso, String nombre) {
        try {
            cursos.saveAndFlush(curso);
        } catch (DataIntegrityViolationException e) {
            // Otro directivo creó el mismo curso justo ahora (índices únicos del año)
            throw duplicado(nombre);
        }
    }

    private static ExcepcionNegocio duplicado(String nombre) {
        return new ExcepcionNegocio(CodigoError.CURSO_DUPLICADO, "Ya existe el curso «" + nombre + "» en el año escolar");
    }

    private static void validar(CursoSolicitud s) {
        if (!ReglasCurso.horasValidas(s.horasLectivas())) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "Las horas lectivas deben estar entre "
                    + ReglasCurso.HORAS_MINIMAS + " y " + ReglasCurso.HORAS_MAXIMAS);
        }
        if (s.competencias() == null || s.competencias().isEmpty()) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "El curso necesita al menos una competencia");
        }
        if (s.competencias().size() > MAXIMO_COMPETENCIAS) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                    "Un curso admite hasta " + MAXIMO_COMPETENCIAS + " competencias");
        }
        Set<String> nombres = new HashSet<>();
        for (CompetenciaSolicitud c : s.competencias()) {
            if (!nombres.add(c.nombre().trim().toLowerCase())) {
                throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                        "La competencia «" + c.nombre().trim() + "» está repetida");
            }
        }
    }

    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private AnioEscolar anio(int anio) {
        return anios.findByAnio(anio).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el año escolar " + anio));
    }

    private AnioEscolar anioDe(Curso curso) {
        return anios.findById(curso.getAnioEscolarId()).orElseThrow();
    }

    /** Un año CERRADO ya no se configura. */
    private AnioEscolar abierto(AnioEscolar anio) {
        if ("CERRADO".equals(anio.getEstado())) {
            throw new ExcepcionNegocio(CodigoError.ANIO_CERRADO);
        }
        return anio;
    }

    private Curso curso(String id) {
        return cursos.findById(id).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el curso"));
    }

    private List<CursoDto> dtos(List<Curso> lista) {
        Map<String, List<Competencia>> porCurso = lista.isEmpty() ? Map.of()
                : competencias.findByCursoIdInOrderByOrden(lista.stream().map(Curso::getId).toList()).stream()
                        .collect(Collectors.groupingBy(Competencia::getCursoId));
        return lista.stream().map(c -> dto(c, porCurso.getOrDefault(c.getId(), List.of()))).toList();
    }

    private static CursoDto dto(Curso c, List<Competencia> lista) {
        return new CursoDto(c.getId(), c.getNombre(), c.getSlug(), c.getHorasLectivas(), c.isHabilitado(),
                lista.stream().sorted(java.util.Comparator.comparingInt(Competencia::getOrden))
                        .map(k -> new CompetenciaDto(k.getId(), k.getCodigo(), k.getNombre(), k.getNivelEsperado(), k.getOrden()))
                        .toList());
    }
}
