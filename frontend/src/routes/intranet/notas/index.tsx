import { createFileRoute, Link } from '@tanstack/react-router'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { useMisAsignaciones } from '../../../modulos/evaluacion/api'

export const Route = createFileRoute('/intranet/notas/')({
  beforeLoad: () => exigirPermiso('NOTA_REGISTRAR'),
  component: MisCursos,
})

/** RF-31: la lista consolidada de los cursos y secciones que el docente imparte, con acceso al registro de notas. */
function MisCursos() {
  const asignaciones = useMisAsignaciones()
  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Registro de notas</h1>
          <p className="muted">Inicio › Registro de notas</p>
        </div>
      </div>
      {asignaciones.isPending && <p className="muted">Cargando…</p>}
      {asignaciones.isError && (
        <p className="alerta alerta-error" role="alert">
          {asignaciones.error.message}
        </p>
      )}
      {asignaciones.isSuccess && asignaciones.data.length === 0 && (
        <p className="alerta" role="status">
          No tienes cursos asignados este año. Las asignaciones se crean al publicar el horario.
        </p>
      )}
      {asignaciones.isSuccess && asignaciones.data.length > 0 && (
        <section className="tarjeta" aria-label="Mis cursos">
          <div className="tabla-scroll">
            <table aria-label="Mis cursos y secciones">
              <thead>
                <tr>
                  <th scope="col">Curso</th>
                  <th scope="col">Sección</th>
                  <th scope="col">Alumnos</th>
                  <th scope="col">Competencias</th>
                  <th scope="col">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {asignaciones.data.map((a) => (
                  <tr key={a.id}>
                    <td>{a.curso}</td>
                    <td>{a.seccion}</td>
                    <td>{a.alumnos}</td>
                    <td>{a.competencias}</td>
                    <td>
                      <Link
                        className="btn"
                        to="/intranet/notas/$curso/$seccion/$bimestre"
                        params={{ curso: a.cursoSlug, seccion: a.seccionCodigo, bimestre: a.bimestreVigente === null ? 'bimestre-actual' : `bimestre-${a.bimestreVigente}` }}
                        aria-label={`Registrar notas de ${a.curso} en ${a.seccion}`}
                      >
                        Registrar notas
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </>
  )
}
