package pe.edu.gesedu.horarios.aplicacion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.AnioEscolarService.AnioEscolarResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.CursoResumen;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.GradoResumen;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.CargaSolicitud;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.CargasHorariasDto;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.CeldaCargaDto;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.FilaCargaDto;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.GradoOpcionDto;
import pe.edu.gesedu.horarios.aplicacion.ConfiguracionHorariosDtos.TotalGradoDto;
import pe.edu.gesedu.horarios.dominio.CargaHorariaCurso;
import pe.edu.gesedu.horarios.infraestructura.BloqueHorarioRepository;
import pe.edu.gesedu.horarios.infraestructura.CargaHorariaCursoRepository;

/**
 * Bloques semanales que cada grado dicta de cada curso (RF-43, C-10): lo que el horario de cada sección debe
 * completar antes de publicarse. Sin un valor propio rige el de las horas lectivas del curso.
 */
@Service
public class CargaHorariaService {

    private static final int BLOQUES_MAXIMOS = 20;

    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final CargaHorariaCursoRepository cargas;
    private final BloqueHorarioRepository bloques;

    CargaHorariaService(AnioEscolarService anios, CatalogoAcademicoService catalogo, CargaHorariaCursoRepository cargas,
                        BloqueHorarioRepository bloques) {
        this.anios = anios;
        this.catalogo = catalogo;
        this.cargas = cargas;
        this.bloques = bloques;
    }

    @Transactional(readOnly = true)
    public CargasHorariasDto obtener(int anio) {
        return armar(anios.obtenerPorAnio(anio));
    }

    /**
     * Guarda las cargas indicadas (las demás celdas no cambian). Cada celda es un curso y un grado del año, de 1 a
     * {@value #BLOQUES_MAXIMOS} bloques por semana.
     */
    @Transactional
    @Auditable(accion = "CONFIGURAR_CARGA_HORARIA", modulo = "HORARIOS", tipoEntidad = "CargaHorariaCurso", idEntidad = "#anio")
    public CargasHorariasDto guardar(int anio, List<CargaSolicitud> solicitudes) {
        AnioEscolarResumen a = anios.obtenerPorAnio(anio);
        if ("CERRADO".equals(a.estado())) {
            throw new ExcepcionNegocio(CodigoError.ANIO_CERRADO);
        }
        Map<String, GradoResumen> grados = catalogo.gradosDelAnio(a.id()).stream()
                .collect(Collectors.toMap(GradoResumen::id, g -> g));
        Map<String, CursoResumen> cursos = catalogo.cursos(a.id()).stream().collect(Collectors.toMap(CursoResumen::id, c -> c));
        Set<String> vistas = new java.util.HashSet<>();
        for (CargaSolicitud s : solicitudes) {
            if (!grados.containsKey(s.gradoId()) || !cursos.containsKey(s.cursoId())) {
                throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "Algún grado o curso no pertenece al año " + anio);
            }
            if (s.bloques() < 1 || s.bloques() > BLOQUES_MAXIMOS) {
                throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                        "Los bloques semanales deben estar entre 1 y " + BLOQUES_MAXIMOS);
            }
            if (!vistas.add(s.gradoId() + "|" + s.cursoId())) {
                throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "Hay una celda repetida en la solicitud");
            }
        }

        Map<String, CargaHorariaCurso> actuales = cargas.findByGradoIdIn(grados.keySet()).stream()
                .collect(Collectors.toMap(c -> c.getGradoId() + "|" + c.getCursoId(), c -> c));
        List<CargaHorariaCurso> aGuardar = new ArrayList<>();
        for (CargaSolicitud s : solicitudes) {
            CargaHorariaCurso actual = actuales.get(s.gradoId() + "|" + s.cursoId());
            int antes = actual == null ? cursos.get(s.cursoId()).horasLectivas() : actual.getBloquesSemanales();
            String etiqueta = cursos.get(s.cursoId()).nombre() + " " + grados.get(s.gradoId()).numeroNivel() + "°";
            if (actual == null) {
                aGuardar.add(new CargaHorariaCurso(s.gradoId(), s.cursoId(), s.bloques()));
            } else {
                actual.setBloquesSemanales(s.bloques());
                aGuardar.add(actual);
            }
            if (antes != s.bloques()) {
                ContextoAuditoria.registrarCambio(etiqueta, antes, s.bloques());
            }
        }
        try {
            cargas.saveAllAndFlush(aGuardar);
        } catch (DataIntegrityViolationException e) {
            throw new ExcepcionNegocio(CodigoError.CONFLICTO_CONCURRENCIA,
                    "Otra persona configuró las cargas al mismo tiempo; vuelve a intentarlo");
        }
        return armar(a);
    }

    private CargasHorariasDto armar(AnioEscolarResumen a) {
        List<GradoResumen> grados = catalogo.gradosDelAnio(a.id());
        List<CursoResumen> cursos = catalogo.cursos(a.id());
        Map<String, CargaHorariaCurso> propias = grados.isEmpty() ? Map.of()
                : cargas.findByGradoIdIn(grados.stream().map(GradoResumen::id).toList()).stream()
                        .collect(Collectors.toMap(c -> c.getGradoId() + "|" + c.getCursoId(), c -> c));
        int bloquesPorSemana = bloques.findByAnioEscolarId(a.id()).size();

        Map<String, Integer> totales = new HashMap<>();
        List<FilaCargaDto> filas = new ArrayList<>();
        for (CursoResumen c : cursos.stream().sorted(Comparator.comparing(CursoResumen::nombre)).toList()) {
            List<CeldaCargaDto> celdas = new ArrayList<>();
            for (GradoResumen g : grados) {
                CargaHorariaCurso propia = propias.get(g.id() + "|" + c.id());
                int valor = propia == null ? c.horasLectivas() : propia.getBloquesSemanales();
                totales.merge(g.id(), valor, Integer::sum);
                celdas.add(new CeldaCargaDto(g.id(), valor, valor != c.horasLectivas()));
            }
            filas.add(new FilaCargaDto(c.id(), c.nombre(), c.horasLectivas(), celdas));
        }
        return new CargasHorariasDto(a.anio(), a.estado(), bloquesPorSemana,
                grados.stream().map(g -> new GradoOpcionDto(g.id(), g.numeroNivel())).toList(), filas,
                grados.stream().map(g -> new TotalGradoDto(g.id(), totales.getOrDefault(g.id(), 0),
                        totales.getOrDefault(g.id(), 0) > bloquesPorSemana)).toList());
    }
}
