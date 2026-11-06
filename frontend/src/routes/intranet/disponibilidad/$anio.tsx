import { createFileRoute } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { ApiError } from '../../../compartido/api/errores'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { fechaCorta } from '../../../compartido/formato'
import {
  useConfirmarDisponibilidad,
  useDisponibilidad,
  useValidacionDisponibilidad,
  type Disponibilidad,
} from '../../../modulos/horarios/api'
import { MatrizDisponibilidad } from '../../../modulos/horarios/componentes/MatrizDisponibilidad'
import { resolverAnioActivo } from '../../../modulos/academico/anioActivo'

export const Route = createFileRoute('/intranet/disponibilidad/$anio')({
  beforeLoad: async ({ params, location }: { params: { anio: string }; location: { href: string } }) => {
    exigirPermiso('DISPONIBILIDAD_REGISTRAR')
    await resolverAnioActivo(params, location.href)
  },
  component: MiDisponibilidad,
})

const formatoConfirmacion = new Intl.DateTimeFormat('es-PE', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  timeZone: 'America/Lima',
})

/** UC-20, Mi disponibilidad horaria: el docente marca los bloques en que puede dictar mientras el periodo está abierto. */
function MiDisponibilidad() {
  const { anio: anioTexto } = Route.useParams()
  const anio = Number(anioTexto)
  const disponibilidad = useDisponibilidad(anio)
  if (disponibilidad.isPending) return <p className="muted">Cargando…</p>
  if (disponibilidad.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {disponibilidad.error.message}
      </p>
    )
  }
  return <Registro anio={anio} datos={disponibilidad.data} />
}

function Registro({ anio, datos }: { anio: number; datos: Disponibilidad }) {
  const confirmar = useConfirmarDisponibilidad(anio)
  const [seleccion, setSeleccion] = useState<ReadonlySet<string>>(() => new Set(datos.bloqueIds))
  const [observaciones, setObservaciones] = useState(datos.observaciones ?? '')
  const [confirmado, setConfirmado] = useState<number | null>(null)
  const ids = useMemo(() => [...seleccion].sort(), [seleccion])
  const abierto = datos.periodo.abierto && datos.vinculado
  const validacion = useValidacionDisponibilidad(anio, ids, abierto)
  const advertencia = (abierto ? validacion.data : datos.validacion)?.mensaje ?? null
  const marcados = abierto ? (validacion.data?.bloquesMarcados ?? ids.length) : datos.bloqueIds.length

  function alternar(bloqueId: string) {
    setConfirmado(null)
    setSeleccion((previa) => {
      const siguiente = new Set(previa)
      if (!siguiente.delete(bloqueId)) siguiente.add(bloqueId)
      return siguiente
    })
  }

  function enviar() {
    setConfirmado(null)
    confirmar.mutate(
      { bloqueIds: ids, observaciones: observaciones.trim() },
      {
        // Lo que quedó registrado es lo que se muestra
        onSuccess: (d) => {
          setConfirmado(d.bloqueIds.length)
          setSeleccion(new Set(d.bloqueIds))
          setObservaciones(d.observaciones ?? '')
        },
      },
    )
  }

  const error = confirmar.error instanceof ApiError ? confirmar.error.message : confirmar.error ? 'No se pudo confirmar. Inténtalo de nuevo.' : null

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Mi disponibilidad horaria · {anio}</h1>
          <p className="muted">Inicio › Disponibilidad</p>
        </div>
      </div>

      {!datos.vinculado ? (
        <p className="alerta alerta-error" role="alert">
          No estás habilitado como docente para el año {anio}. Consulta con la administración.
        </p>
      ) : datos.periodo.abierto ? (
        <p className="alerta" role="status">
          Periodo de registro abierto hasta {fechaCorta(datos.periodo.fechaHoraFin ?? '')}. Marca los bloques en los que puedes dictar
          clases. Puedes cambiarlos hasta esa fecha.
        </p>
      ) : (
        <p className="alerta alerta-error" role="status">
          El periodo de registro está cerrado: ya no se permiten modificaciones. Esta es tu última disponibilidad confirmada.
        </p>
      )}
      {confirmado !== null && (
        <p className="alerta alerta-ok" role="status">
          Se confirmó tu disponibilidad: {confirmado} {confirmado === 1 ? 'bloque disponible' : 'bloques disponibles'}.
        </p>
      )}
      {error && (
        <p className="alerta alerta-error" role="alert">
          {error}
        </p>
      )}

      {datos.vinculado && (
        <div className="dos-columnas">
          <section className="tarjeta" aria-label="Matriz de disponibilidad">
            <MatrizDisponibilidad bloques={datos.bloques} seleccionados={seleccion} soloLectura={!abierto} alAlternar={alternar} />
            <p className="muted">
              {abierto
                ? 'Haz clic en una celda para cambiar su estado.'
                : 'Vista de solo lectura: las celdas marcadas son los bloques que confirmaste como disponibles.'}
            </p>
          </section>

          <aside className="tarjeta" aria-label="Resumen">
            <p>
              <strong>{datos.docente}</strong>
            </p>
            <dl className="datos">
              <dt>Carga horaria asignada</dt>
              <dd>{datos.cargaSemanalReferencia} horas semanales</dd>
              <dt>Bloques disponibles marcados</dt>
              <dd>{marcados}</dd>
              <dt>Cursos</dt>
              <dd>{datos.cursos.map((c) => c.nombre).join(', ') || '—'}</dd>
            </dl>
            {advertencia && (
              <p className="alerta alerta-error" role="alert">
                {advertencia}
              </p>
            )}
            <label className="campo">
              Observaciones
              <textarea
                id="observaciones-disponibilidad"
                name="observaciones"
                rows={3}
                maxLength={500}
                value={observaciones}
                disabled={!abierto}
                onChange={(e) => {
                  setConfirmado(null)
                  setObservaciones(e.target.value)
                }}
              />
            </label>
            {abierto && (
              <button type="button" className="btn btn-primario" disabled={confirmar.isPending} onClick={enviar}>
                Confirmar disponibilidad
              </button>
            )}
            <p className="muted">
              {datos.ultimaConfirmacion
                ? `Última versión confirmada: ${formatoConfirmacion.format(new Date(datos.ultimaConfirmacion))}`
                : 'Aún no confirmas tu disponibilidad.'}
            </p>
          </aside>
        </div>
      )}
    </>
  )
}
