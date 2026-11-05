import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { ApiError } from '../../../../compartido/api/errores'
import { exigirPermiso } from '../../../../compartido/auth/guardias'
import {
  romano,
  useConfiguracionCurso,
  useGuardarConfiguracion,
  useMisAsignaciones,
  type ConfiguracionCurso,
} from '../../../../modulos/evaluacion/api'
import { esquemaConfiguracion, pesoParaApi, type DatosConfiguracion } from '../../../../modulos/evaluacion/esquemas'

export const Route = createFileRoute('/intranet/cursos/$cursoSeccion/configuracion')({
  beforeLoad: () => exigirPermiso('REGLA_DEFINIR'),
  component: ConfiguracionDelCurso,
})

/** «matematica-3A» → curso «matematica», sección «3A» (el curso puede llevar guiones: se parte por el último). */
function separar(cursoSeccion: string): { curso: string; seccion: string } {
  const corte = cursoSeccion.lastIndexOf('-')
  return corte < 0 ? { curso: cursoSeccion, seccion: '' } : { curso: cursoSeccion.slice(0, corte), seccion: cursoSeccion.slice(corte + 1) }
}

function ConfiguracionDelCurso() {
  const { cursoSeccion } = Route.useParams()
  const { curso, seccion } = separar(cursoSeccion)
  const asignaciones = useMisAsignaciones()
  const asignacion = asignaciones.data?.find((a) => a.cursoSlug === curso && a.seccionCodigo.toLowerCase() === seccion.toLowerCase())
  const configuracion = useConfiguracionCurso(asignacion?.id)

  const titulo = <h1>Configuración del curso</h1>
  if (asignaciones.isPending || (asignacion && configuracion.isPending)) return <p className="muted">Cargando…</p>
  if (asignaciones.isError || configuracion.isError) {
    return (
      <>
        {titulo}
        <p className="alerta alerta-error" role="alert">
          {(asignaciones.error ?? configuracion.error)?.message}
        </p>
      </>
    )
  }
  if (!asignacion || !configuracion.data) {
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
  return <Formulario configuracion={configuracion.data} cursoSlug={curso} seccionCodigo={asignacion.seccionCodigo} />
}

function valoresDe(c: ConfiguracionCurso): DatosConfiguracion {
  return { regla: c.regla, pesos: c.pesos.map((p) => ({ bimestre: p.bimestre, peso: p.peso })) }
}

/** UC-16: el docente elige cómo se obtiene la nota final de cada competencia y, para el promedio, el peso de cada bimestre. */
function Formulario({ configuracion, cursoSlug, seccionCodigo }: { configuracion: ConfiguracionCurso; cursoSlug: string; seccionCodigo: string }) {
  const guardar = useGuardarConfiguracion(configuracion.asignacionId)
  const [guardado, setGuardado] = useState<number | null>(null)
  const form = useForm<DatosConfiguracion>({ resolver: zodResolver(esquemaConfiguracion), defaultValues: valoresDe(configuracion) })
  const elegida = useWatch({ control: form.control, name: 'regla' })
  const errores = form.formState.errors
  const errorServidor = guardar.error instanceof ApiError ? guardar.error : null
  const actual = configuracion.reglas.find((r) => r.tipo === configuracion.regla)
  const editable = configuracion.editable

  function enviar(datos: DatosConfiguracion) {
    setGuardado(null)
    guardar.mutate(
      {
        regla: datos.regla,
        pesos: datos.pesos.filter((p) => Number(pesoParaApi(p.peso)) !== 1).map((p) => ({ bimestre: p.bimestre, peso: pesoParaApi(p.peso) })),
      },
      {
        onSuccess: (respuesta) => {
          form.reset(valoresDe(respuesta))
          setGuardado(respuesta.cambios)
        },
      },
    )
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>
            Configuración del curso · {configuracion.curso} · {configuracion.seccion}
          </h1>
          <p className="muted">Inicio › Mis cursos › {configuracion.curso} {configuracion.seccion} › Configuración</p>
        </div>
      </div>

      <form className="tarjeta" aria-label="Configuración del curso" onSubmit={form.handleSubmit(enviar)} noValidate>
        <p>
          <strong>Sección</strong> {configuracion.seccion} · {configuracion.anio} · <strong>{configuracion.competencias.length} competencias</strong>
        </p>
        <ul aria-label="Competencias del curso">
          {configuracion.competencias.map((c) => (
            <li key={c.id}>
              {c.codigo} · {c.nombre}
            </li>
          ))}
        </ul>

        <h2>Regla de consolidación</h2>
        <p className="muted">Define cómo se obtiene la nota final de cada competencia a partir de los bimestres.</p>
        <p>
          <strong>Regla actual: {actual?.nombre}</strong>
        </p>

        {!editable && (
          <p className="alerta" role="status">
            El año escolar está cerrado: la regla ya no se puede cambiar.
          </p>
        )}
        {errorServidor && (
          <p className="alerta alerta-error" role="alert">
            {errorServidor.message}
          </p>
        )}
        {guardado !== null && (
          <p className="alerta alerta-ok" role="status">
            {guardado === 0 ? 'No hubo cambios que guardar.' : 'Se guardó la configuración del curso.'}
          </p>
        )}

        <fieldset className="grupo-opciones" aria-label="Reglas de consolidación" disabled={!editable}>
          {configuracion.reglas.map((r) => (
            <label key={r.tipo} className="opcion-radio">
              <input type="radio" value={r.tipo} {...form.register('regla')} />
              <span>
                <strong>{r.nombre}</strong>
                {r.porDefecto ? ' · Por defecto' : ''}
                {r.tipo === configuracion.regla ? ' · Regla actual' : ''}
                <br />
                <span className="muted">{r.descripcion}</span>
                <br />
                <span className="muted">
                  Ejemplo con los bimestres I a IV: {r.ejemplo.niveles.join(', ')} → <strong>{r.ejemplo.resultado}</strong>
                </span>
              </span>
            </label>
          ))}
        </fieldset>

        <h2>Peso de cada bimestre</h2>
        <p className="muted">
          {elegida === 'PROMEDIO_PONDERADO'
            ? 'Con el promedio ponderado todos los bimestres pesan lo mismo (1) salvo que los ajustes.'
            : 'Los pesos solo se usan con el promedio ponderado; con esta regla no cambian la nota.'}
        </p>
        <div className="dos-columnas">
          {configuracion.pesos.map((p, i) => (
            <div key={p.bimestre} className="campo">
              <label htmlFor={`peso-${p.bimestre}`}>Bimestre {romano(p.bimestre)}</label>
              <input
                id={`peso-${p.bimestre}`}
                inputMode="decimal"
                disabled={!editable}
                aria-invalid={errores.pesos?.[i]?.peso ? true : undefined}
                {...form.register(`pesos.${i}.peso`)}
              />
              <input type="hidden" {...form.register(`pesos.${i}.bimestre`, { valueAsNumber: true })} />
              {errores.pesos?.[i]?.peso && <div className="error-campo">{errores.pesos[i]?.peso?.message}</div>}
            </div>
          ))}
        </div>

        <div className="acciones">
          <Link className="btn" to="/intranet/notas/$curso/$seccion/$bimestre" params={{ curso: cursoSlug, seccion: seccionCodigo, bimestre: 'bimestre-actual' }}>
            Volver al registro de notas
          </Link>
          <button type="submit" className="btn btn-primario" disabled={!editable || guardar.isPending}>
            Guardar configuración
          </button>
        </div>
      </form>
    </>
  )
}
