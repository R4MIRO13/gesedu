import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { ApiError } from '../../../../compartido/api/errores'
import { exigirPermiso } from '../../../../compartido/auth/guardias'
import { usePermiso } from '../../../../compartido/auth/usePermiso'
import {
  useCopiaAnterior,
  useCopiarCursos,
  useCrearCurso,
  useCursos,
  useEliminarCurso,
  useModificarCurso,
  type Curso,
  type CursoCopiable,
} from '../../../../modulos/academico/api'
import { esquemaCurso, type DatosCurso } from '../../../../modulos/academico/esquemas'
import { resolverAnioActivo } from '../../../../modulos/academico/anioActivo'

export const Route = createFileRoute('/intranet/academico/$anio/cursos')({
  beforeLoad: async ({ params, location }: { params: { anio: string }; location: { href: string } }) => {
    exigirPermiso('ACADEMICO_CONSULTAR')
    await resolverAnioActivo(params, location.href)
  },
  component: CursosYCompetencias,
})

const NUEVO = 'nuevo'

function plural(n: number, uno: string, varios: string) {
  return `${n} ${n === 1 ? uno : varios}`
}

/** UC-14, Cursos y competencias del año: lista, formulario del curso y copia del año anterior. */
function CursosYCompetencias() {
  const { anio: anioTexto } = Route.useParams()
  const anio = Number(anioTexto)
  const cursos = useCursos(anio)
  const puedeConfigurar = usePermiso('ACADEMICO_CONFIGURAR')
  const [seleccion, setSeleccion] = useState<string | null>(null)
  const [copiando, setCopiando] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)
  const copia = useCopiaAnterior(anio, puedeConfigurar)

  if (cursos.isPending) return <p className="muted">Cargando…</p>
  if (cursos.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {cursos.error.message}
      </p>
    )
  }
  const datos = cursos.data
  const cerrado = datos.estado === 'CERRADO'
  const editable = puedeConfigurar && !cerrado
  const elegido = seleccion === NUEVO ? null : (datos.cursos.find((c) => c.id === seleccion) ?? null)
  const hayCopia = (copia.data?.anioOrigen ?? null) !== null && editable

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Cursos y competencias · Año escolar {anio}</h1>
          <p className="muted">Los cursos son los mismos para todos los grados, de 1° a 5° de secundaria.</p>
        </div>
        {cerrado && <span className="etiqueta etiqueta-alerta">Año cerrado: solo consulta</span>}
      </div>
      {aviso && (
        <p className="alerta alerta-ok" role="status">
          {aviso}
        </p>
      )}

      <div className="dos-columnas">
        <section className="tarjeta" aria-label="Cursos del año">
          <h2>
            Cursos <span className="muted">{plural(datos.cursos.length, 'curso', 'cursos')} · {datos.totalHoras} h semanales</span>
          </h2>
          {datos.cursos.length === 0 && <p className="muted">Este año aún no tiene cursos.</p>}
          <ul className="lista-cursos">
            {datos.cursos.map((c) => (
              <li key={c.id}>
                <button
                  type="button"
                  className={c.id === seleccion ? 'curso-item curso-item-activo' : 'curso-item'}
                  aria-pressed={c.id === seleccion}
                  onClick={() => {
                    setAviso(null)
                    setCopiando(false)
                    setSeleccion(c.id)
                  }}
                >
                  <strong>{c.nombre}</strong>
                  <span className="muted">
                    {plural(c.horasLectivas, 'hora semanal', 'horas semanales')} · {plural(c.competencias.length, 'competencia', 'competencias')}
                  </span>
                </button>
              </li>
            ))}
          </ul>
          {editable && (
            <div className="acciones">
              <button
                type="button"
                className="btn btn-primario"
                onClick={() => {
                  setAviso(null)
                  setCopiando(false)
                  setSeleccion(NUEVO)
                }}
              >
                + Nuevo curso
              </button>
              {hayCopia && (
                <button
                  type="button"
                  className="btn"
                  onClick={() => {
                    setAviso(null)
                    setSeleccion(null)
                    setCopiando(true)
                  }}
                >
                  Copiar configuración de {copia.data?.anioOrigen}
                </button>
              )}
            </div>
          )}
        </section>

        <section className="tarjeta" aria-label="Detalle del curso">
          {copiando && copia.data && (
            <PanelCopia
              anio={anio}
              origen={copia.data.anioOrigen ?? 0}
              cursos={copia.data.cursos}
              alTerminar={(mensaje) => {
                setCopiando(false)
                setAviso(mensaje)
              }}
              alCancelar={() => setCopiando(false)}
            />
          )}
          {!copiando && seleccion === null && <p className="muted">Elige un curso para ver sus competencias{editable ? ' o crea uno nuevo' : ''}.</p>}
          {!copiando && seleccion !== null && (elegido || seleccion === NUEVO) && (
            <FormularioCurso
              key={seleccion}
              anio={anio}
              curso={elegido}
              editable={editable}
              alGuardar={(curso, creado) => {
                setSeleccion(curso.id)
                setAviso(creado ? `Se creó el curso ${curso.nombre} con ${plural(curso.competencias.length, 'competencia', 'competencias')}.` : `Se guardó el curso ${curso.nombre}.`)
              }}
              alEliminar={(nombre) => {
                setSeleccion(null)
                setAviso(`Se eliminó el curso ${nombre}.`)
              }}
              alCancelar={() => setSeleccion(null)}
            />
          )}
        </section>
      </div>
    </>
  )
}

function valoresDe(curso: Curso | null): DatosCurso {
  return {
    nombre: curso?.nombre ?? '',
    horasLectivas: curso ? String(curso.horasLectivas) : '',
    competencias: curso
      ? curso.competencias.map((c) => ({ id: c.id, codigo: c.codigo, nombre: c.nombre, nivelEsperado: c.nivelEsperado ?? '' }))
      : [],
  }
}

function FormularioCurso({
  anio,
  curso,
  editable,
  alGuardar,
  alEliminar,
  alCancelar,
}: {
  anio: number
  curso: Curso | null
  editable: boolean
  alGuardar: (curso: Curso, creado: boolean) => void
  alEliminar: (nombre: string) => void
  alCancelar: () => void
}) {
  const crear = useCrearCurso(anio)
  const modificar = useModificarCurso(anio)
  const eliminar = useEliminarCurso(anio)
  const [nueva, setNueva] = useState('')
  const [confirmarBaja, setConfirmarBaja] = useState(false)
  const form = useForm<DatosCurso>({ resolver: zodResolver(esquemaCurso), defaultValues: valoresDe(curso) })
  const competencias = useFieldArray({ control: form.control, name: 'competencias' })
  const errores = form.formState.errors
  const error = [crear.error, modificar.error, eliminar.error].find((e) => e instanceof ApiError) ?? null

  function enviar(datos: DatosCurso) {
    const cuerpo = {
      nombre: datos.nombre.trim(),
      horasLectivas: Number(datos.horasLectivas),
      competencias: datos.competencias.map((c) => ({
        ...(c.id ? { id: c.id } : {}),
        nombre: c.nombre.trim(),
        nivelEsperado: c.nivelEsperado?.trim() ? c.nivelEsperado.trim() : null,
      })),
    }
    if (curso) {
      modificar.mutate({ id: curso.id, ...cuerpo }, { onSuccess: (guardado) => alGuardar(guardado, false) })
    } else {
      crear.mutate(cuerpo, { onSuccess: (guardado) => alGuardar(guardado, true) })
    }
  }

  function agregarCompetencia() {
    const nombre = nueva.trim()
    if (!nombre) return
    competencias.append({ nombre, nivelEsperado: '' })
    setNueva('')
  }

  const siguienteCodigo = `C${(curso?.competencias.length ?? 0) + competencias.fields.filter((c) => !c.id).length + 1}`

  return (
    <form onSubmit={form.handleSubmit(enviar)} noValidate aria-label={curso ? 'Editar curso' : 'Nuevo curso'} className="formulario-curso">
      <h2>{curso ? 'Editar curso' : 'Nuevo curso'}</h2>
      <p className="muted">Aplica de 1° a 5° de secundaria.</p>
      {error && (
        <p className="alerta alerta-error" role="alert">
          {error instanceof ApiError ? error.message : 'No se pudo guardar. Inténtalo de nuevo.'}
        </p>
      )}
      <div className="filtros">
        <label className="campo">
          Nombre del curso *
          <input {...form.register('nombre')} disabled={!editable} aria-invalid={!!errores.nombre} />
          {errores.nombre && <span className="error-campo">{errores.nombre.message}</span>}
        </label>
        <label className="campo campo-corto">
          Horas lectivas semanales *
          <input inputMode="numeric" {...form.register('horasLectivas')} disabled={!editable} aria-invalid={!!errores.horasLectivas} />
          {errores.horasLectivas && <span className="error-campo">{errores.horasLectivas.message}</span>}
        </label>
      </div>

      <h3>
        Competencias del curso <span className="muted">{plural(competencias.fields.length, 'competencia', 'competencias')} · se evalúan con AD, A, B o C</span>
      </h3>
      {errores.competencias?.root && <p className="error-campo">{errores.competencias.root.message}</p>}
      {errores.competencias?.message && <p className="error-campo">{errores.competencias.message}</p>}
      <ul className="lista-competencias">
        {competencias.fields.map((campo, i) => (
          <li key={campo.id}>
            <span className="codigo-competencia">{campo.codigo ?? 'nueva'}</span>
            <input
              aria-label={`Competencia ${campo.codigo ?? 'nueva'}`}
              {...form.register(`competencias.${i}.nombre`)}
              disabled={!editable}
              aria-invalid={!!errores.competencias?.[i]?.nombre}
            />
            {editable && (
              <button type="button" className="btn" onClick={() => competencias.remove(i)} aria-label={`Quitar la competencia ${campo.codigo ?? 'nueva'}`}>
                Eliminar
              </button>
            )}
            {errores.competencias?.[i]?.nombre && <span className="error-campo">{errores.competencias[i].nombre.message}</span>}
          </li>
        ))}
      </ul>
      {editable && (
        <div className="fila-agregar">
          <span className="codigo-competencia">{siguienteCodigo}</span>
          <input
            aria-label="Nombre de la nueva competencia"
            placeholder="Escribe el nombre de la nueva competencia"
            value={nueva}
            onChange={(e) => setNueva(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault()
                agregarCompetencia()
              }
            }}
          />
          <button type="button" className="btn" onClick={agregarCompetencia}>
            Agregar competencia
          </button>
        </div>
      )}

      {editable && (
        <div className="acciones">
          <button type="button" className="btn" onClick={alCancelar}>
            Cancelar
          </button>
          <button type="submit" className="btn btn-primario" disabled={crear.isPending || modificar.isPending}>
            Guardar curso
          </button>
          {curso && !confirmarBaja && (
            <button type="button" className="btn" onClick={() => setConfirmarBaja(true)}>
              Eliminar curso
            </button>
          )}
          {curso && confirmarBaja && (
            <>
              <button
                type="button"
                className="btn btn-primario"
                disabled={eliminar.isPending}
                onClick={() => eliminar.mutate(curso.id, { onSuccess: () => alEliminar(curso.nombre), onSettled: () => setConfirmarBaja(false) })}
              >
                Confirmar eliminación
              </button>
              <button type="button" className="btn" onClick={() => setConfirmarBaja(false)}>
                No eliminar
              </button>
            </>
          )}
        </div>
      )}
    </form>
  )
}

/** FA-1: la configuración del año anterior, con los cursos que se copiarán (los que ya existen no se copian). */
function PanelCopia({
  anio,
  origen,
  cursos,
  alTerminar,
  alCancelar,
}: {
  anio: number
  origen: number
  cursos: CursoCopiable[]
  alTerminar: (mensaje: string) => void
  alCancelar: () => void
}) {
  const copiar = useCopiarCursos(anio)
  const disponibles = cursos.filter((c) => !c.yaExiste)
  const [elegidos, setElegidos] = useState<string[]>(disponibles.map((c) => c.id))

  function alternar(id: string) {
    setElegidos((actual) => (actual.includes(id) ? actual.filter((x) => x !== id) : [...actual, id]))
  }

  function confirmar() {
    copiar.mutate(elegidos, {
      onSuccess: (r) =>
        alTerminar(
          `Se copiaron ${plural(r.copiados.length, 'curso', 'cursos')} de ${r.anioOrigen} con sus competencias.` +
            (r.omitidos.length > 0 ? ` Ya existían: ${r.omitidos.join(', ')}.` : ''),
        ),
    })
  }

  return (
    <div aria-label={`Copiar configuración de ${origen}`}>
      <h2>Copiar configuración de {origen}</h2>
      <p className="muted">Se copian los cursos que elijas con sus competencias. Los que el año {anio} ya tiene no se vuelven a copiar.</p>
      {copiar.error instanceof ApiError && (
        <p className="alerta alerta-error" role="alert">
          {copiar.error.message}
        </p>
      )}
      <ul className="lista-competencias">
        {cursos.map((c) => (
          <li key={c.id}>
            <label className="opcion-copia">
              <input
                type="checkbox"
                checked={!c.yaExiste && elegidos.includes(c.id)}
                disabled={c.yaExiste}
                onChange={() => alternar(c.id)}
              />
              <span>
                <strong>{c.nombre}</strong> <span className="muted">{c.horasLectivas} h · {plural(c.competencias.length, 'competencia', 'competencias')}</span>
                {c.yaExiste && <span className="etiqueta"> Ya existe</span>}
              </span>
            </label>
          </li>
        ))}
      </ul>
      <div className="acciones">
        <button type="button" className="btn" onClick={alCancelar}>
          Cancelar
        </button>
        <button type="button" className="btn btn-primario" disabled={copiar.isPending || elegidos.length === 0} onClick={confirmar}>
          Confirmar copia
        </button>
      </div>
    </div>
  )
}
