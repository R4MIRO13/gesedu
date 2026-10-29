import { useState } from 'react'
import { ApiError } from '../../../compartido/api/errores'
import { hhmm } from '../../../compartido/formato'
import {
  NOMBRE_DIA,
  useEliminarAsignacion,
  useGuardarAsignacion,
  useOpciones,
  useValidacion,
  type Asignacion,
  type BloqueHorario,
  type ConflictoHorario,
} from '../api'

interface Props {
  anio: number
  seccionId: string
  seccionNombre: string
  bloque: BloqueHorario
  /** La clase que ya está en el bloque (para modificarla o eliminarla); ausente al agregar una nueva. */
  asignacion?: Asignacion
  /** Versión del borrador que se está viendo: va en If-Match. */
  version: number
  onTerminar: () => void
  /** Vuelve a pedir el borrador (tras un 409 por versión). */
  onRecargar: () => void
}

/**
 * Panel de asignación de UC-21 (pasos 5 a 9): elegir curso y docente para un bloque de una sección. Muestra en
 * vivo si el docente está disponible o ya tiene clase en ese bloque (RF-47, RF-42). El botón Guardar no se
 * bloquea por los avisos: el servidor es quien rechaza el guardado y explica la causa (409 / 422).
 */
export function PanelAsignacion({
  anio,
  seccionId,
  seccionNombre,
  bloque,
  asignacion,
  version,
  onTerminar,
  onRecargar,
}: Props) {
  const [cursoId, setCursoId] = useState(asignacion?.cursoId ?? '')
  const [docenteId, setDocenteId] = useState(asignacion?.docenteId ?? '')
  const opciones = useOpciones(anio, {
    bloqueId: bloque.id,
    seccionId,
    cursoId: cursoId || undefined,
    asignacionId: asignacion?.id,
  })
  const solicitud =
    cursoId && docenteId
      ? { bloqueId: bloque.id, seccionId, cursoId, docenteId, asignacionId: asignacion?.id }
      : null
  const validacion = useValidacion(anio, solicitud)
  const guardar = useGuardarAsignacion(anio)
  const eliminar = useEliminarAsignacion(anio)

  const error = guardar.error ?? eliminar.error
  const versionVencida = error instanceof ApiError && error.codigo === 'CONFLICTO_CONCURRENCIA'
  const conflictosGuardado = error instanceof ApiError ? conflictosDe(error) : []
  const avisos = validacion.data?.conflictos ?? []

  function cambiarCurso(nuevo: string) {
    setCursoId(nuevo)
    // Los docentes ofrecidos dependen del curso
    setDocenteId('')
    guardar.reset()
  }

  function enviar() {
    if (!solicitud) return
    guardar.mutate({ solicitud, version, asignacionId: asignacion?.id }, { onSuccess: onTerminar })
  }

  function quitar() {
    if (!asignacion) return
    eliminar.mutate({ asignacionId: asignacion.id, version }, { onSuccess: onTerminar })
  }

  return (
    <section className="tarjeta panel-asignacion" aria-label="Asignación de clase">
      <h2>
        {asignacion ? 'Modificar clase' : 'Agregar clase'} · {seccionNombre}
      </h2>
      <p className="muted">
        {NOMBRE_DIA[bloque.diaSemana]} {hhmm(bloque.horaInicio)} – {hhmm(bloque.horaFin)}
      </p>

      <fieldset className="grupo-opciones">
        <legend>Curso</legend>
        {opciones.isPending && <p className="muted">Cargando…</p>}
        {opciones.data?.cursos.map((c) => (
          <label key={c.cursoId} className="opcion-radio">
            <input
              type="radio"
              name="curso"
              value={c.cursoId}
              checked={cursoId === c.cursoId}
              onChange={() => cambiarCurso(c.cursoId)}
            />
            <span>{c.curso}</span>
            <span className="muted">
              {c.horasAsignadas} de {c.horasRequeridas} h
            </span>
          </label>
        ))}
      </fieldset>

      {cursoId && (
        <fieldset className="grupo-opciones">
          <legend>Docente</legend>
          {opciones.data?.docentes.length === 0 && (
            <p className="muted">Ningún docente está asociado a este curso.</p>
          )}
          {opciones.data?.docentes.map((d) => (
            <label key={d.docenteId} className="opcion-radio">
              <input
                type="radio"
                name="docente"
                value={d.docenteId}
                checked={docenteId === d.docenteId}
                onChange={() => {
                  setDocenteId(d.docenteId)
                  guardar.reset()
                }}
              />
              <span>{d.nombre}</span>
              <span className={estadoDocente(d.disponible, d.ocupadoEn).clase}>
                {d.detalleOcupado ?? estadoDocente(d.disponible, d.ocupadoEn).texto}
              </span>
            </label>
          ))}
        </fieldset>
      )}

      {avisos.length > 0 && !error && (
        <div className="alerta alerta-error" role="alert">
          {avisos.map((a) => (
            <p key={`${a.tipo}-${a.asignacionId ?? ''}`}>{a.mensaje}</p>
          ))}
        </div>
      )}
      {solicitud && validacion.data?.valido && !error && (
        <p className="alerta alerta-ok" role="status">
          Sin conflictos: se puede guardar.
        </p>
      )}

      {error && (
        <div className="alerta alerta-error" role="alert">
          {versionVencida ? (
            <>
              <p>El horario cambió mientras lo editabas. Recarga la matriz para ver la versión más reciente.</p>
              <button
                type="button"
                className="btn"
                onClick={() => {
                  guardar.reset()
                  eliminar.reset()
                  onRecargar()
                }}
              >
                Recargar matriz
              </button>
            </>
          ) : conflictosGuardado.length > 0 ? (
            conflictosGuardado.map((c) => <p key={`${c.tipo}-${c.asignacionId ?? ''}`}>{c.mensaje}</p>)
          ) : (
            <p>{error.message}</p>
          )}
        </div>
      )}

      <div className="acciones">
        <button
          type="button"
          className="btn btn-primario"
          disabled={!solicitud || guardar.isPending || eliminar.isPending}
          onClick={enviar}
        >
          {guardar.isPending ? 'Guardando…' : 'Guardar'}
        </button>
        {asignacion && (
          <button type="button" className="btn" disabled={eliminar.isPending || guardar.isPending} onClick={quitar}>
            Eliminar clase
          </button>
        )}
        <button type="button" className="btn" onClick={onTerminar}>
          Cancelar
        </button>
      </div>
    </section>
  )
}

function estadoDocente(disponible: boolean, ocupadoEn: string | null) {
  if (!disponible) return { texto: 'No disponible', clase: 'etiqueta etiqueta-no' }
  if (ocupadoEn) return { texto: `Tiene clase en ${ocupadoEn}`, clase: 'etiqueta etiqueta-no' }
  return { texto: 'Disponible', clase: 'etiqueta etiqueta-si' }
}

/** Los conflictos que el servidor adjunta a un 409 / 422 de horario. */
function conflictosDe(error: ApiError): ConflictoHorario[] {
  const lista = error.propiedades.conflictos
  return Array.isArray(lista)
    ? lista.filter((c): c is ConflictoHorario => typeof c === 'object' && c !== null && 'mensaje' in c)
    : []
}
