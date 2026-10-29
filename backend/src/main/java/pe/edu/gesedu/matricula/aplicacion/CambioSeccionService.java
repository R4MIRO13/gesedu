package pe.edu.gesedu.matricula.aplicacion;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.academico.aplicacion.AnioEscolarService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.SeccionConVacantes;
import pe.edu.gesedu.academico.aplicacion.CatalogoAcademicoService.SeccionResumen;
import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.matricula.dominio.EstadoMatricula;
import pe.edu.gesedu.matricula.dominio.Matricula;
import pe.edu.gesedu.matricula.infraestructura.MatriculaRepository;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias.AlumnoResumen;

/**
 * Cambio de grado o sección de una matrícula confirmada de un año vigente (RF-05, C-43; solo el administrador). Es una
 * sola transacción: descuenta la vacante del destino con un UPDATE condicional y devuelve la del origen sin pasar de la
 * capacidad. Si el destino está lleno responde 409 {@code SIN_VACANTE} y no libera nada.
 */
@Service
public class CambioSeccionService {

    /** La matrícula vigente de un estudiante con las secciones del año a las que se la puede trasladar. */
    public record MatriculaGestionable(String matriculaId, String alumnoId, String alumno, int anio, String grado,
                                       String seccionId, String seccion, String estado, long version,
                                       List<SeccionConVacantes> secciones) {
    }

    public record ResultadoCambioSeccion(String matriculaId, String alumno, String seccionAnterior, String seccionNueva,
                                         int vacantesOrigen, int vacantesDestino, boolean sinCambios) {
    }

    private final MatriculaRepository matriculas;
    private final AnioEscolarService anios;
    private final CatalogoAcademicoService catalogo;
    private final DirectorioFamilias familias;

    CambioSeccionService(MatriculaRepository matriculas, AnioEscolarService anios, CatalogoAcademicoService catalogo,
                         DirectorioFamilias familias) {
        this.matriculas = matriculas;
        this.anios = anios;
        this.catalogo = catalogo;
        this.familias = familias;
    }

    /** La matrícula vigente del estudiante y las secciones de su año con su cupo; 404 si no tiene una matrícula vigente. */
    @Transactional(readOnly = true)
    public MatriculaGestionable deAlumno(String alumnoId) {
        AlumnoResumen alumno = familias.alumnos(List.of(alumnoId)).get(alumnoId);
        if (alumno == null) {
            throw new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el estudiante");
        }
        Set<String> vigentes = Set.copyOf(anios.idsNoCerrados());
        Matricula m = matriculas.findByAlumnoIdInAndEstado(List.of(alumnoId), EstadoMatricula.CONFIRMADA).stream()
                .filter(x -> vigentes.contains(x.getAnioEscolarId()))
                .max(Comparator.comparingInt(x -> anios.obtenerPorId(x.getAnioEscolarId()).anio()))
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO,
                        "El estudiante no tiene una matrícula vigente"));
        SeccionResumen seccion = catalogo.seccion(m.getSeccionId());
        return new MatriculaGestionable(m.getId(), alumnoId, alumno.nombreCompleto(),
                anios.obtenerPorId(m.getAnioEscolarId()).anio(), seccion.numeroGrado() + "° de secundaria", seccion.id(),
                seccion.nombre(), "Confirmada", m.getVersion(), catalogo.seccionesConVacantesDelAnio(m.getAnioEscolarId()));
    }

    /**
     * Traslada la matrícula a otra sección del mismo año (del mismo grado o de otro). Misma sección → sin cambios.
     *
     * @throws ExcepcionNegocio {@code MATRICULA_NO_VIGENTE} (422) si está anulada o es de un año cerrado;
     *                          {@code SIN_VACANTE} (409) si el destino está lleno; {@code SOLICITUD_INVALIDA} (400)
     *                          si la sección es de otro año
     */
    @Transactional
    @Auditable(accion = "CAMBIAR_SECCION_MATRICULA", modulo = "MATRICULA", tipoEntidad = "Matricula",
            idEntidad = "#matriculaId", descripcion = "Cambio de grado o sección de una matrícula",
            condicion = "!#result.sinCambios")
    public ResultadoCambioSeccion cambiar(String matriculaId, String seccionDestinoId) {
        Matricula m = matriculas.findById(matriculaId).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe la matrícula"));
        if (m.getEstado() != EstadoMatricula.CONFIRMADA || !anios.idsNoCerrados().contains(m.getAnioEscolarId())) {
            throw new ExcepcionNegocio(CodigoError.MATRICULA_NO_VIGENTE,
                    "Solo se puede cambiar de sección una matrícula confirmada de un año vigente");
        }
        if (!catalogo.anioEscolarDe(seccionDestinoId).equals(m.getAnioEscolarId())) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA, "La sección no pertenece al año de la matrícula");
        }
        String origenId = m.getSeccionId();
        AlumnoResumen alumno = familias.alumnos(List.of(m.getAlumnoId())).get(m.getAlumnoId());
        String nombreAlumno = alumno == null ? "—" : alumno.nombreCompleto();
        SeccionResumen origen = catalogo.seccion(origenId);
        SeccionResumen destino = catalogo.seccion(seccionDestinoId);

        if (!catalogo.moverVacante(origenId, seccionDestinoId)) {
            return new ResultadoCambioSeccion(m.getId(), nombreAlumno, origen.nombre(), origen.nombre(),
                    catalogo.vacantes(origenId), catalogo.vacantes(origenId), true);
        }
        m.cambiarSeccion(seccionDestinoId);
        matriculas.saveAndFlush(m);

        int vacantesOrigen = catalogo.vacantes(origenId);
        int vacantesDestino = catalogo.vacantes(seccionDestinoId);
        ContextoAuditoria.registrarCambio("seccion", origen.nombre(), destino.nombre());
        ContextoAuditoria.registrarCambio("vacantesOrigen", vacantesOrigen - 1, vacantesOrigen);
        ContextoAuditoria.registrarCambio("vacantesDestino", vacantesDestino + 1, vacantesDestino);
        return new ResultadoCambioSeccion(m.getId(), nombreAlumno, origen.nombre(), destino.nombre(), vacantesOrigen,
                vacantesDestino, false);
    }
}
