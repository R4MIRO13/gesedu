import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { z } from 'zod'
import { ApiError } from '../../compartido/api/errores'
import { exigirPermiso } from '../../compartido/auth/guardias'
import { MatrizHorario } from '../../compartido/componentes/MatrizHorario'
import { fechaCorta, hhmm } from '../../compartido/formato'
import { ANIO_TRABAJO } from '../../compartido/menu'
import {
  HORARIO_NO_PUBLICADO,
  NOMBRE_DIA,
  useHorarioVigente,
  type Asignacion,
  type BloqueHorario,
  type HorarioVigente,
  type Perspectiva,
} from '../../modulos/horarios/api'

const esquemaBusqueda = z.object({
  perspectiva: z.enum(['SECCION', 'DOCENTE']).optional().catch(undefined),
  seccionId: z.string().optional().catch(undefined),
  docenteId: z.string().optional().catch(undefined),
})

export const Route = createFileRoute('/intranet/horario')({
  beforeLoad: () => exigirPermiso('HORARIO_CONSULTAR'),
  validateSearch: (busqueda) => esquemaBusqueda.parse(busqueda),
  component: MiHorario,
})

/** UC-23, Consultar horario: la última versión publicada, por sección o por docente según el alcance del rol. */
function MiHorario() {
  const { perspectiva, seccionId, docenteId } = Route.useSearch()
  const navegar = useNavigate({ from: Route.fullPath })
  const horario = useHorarioVigente(ANIO_TRABAJO, perspectiva, seccionId, docenteId)

  if (horario.isPending) return <p className="muted">Cargando…</p>
  if (horario.isError) {
    const sinPublicar = horario.error instanceof ApiError && horario.error.codigo === HORARIO_NO_PUBLICADO
    return (
      <>
        <h1>Horario {ANIO_TRABAJO}</h1>
        <p className={sinPublicar ? 'alerta' : 'alerta alerta-error'} role={sinPublicar ? 'status' : 'alert'}>
          {sinPublicar ? 'El horario aún no se encuentra disponible. Cuando la administración lo publique, lo verás aquí.' : horario.error.message}
        </p>
      </>
    )
  }
  return <Consulta datos={horario.data} alElegir={(cambio) => void navegar({ search: (previa) => ({ ...previa, ...cambio }) })} />
}

const TEXTO_PERSPECTIVA: Record<Perspectiva, string> = { SECCION: 'Por sección', DOCENTE: 'Por docente' }

function Consulta({
  datos,
  alElegir,
}: {
  datos: HorarioVigente
  alElegir: (cambio: { perspectiva?: Perspectiva; seccionId?: string; docenteId?: string }) => void
}) {
  const [bloqueId, setBloqueId] = useState<string | null>(null)
  const porBloque = new Map<string, Asignacion>(datos.asignaciones.map((a) => [a.bloqueId, a]))
  const esSeccion = datos.perspectiva === 'SECCION'
  const seleccionado: BloqueHorario | null = datos.bloques.find((b) => b.id === bloqueId) ?? null
  const clase = seleccionado ? porBloque.get(seleccionado.id) : undefined
  const sinAlcance = esSeccion ? datos.secciones.length === 0 : datos.docentes.length === 0
  const titulo = esSeccion
    ? (datos.secciones.find((s) => s.id === datos.seccionId)?.nombre ?? '')
    : (datos.docentes.find((d) => d.id === datos.docenteId)?.nombre ?? '')

  function contenido(bloque: BloqueHorario) {
    const a = porBloque.get(bloque.id)
    if (!a) return <span className="muted">Libre</span>
    return esSeccion ? (
      <>
        <strong>{a.curso}</strong>
        <span>Prof. {a.docente}</span>
      </>
    ) : (
      <>
        <strong>{a.curso}</strong>
        <span>{a.seccion}</span>
      </>
    )
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>
            Mi horario {titulo && <>· {titulo}</>} · {datos.anio}
          </h1>
          <p className="muted">Inicio › Horario</p>
        </div>
        <span className="etiqueta etiqueta-si">
          Versión {datos.numeroVersion} publicada · vigente desde {fechaCorta(datos.vigenteDesde)}
        </span>
      </div>

      <div className="filtros">
        {datos.perspectivas.length > 1 && (
          <div className="pestanas" role="tablist" aria-label="Perspectiva">
            {datos.perspectivas.map((p) => (
              <button
                key={p}
                type="button"
                role="tab"
                aria-selected={datos.perspectiva === p}
                className={datos.perspectiva === p ? 'pestana pestana-activa' : 'pestana'}
                onClick={() => {
                  setBloqueId(null)
                  alElegir({ perspectiva: p, seccionId: undefined, docenteId: undefined })
                }}
              >
                {TEXTO_PERSPECTIVA[p]}
              </button>
            ))}
          </div>
        )}
        {esSeccion && datos.secciones.length > 1 && (
          <label className="campo">
            Sección
            <select
              value={datos.seccionId ?? ''}
              onChange={(e) => {
                setBloqueId(null)
                alElegir({ perspectiva: 'SECCION', seccionId: e.target.value })
              }}
            >
              {datos.secciones.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.nombre}
                </option>
              ))}
            </select>
          </label>
        )}
        {!esSeccion && datos.docentes.length > 1 && (
          <label className="campo">
            Docente
            <select
              value={datos.docenteId ?? ''}
              onChange={(e) => {
                setBloqueId(null)
                alElegir({ perspectiva: 'DOCENTE', docenteId: e.target.value })
              }}
            >
              {datos.docentes.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.nombre}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {sinAlcance ? (
        <p className="alerta" role="status">
          {esSeccion ? 'No tienes una sección con matrícula vigente para mostrar su horario.' : 'No hay docentes con clases en este horario.'}
        </p>
      ) : (
        <div className="elaborar">
          <MatrizHorario bloques={datos.bloques} contenido={contenido} seleccionadoId={bloqueId} onSeleccionar={(b) => setBloqueId(b.id)} />
          <aside className="lateral-elaborar">
            <section className="tarjeta" aria-label="Detalle del bloque">
              <h2>Detalle del bloque</h2>
              {!seleccionado && <p className="muted">Haz clic en cualquier bloque del horario para ver su detalle.</p>}
              {seleccionado && !clase && (
                <p className="muted">
                  {NOMBRE_DIA[seleccionado.diaSemana]} · {hhmm(seleccionado.horaInicio)} – {hhmm(seleccionado.horaFin)}: sin clase.
                </p>
              )}
              {seleccionado && clase && (
                <dl className="datos">
                  <dt>Curso</dt>
                  <dd>{clase.curso}</dd>
                  <dt>Sección</dt>
                  <dd>{clase.seccion}</dd>
                  <dt>Docente</dt>
                  <dd>{clase.docente}</dd>
                  <dt>Día</dt>
                  <dd>{NOMBRE_DIA[seleccionado.diaSemana]}</dd>
                  <dt>Bloque</dt>
                  <dd>
                    {hhmm(seleccionado.horaInicio)} – {hhmm(seleccionado.horaFin)}
                  </dd>
                </dl>
              )}
            </section>
          </aside>
        </div>
      )}
      <p className="muted">Solo consulta. Ves únicamente el horario que corresponde a tu alcance.</p>
    </>
  )
}
