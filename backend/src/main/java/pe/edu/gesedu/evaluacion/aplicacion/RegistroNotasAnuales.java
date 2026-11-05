package pe.edu.gesedu.evaluacion.aplicacion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.CompetenciaResumen;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.dominio.NivelLogro;
import pe.edu.gesedu.evaluacion.aplicacion.NotasFinalesDtos.CalculoDto;
import pe.edu.gesedu.evaluacion.dominio.AsignacionDocente;
import pe.edu.gesedu.evaluacion.dominio.NotaConsolidada;
import pe.edu.gesedu.evaluacion.infraestructura.NotaConsolidadaRepository;
import pe.edu.gesedu.matricula.aplicacion.ConsultaMatriculaService.AlumnoDeSeccion;

/**
 * Guarda las notas anuales de una asignación (RF-28) reemplazando las que ya existían: una sola nota por alumno y
 * competencia. Lo usan el cálculo del docente (UC-17) y la consolidación del último bimestre (UC-18, FA-1), de modo que
 * las dos hacen exactamente lo mismo. Registra los cambios en la auditoría de la operación en curso.
 */
@Component
class RegistroNotasAnuales {

    private final NotaConsolidadaRepository notas;

    RegistroNotasAnuales(NotaConsolidadaRepository notas) {
        this.notas = notas;
    }

    /**
     * @param esperadas nivel anual por {@code alumnoId|competenciaId} (las celdas sin logros no figuran: no hay nota)
     * @param libretas  libreta de cada alumno por su id; vacío para no vincular ninguna
     */
    CalculoDto guardar(AsignacionDocente asignacion, String anioId, List<AlumnoDeSeccion> alumnos, List<CompetenciaResumen> competencias,
                       Map<String, NivelLogro> esperadas, Map<String, String> libretas, Instant ahora) {
        Map<String, NotaConsolidada> existentes = notas.findByAsignacionIdAndTipo(asignacion.getId(), NotaConsolidada.ANUAL).stream()
                .collect(Collectors.toMap(n -> CalculadorNotaAnual.clave(n.getAlumnoId(), n.getCompetenciaId()), Function.identity()));
        int creadas = 0;
        int actualizadas = 0;
        int sinCambios = 0;
        // Solo los alumnos matriculados hoy en la sección y las competencias vigentes del curso reciben nota
        for (AlumnoDeSeccion alumno : alumnos) {
            String libreta = libretas.get(alumno.alumnoId());
            for (CompetenciaResumen competencia : competencias) {
                String celda = CalculadorNotaAnual.clave(alumno.alumnoId(), competencia.id());
                NivelLogro nivel = esperadas.get(celda);
                if (nivel == null) {
                    continue;
                }
                NotaConsolidada existente = existentes.get(celda);
                String campo = "anual " + alumno.codigo() + " " + competencia.codigo();
                if (existente == null) {
                    NotaConsolidada nueva = NotaConsolidada.anual(alumno.alumnoId(), competencia.id(), asignacion.getId(), anioId, nivel, ahora);
                    nueva.vincularLibreta(libreta);
                    notas.save(nueva);
                    ContextoAuditoria.registrarCambio(campo, null, nivel);
                    creadas++;
                } else {
                    NivelLogro anterior = existente.getNivelLogro();
                    existente.vincularLibreta(libreta);
                    if (existente.reemplazar(nivel, ahora)) {
                        ContextoAuditoria.registrarCambio(campo, anterior, nivel);
                        actualizadas++;
                    } else {
                        sinCambios++;
                    }
                }
            }
        }
        int sinNota = alumnos.size() * competencias.size() - creadas - actualizadas - sinCambios;
        return new CalculoDto(creadas, actualizadas, sinCambios, sinNota);
    }
}
