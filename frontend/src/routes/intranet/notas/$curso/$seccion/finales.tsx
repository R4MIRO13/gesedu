import { createFileRoute, Link } from '@tanstack/react-router'
import { ApiError } from '../../../../../compartido/api/errores'
import { exigirPermiso } from '../../../../../compartido/auth/guardias'
import {
  NOMBRE_REGLA,
  romano,
  useCalcularNotasFinales,
  useMisAsignaciones,
  useNotasFinales,
  type NotasFinales,
} from '../../../../../modulos/evaluacion/api'

export const Route = createFileRoute('/intranet/notas/$curso/$seccion/finales')({
  beforeLoad: () => exigirPermiso('NOTA_CALCULAR_FINAL'),
  component: NotasFinalesDelCurso,
})

function NotasFinalesDelCurso() {
  const { curso, seccion } = Route.useParams()
  const asignaciones = useMisAsignaciones()
  const asignacion = asignaciones.data?.find((a) => a.cursoSlug === curso && a.seccionCodigo.toLowerCase() === seccion.toLowerCase())
  const notas = useNotasFinales(asignacion?.id)

  const titulo = <h1>Notas finales</h1>
  if (asignaciones.isPending || (asignacion && notas.isPending)) return <p className="muted">Cargando…</p>
  if (asignaciones.isError || notas.isError) {
    return (
      <>
        {titulo}
        <p className="alerta alerta-error" role="alert">
          {(asignaciones.error ?? notas.error)?.message}
        </p>
      </>
    )
  }
  if (!asignacion || !notas.data) {
    return (
      <>
        {titulo}
        <p className="alerta alerta-error" role="alert">
          No dictas este curso en esa sección, o todavía no tiene una asignación: se crea al publicar el horario.
        </p>
        <p>
          <Link to="/intranet/notas">Volver a mis cursos</Link>
        </p>
      </>
    )
  }
  return <TablaNotas datos={notas.data} curso={curso} seccion={asignacion.seccionCodigo} />
}

/** UC-17: el docente ve el logro de cada bimestre de sus alumnos y calcula la nota final de cada competencia. */
function TablaNotas({ datos, curso, seccion }: { datos: NotasFinales; curso: string; seccion: string }) {
  const calcular = useCalcularNotasFinales(datos.asignacionId)
  const error = calcular.error instanceof ApiError ? calcular.error : null
  const resultado = calcular.data?.calculo ?? null

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>
            Notas finales · {datos.curso} · {datos.seccion}
          </h1>
          <p className="muted">Inicio › Registro de notas › {datos.curso} {datos.seccion} › Notas finales</p>
        </div>
      </div>

      <section className="tarjeta" aria-label="Notas finales del curso">
        <p>
          <strong>Regla de consolidación:</strong> {NOMBRE_REGLA[datos.regla]} ·{' '}
          <Link to="/intranet/cursos/$cursoSeccion/configuracion" params={{ cursoSeccion: `${curso}-${seccion}` }}>
            Cambiar regla
          </Link>
        </p>

        {!datos.editable && (
          <p className="alerta" role="status">
            El año escolar está cerrado: las notas finales ya no se calculan.
          </p>
        )}
        {error && (
          <p className="alerta alerta-error" role="alert">
            {error.message}
          </p>
        )}
        {resultado && (
          <p className="alerta alerta-ok" role="status">
            {resultado.creadas + resultado.actualizadas === 0
              ? `Las notas finales ya estaban al día (${resultado.sinCambios} sin cambios).`
              : `Se calcularon las notas finales: ${resultado.creadas} ${resultado.creadas === 1 ? 'nueva' : 'nuevas'}, ${resultado.actualizadas} ${resultado.actualizadas === 1 ? 'actualizada' : 'actualizadas'} y ${resultado.sinCambios} sin cambios.`}
          </p>
        )}
        {datos.desactualizada && (
          <p className="alerta" role="status">
            Las notas finales están desactualizadas: después del último cálculo se registraron logros o se cambió la regla o los pesos. Vuelve
            a calcularlas.
          </p>
        )}
        {datos.celdasSinNota > 0 && (
          <p className="alerta" role="status">
            {datos.celdasSinNota} {datos.celdasSinNota === 1 ? 'competencia' : 'competencias'} de los alumnos no tienen ningún logro registrado
            y no recibirán nota final.
          </p>
        )}

        {datos.alumnos.length === 0 ? (
          <p className="alerta" role="status">
            La sección no tiene alumnos matriculados.
          </p>
        ) : (
          <div className="tabla-scroll">
            <table aria-label="Notas finales por competencia">
              <thead>
                <tr>
                  <th scope="col" rowSpan={2}>
                    Estudiante
                  </th>
                  {datos.competencias.map((c) => (
                    <th key={c.id} scope="colgroup" colSpan={datos.bimestres.length + 1} title={c.nombre}>
                      {c.codigo}
                    </th>
                  ))}
                </tr>
                <tr>
                  {datos.competencias.map((c) => (
                    <FragmentoEncabezado key={c.id} bimestres={datos.bimestres} />
                  ))}
                </tr>
              </thead>
              <tbody>
                {datos.alumnos.map((fila) => {
                  const nombre = `${fila.apellidos}, ${fila.nombres}`
                  return (
                    <tr key={fila.alumnoId}>
                      <th scope="row">{nombre}</th>
                      {datos.competencias.map((c) => {
                        const celda = fila.competencias[c.id]
                        return [
                          ...datos.bimestres.map((b) => <td key={`${c.id}-${b}`}>{celda?.bimestres[String(b)] ?? '—'}</td>),
                          <td key={`${c.id}-final`}>
                            <strong aria-label={`${nombre} · ${c.codigo} · nota final`}>{celda?.anual ?? '—'}</strong>
                          </td>,
                        ]
                      })}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        <div className="acciones">
          <Link className="btn" to="/intranet/notas">
            Volver a mis cursos
          </Link>
          <button type="button" className="btn btn-primario" disabled={!datos.editable || calcular.isPending} onClick={() => calcular.mutate()}>
            Calcular notas finales
          </button>
        </div>
      </section>
    </>
  )
}

function FragmentoEncabezado({ bimestres }: { bimestres: number[] }) {
  return (
    <>
      {bimestres.map((b) => (
        <th key={b} scope="col">
          {romano(b)}
        </th>
      ))}
      <th scope="col">Final</th>
    </>
  )
}
