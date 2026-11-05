import { createFileRoute, Link } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { ApiError } from '../../../../../compartido/api/errores'
import { exigirPermiso } from '../../../../../compartido/auth/guardias'
import { fechaCorta } from '../../../../../compartido/formato'
import {
  NIVELES,
  NOMBRE_NIVEL,
  romano,
  useGuardarLogros,
  useMisAsignaciones,
  useRejilla,
  type CeldaLogro,
  type NivelLogro,
  type Rejilla,
} from '../../../../../modulos/evaluacion/api'

export const Route = createFileRoute('/intranet/notas/$curso/$seccion/$bimestre')({
  beforeLoad: () => exigirPermiso('NOTA_REGISTRAR'),
  component: RegistroDeNotas,
})

/** «bimestre-3» → 3; «bimestre-actual» → nulo (el servidor elige el vigente). */
function numeroDeBimestre(segmento: string): number | null | 'invalido' {
  if (segmento === 'bimestre-actual') return null
  const coincidencia = /^bimestre-([1-4])$/.exec(segmento)
  return coincidencia ? Number(coincidencia[1]) : 'invalido'
}

function RegistroDeNotas() {
  const { curso, seccion, bimestre } = Route.useParams()
  const numero = numeroDeBimestre(bimestre)
  const asignaciones = useMisAsignaciones()
  const asignacion = asignaciones.data?.find((a) => a.cursoSlug === curso && a.seccionCodigo.toLowerCase() === seccion.toLowerCase())
  const rejilla = useRejilla(asignacion?.id, numero === 'invalido' ? null : numero)

  const titulo = <h1>Registro de notas</h1>
  if (numero === 'invalido') {
    return (
      <>
        {titulo}
        <p className="alerta alerta-error" role="alert">
          El bimestre «{bimestre}» no existe. Usa una dirección como bimestre-1 a bimestre-4.
        </p>
      </>
    )
  }
  if (asignaciones.isPending || (asignacion && rejilla.isPending)) return <p className="muted">Cargando…</p>
  if (asignaciones.isError) {
    return (
      <>
        {titulo}
        <p className="alerta alerta-error" role="alert">
          {asignaciones.error.message}
        </p>
      </>
    )
  }
  if (!asignacion) {
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
  if (rejilla.isError) {
    return (
      <>
        {titulo}
        <p className="alerta alerta-error" role="alert">
          {rejilla.error.message}
        </p>
      </>
    )
  }
  if (!rejilla.data) return <p className="muted">Cargando…</p>
  return (
    <RejillaLogros
      // Al cambiar de bimestre se vuelve a empezar con los valores de ese bimestre
      key={`${rejilla.data.asignacionId}-${rejilla.data.bimestre.numero}`}
      curso={curso}
      seccion={seccion}
      numeroSolicitado={numero}
      datos={rejilla.data}
    />
  )
}

const clave = (alumnoId: string, competenciaId: string) => `${alumnoId}|${competenciaId}`

function borradorDe(datos: Rejilla): Record<string, NivelLogro | ''> {
  const borrador: Record<string, NivelLogro | ''> = {}
  for (const fila of datos.alumnos) {
    for (const competencia of datos.competencias) {
      borrador[clave(fila.alumnoId, competencia.id)] = fila.logros[competencia.id] ?? ''
    }
  }
  return borrador
}

/** UC-15: el docente califica con AD, A, B o C a cada alumno en cada competencia del bimestre vigente. */
function RejillaLogros({ curso, seccion, numeroSolicitado, datos }: { curso: string; seccion: string; numeroSolicitado: number | null; datos: Rejilla }) {
  const guardar = useGuardarLogros(datos.asignacionId, numeroSolicitado)
  const [borrador, setBorrador] = useState(() => borradorDe(datos))
  const [resultado, setResultado] = useState<{ cambios: number; aviso: string | null } | null>(null)
  const error = guardar.error instanceof ApiError ? guardar.error : null
  const celdasConError = new Set((error?.errores ?? []).map((e) => e.campo.replace('logros.', '').replace('.', '|')))

  const cambios = useMemo<CeldaLogro[]>(() => {
    const lista: CeldaLogro[] = []
    for (const fila of datos.alumnos) {
      for (const competencia of datos.competencias) {
        const nivel = borrador[clave(fila.alumnoId, competencia.id)]
        if (nivel && nivel !== fila.logros[competencia.id]) {
          lista.push({ alumnoId: fila.alumnoId, competenciaId: competencia.id, nivel })
        }
      }
    }
    return lista
  }, [borrador, datos])

  const completas = Object.values(borrador).filter((v) => v !== '').length
  const incompletos = datos.alumnos
    .map((fila) => ({
      alumno: `${fila.apellidos} ${fila.nombres}`,
      faltan: datos.competencias.filter((c) => borrador[clave(fila.alumnoId, c.id)] === '').map((c) => c.codigo),
    }))
    .filter((x) => x.faltan.length > 0)

  function guardarRegistro() {
    setResultado(null)
    guardar.mutate(cambios, {
      onSuccess: (respuesta) => {
        setBorrador(borradorDe(respuesta))
        setResultado({ cambios: respuesta.cambios, aviso: respuesta.aviso })
      },
    })
  }

  const b = datos.bimestre
  return (
    <>
      <div className="encabezado">
        <div>
          <h1>
            Registro de notas · {datos.curso} · {datos.seccion} · Bimestre {romano(b.numero)}
          </h1>
          <p className="muted">Inicio › Registro de notas › {datos.curso} {datos.seccion}</p>
        </div>
      </div>

      <section className="tarjeta" aria-label="Datos del registro">
        <p>
          <strong>Curso</strong> {datos.curso} · <strong>Sección</strong> {datos.seccion} · <strong>Bimestre</strong> {romano(b.numero)}
          {datos.editable ? ' (vigente)' : ''} · {fechaCorta(b.fechaInicio)} al {fechaCorta(b.fechaFin)}
        </p>
        <nav aria-label="Bimestres" className="acciones">
          {datos.bimestres.map((x) => (
            <Link
              key={x.numero}
              className={x.numero === b.numero ? 'btn btn-primario' : 'btn'}
              to="/intranet/notas/$curso/$seccion/$bimestre"
              params={{ curso, seccion, bimestre: `bimestre-${x.numero}` }}
              aria-current={x.numero === b.numero ? 'page' : undefined}
            >
              Bimestre {romano(x.numero)}
              {x.vigente ? ' (vigente)' : ''}
            </Link>
          ))}
        </nav>
        <p className="muted">
          Escala de calificación: {NIVELES.map((n) => `${n} ${NOMBRE_NIVEL[n]}`).join(' · ')}
        </p>

        {datos.mensaje && (
          <p className="alerta" role="status">
            {datos.mensaje}
          </p>
        )}
        {datos.avisoConsolidacion && (
          <p className="alerta" role="status">
            {datos.avisoConsolidacion}
          </p>
        )}
        {error && (
          <p className="alerta alerta-error" role="alert">
            {error.message}
          </p>
        )}
        {resultado && (
          <p className="alerta alerta-ok" role="status">
            {resultado.cambios === 0
              ? 'No hubo cambios que guardar.'
              : `Se guardó el registro: ${resultado.cambios} ${resultado.cambios === 1 ? 'celda' : 'celdas'}.`}
          </p>
        )}
        {incompletos.length > 0 && datos.alumnos.length > 0 && (
          <p className="alerta" role="status">
            Hay {incompletos.length} {incompletos.length === 1 ? 'alumno' : 'alumnos'} con competencias sin calificar:{' '}
            {incompletos.map((x) => `${x.alumno} (${x.faltan.join(', ')})`).join(' · ')}
          </p>
        )}

        <p className="muted">
          {datos.alumnos.length} alumnos · {datos.competencias.length} competencias · {completas}/{datos.celdasTotales} celdas completas
        </p>
        {datos.alumnos.length === 0 ? (
          <p className="alerta" role="status">
            La sección no tiene alumnos matriculados.
          </p>
        ) : (
          <div className="tabla-scroll">
            <table aria-label="Rejilla de logros">
              <thead>
                <tr>
                  <th scope="col">Estudiante</th>
                  {datos.competencias.map((c) => (
                    <th key={c.id} scope="col" title={c.nombre}>
                      {c.codigo}
                    </th>
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
                        const k = clave(fila.alumnoId, c.id)
                        const valor = borrador[k] ?? ''
                        return (
                          <td key={c.id}>
                            {datos.editable ? (
                              <select
                                name={`logro-${k}`}
                                id={`logro-${fila.alumnoId}-${c.id}`}
                                aria-label={`${nombre} · ${c.codigo}`}
                                aria-invalid={celdasConError.has(k) || undefined}
                                value={valor}
                                onChange={(e) => setBorrador((actual) => ({ ...actual, [k]: e.target.value as NivelLogro | '' }))}
                              >
                                <option value="">—</option>
                                {NIVELES.map((n) => (
                                  <option key={n} value={n}>
                                    {n}
                                  </option>
                                ))}
                              </select>
                            ) : (
                              <span aria-label={`${nombre} · ${c.codigo}`}>{valor || '—'}</span>
                            )}
                          </td>
                        )
                      })}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        <p className="muted">Pasa el cursor sobre C1 a C{datos.competencias.length} para ver el nombre completo de la competencia.</p>
        <div className="acciones">
          <Link className="btn" to="/intranet/notas">
            Cancelar
          </Link>
          <button type="button" className="btn btn-primario" disabled={!datos.editable || cambios.length === 0 || guardar.isPending} onClick={guardarRegistro}>
            Guardar registro
          </button>
        </div>
      </section>
    </>
  )
}
