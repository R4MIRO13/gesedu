import { createFileRoute } from '@tanstack/react-router'
import { ApiError } from '../../compartido/api/errores'
import { exigirPermiso } from '../../compartido/auth/guardias'
import { diaCorto, diaLargo, hora12, horaDeInstante } from '../../compartido/formato'
import {
  NOMBRE_ESTADO,
  useAsistenciaHoy,
  useMarcarAsistencia,
  useMiHistorialAsistencia,
  type AsistenciaHoy,
  type EstadoAsistencia,
  type RegistroAsistenciaDocente,
} from '../../modulos/asistencia/api'
import { RelojServidor } from '../../modulos/asistencia/componentes/RelojServidor'

export const Route = createFileRoute('/intranet/mi-asistencia')({
  beforeLoad: () => exigirPermiso('ASISTENCIA_DOCENTE_MARCAR'),
  component: MiAsistencia,
})

const CLASE_ESTADO: Record<EstadoAsistencia, string> = {
  PUNTUAL: 'etiqueta etiqueta-si',
  TARDANZA: 'etiqueta etiqueta-alerta',
  FALTA: 'etiqueta etiqueta-no',
}

/** Cuántos días se muestran en «Últimos días» (el de hoy cuenta). */
const DIAS_VISIBLES = 5

function Estado({ estado }: { estado: EstadoAsistencia }) {
  return <span className={CLASE_ESTADO[estado]}>{NOMBRE_ESTADO[estado]}</span>
}

/** UC-07, Mi asistencia: el docente marca su llegada; el servidor pone la hora y decide si es puntual o tardanza. */
function MiAsistencia() {
  const hoy = useAsistenciaHoy()
  const historial = useMiHistorialAsistencia()
  const marcar = useMarcarAsistencia()

  if (hoy.isPending) return <p className="muted">Cargando…</p>
  if (hoy.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {hoy.error.message}
      </p>
    )
  }
  const datos = hoy.data
  const registrada = marcar.data
  const error = marcar.error instanceof ApiError ? marcar.error : null

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Mi asistencia</h1>
          <p className="muted">Inicio › Mi asistencia</p>
        </div>
      </div>

      <div className="dos-columnas">
        <section className="tarjeta" aria-label="Registrar asistencia">
          <p className="muted">{diaLargo(datos.fecha)}</p>
          <RelojServidor key={datos.horaServidor} horaServidor={datos.horaServidor} />
          {datos.primerBloque && (
            <p>
              Tu primera clase hoy: <strong>{hora12(datos.primerBloque)}</strong>
            </p>
          )}
          <p className="muted">
            Tolerancia para docentes: {datos.toleranciaMinutos} {datos.toleranciaMinutos === 1 ? 'minuto' : 'minutos'}
            {datos.limitePuntual && <> · eres puntual hasta las {hora12(datos.limitePuntual)}</>}
          </p>

          <Aviso datos={datos} />
          {registrada && (
            <p className={registrada.estado === 'PUNTUAL' ? 'alerta alerta-ok' : 'alerta'} role="status">
              Asistencia registrada a las {registrada.horaRegistro ? horaDeInstante(registrada.horaRegistro) : '—'}:{' '}
              {NOMBRE_ESTADO[registrada.estado]}.
            </p>
          )}
          {error && (
            <p className="alerta alerta-error" role="alert">
              {error.message}
            </p>
          )}

          <div className="acciones">
            <button
              type="button"
              className="btn btn-primario"
              disabled={!datos.puedeMarcar || marcar.isPending}
              onClick={() => marcar.mutate()}
            >
              Registrar asistencia
            </button>
          </div>
        </section>

        <section className="tarjeta" aria-label="Últimos días">
          <h2>Últimos días</h2>
          <Historial datos={datos} historial={historial.data} cargando={historial.isPending} />
        </section>
      </div>
    </>
  )
}

/** Explica por qué no se puede marcar (o qué quedó registrado hoy). */
function Aviso({ datos }: { datos: AsistenciaHoy }) {
  if (datos.registro) {
    return (
      <p className="alerta" role="status">
        Hoy quedó registrada tu asistencia
        {datos.registro.horaRegistro ? ` a las ${horaDeInstante(datos.registro.horaRegistro)}` : ''}:{' '}
        <Estado estado={datos.registro.estado} />
      </p>
    )
  }
  if (!datos.puedeMarcar && datos.mensaje) {
    return (
      <p className="alerta" role="status">
        {datos.mensaje}
      </p>
    )
  }
  return null
}

function Historial({ datos, historial, cargando }: { datos: AsistenciaHoy; historial: RegistroAsistenciaDocente[] | undefined; cargando: boolean }) {
  if (cargando) return <p className="muted">Cargando…</p>
  const anteriores = (historial ?? []).filter((r) => r.fecha !== datos.fecha)
  const deHoy = datos.registro
  // Hoy figura aunque aún no haya marcado, salvo que no sea un día lectivo
  const mostrarHoy = deHoy !== null || datos.puedeMarcar || datos.motivo === 'JORNADA_CERRADA'
  const filas: { fecha: string; hora: string; estado: EstadoAsistencia | null; esHoy: boolean }[] = []
  if (mostrarHoy) {
    filas.push({
      fecha: datos.fecha,
      hora: deHoy?.horaRegistro ? horaDeInstante(deHoy.horaRegistro) : '—',
      estado: deHoy?.estado ?? null,
      esHoy: true,
    })
  }
  for (const r of anteriores) {
    if (filas.length >= DIAS_VISIBLES) break
    filas.push({ fecha: r.fecha, hora: r.horaRegistro ? horaDeInstante(r.horaRegistro) : '—', estado: r.estado, esHoy: false })
  }
  if (filas.length === 0) return <p className="muted">Aún no tienes asistencias registradas.</p>
  return (
    <div className="tabla-scroll">
      <table aria-label="Últimos días de asistencia">
        <thead>
          <tr>
            <th scope="col">Fecha</th>
            <th scope="col">Hora de marcado</th>
            <th scope="col">Estado</th>
          </tr>
        </thead>
        <tbody>
          {filas.map((f) => (
            <tr key={f.fecha}>
              <td>
                {diaCorto(f.fecha)}
                {f.esHoy && <span className="muted"> (hoy)</span>}
              </td>
              <td>
                {f.hora}
                {f.estado === 'FALTA' && <div className="muted">Generado al cierre de jornada</div>}
              </td>
              <td>{f.estado ? <Estado estado={f.estado} /> : <span className="muted">Sin registrar</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
