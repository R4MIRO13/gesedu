import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { ApiError } from '../../../../compartido/api/errores'
import { exigirPermiso } from '../../../../compartido/auth/guardias'
import {
  useCompletitud,
  usePublicarHorario,
  useVersionesPublicadas,
  type Completitud,
  type Publicacion,
  type SeccionCompletitud,
} from '../../../../modulos/horarios/api'

export const Route = createFileRoute('/intranet/horarios/$anio/publicar')({
  beforeLoad: () => exigirPermiso('HORARIO_GESTIONAR'),
  component: PublicarHorario,
})

const formatoPublicacion = new Intl.DateTimeFormat('es-PE', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  timeZone: 'America/Lima',
})

function horas(n: number) {
  return `${n} h`
}

function estadoTexto(s: SeccionCompletitud): string {
  switch (s.estado) {
    case 'COMPLETO':
      return 'Completo'
    case 'FALTAN':
      return `Faltan ${horas(s.faltan)}`
    case 'EXCEDE':
      return `Excede ${horas(s.sobran)}`
    case 'FALTAN_Y_EXCEDE':
      return `Faltan ${horas(s.faltan)} y excede ${horas(s.sobran)}`
  }
}

/** UC-22, Publicar horario: valida la completitud sección por sección y publica una versión vigente. */
function PublicarHorario() {
  const { anio: anioTexto } = Route.useParams()
  const anio = Number(anioTexto)
  const completitud = useCompletitud(anio)
  const versiones = useVersionesPublicadas(anio)
  if (completitud.isPending) return <p className="muted">Cargando…</p>
  if (completitud.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {completitud.error.message}
      </p>
    )
  }
  return <Validacion anio={anio} datos={completitud.data} versiones={versiones.data ?? []} />
}

function Validacion({ anio, datos, versiones }: { anio: number; datos: Completitud; versiones: Publicacion[] }) {
  const publicar = usePublicarHorario(anio)
  const [confirmando, setConfirmando] = useState(false)
  const [publicada, setPublicada] = useState<Publicacion | null>(null)
  const observadas = datos.secciones.filter((s) => s.estado !== 'COMPLETO')
  const error = publicar.error instanceof ApiError ? publicar.error : null
  const proximaVersion = (datos.vigente?.numeroVersion ?? 0) + 1

  function confirmar() {
    setPublicada(null)
    publicar.mutate(datos.versionBorrador, {
      onSuccess: (p) => setPublicada(p),
      onSettled: () => setConfirmando(false),
    })
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Publicar horario {anio}</h1>
          <p className="muted">Inicio › Horarios › Publicar</p>
        </div>
        <div className="acciones">
          <span className="etiqueta etiqueta-si">Completas {datos.completas}</span>
          <span className={datos.conObservaciones > 0 ? 'etiqueta etiqueta-alerta' : 'etiqueta'}>Con observaciones {datos.conObservaciones}</span>
          <span className="etiqueta">{datos.vigente ? `Borrador · vigente: versión ${datos.vigente.numeroVersion}` : 'Borrador'}</span>
        </div>
      </div>

      {publicada && (
        <p className="alerta alerta-ok" role="status">
          Horario publicado: la versión {publicada.numeroVersion} es la vigente desde {formatoPublicacion.format(new Date(publicada.fechaHoraPublicacion))}{' '}
          ({publicada.clases} clases). Ya lo ven docentes, alumnos y apoderados.
        </p>
      )}
      {error && (
        <p className="alerta alerta-error" role="alert">
          {error.status === 409
            ? 'El borrador cambió desde que lo revisaste. La validación se volvió a calcular: revísala y publica de nuevo.'
            : error.message}
        </p>
      )}
      {observadas.length > 0 && (
        <p className="alerta alerta-error" role="alert">
          {observadas.length === 1 ? 'Hay 1 sección con observaciones' : `Hay ${observadas.length} secciones con observaciones`}. Corrígelas antes de
          publicar. ({observadas.map((s) => s.seccion).join(' y ')})
        </p>
      )}
      {datos.advertencias.map((a) => (
        <p key={a.tipo} className="alerta" role="status">
          {a.mensaje} No impide publicar.
        </p>
      ))}
      {datos.sinCambios && datos.vigente && (
        <p className="alerta" role="status">
          El borrador es igual a la versión {datos.vigente.numeroVersion} vigente: publicarlo crearía una versión idéntica.
        </p>
      )}

      <section className="tarjeta" aria-label="Validación de completitud">
        <h2>Validación de completitud</h2>
        <p className="muted">Compara los bloques asignados con la carga horaria semanal de cada sección, curso por curso.</p>
        <div className="tabla-scroll">
          <table aria-label="Completitud por sección">
            <thead>
              <tr>
                <th>Sección</th>
                <th>Horas asignadas</th>
                <th>Horas requeridas</th>
                <th>Estado</th>
                <th>Detalle</th>
              </tr>
            </thead>
            <tbody>
              {datos.secciones.map((s) => (
                <tr key={s.seccionId}>
                  <td>
                    <strong>{s.seccion}</strong>
                  </td>
                  <td>{horas(s.horasAsignadas)}</td>
                  <td>{horas(s.horasRequeridas)}</td>
                  <td>
                    <span className={s.estado === 'COMPLETO' ? 'etiqueta etiqueta-si' : 'etiqueta etiqueta-alerta'}>{estadoTexto(s)}</span>
                  </td>
                  <td>{s.detalle.map((d) => `${d.curso} ${d.asignadas}/${d.requeridas} h`).join(' · ') || <span className="muted">—</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="muted">Mientras haya observaciones, el horario se mantiene en Borrador y no lo ven docentes, alumnos ni apoderados.</p>

        {confirmando ? (
          <div className="alerta" role="alertdialog" aria-label="Confirmar publicación">
            <p>
              <strong>¿Publicar el horario {anio}?</strong> La versión {proximaVersion} pasará a ser la vigente
              {datos.vigente ? ` y la versión ${datos.vigente.numeroVersion} dejará de serlo` : ''}.
            </p>
            <div className="acciones">
              <button type="button" className="btn btn-primario" disabled={publicar.isPending} onClick={confirmar}>
                Confirmar publicación
              </button>
              <button type="button" className="btn" onClick={() => setConfirmando(false)}>
                Cancelar
              </button>
            </div>
          </div>
        ) : (
          <div className="acciones">
            <Link className="btn" to="/intranet/horarios/$anio/elaborar" params={{ anio: String(anio) }} search={{ perspectiva: 'SECCION' }}>
              Volver a elaborar horario
            </Link>
            <button
              type="button"
              className="btn btn-primario"
              disabled={!datos.publicable || publicar.isPending}
              onClick={() => {
                setPublicada(null)
                setConfirmando(true)
              }}
            >
              Publicar horario
            </button>
          </div>
        )}
      </section>

      {versiones.length > 0 && (
        <section className="tarjeta" aria-label="Versiones publicadas">
          <h2>Versiones publicadas</h2>
          <div className="tabla-scroll">
            <table aria-label="Versiones publicadas del horario">
              <thead>
                <tr>
                  <th>Versión</th>
                  <th>Publicada</th>
                  <th>Por</th>
                  <th>Clases</th>
                  <th>Estado</th>
                </tr>
              </thead>
              <tbody>
                {versiones.map((v) => (
                  <tr key={v.id}>
                    <td>{v.numeroVersion}</td>
                    <td>{formatoPublicacion.format(new Date(v.fechaHoraPublicacion))}</td>
                    <td>{v.publicadoPor ?? '—'}</td>
                    <td>{v.clases}</td>
                    <td>
                      <span className={v.vigente ? 'etiqueta etiqueta-si' : 'etiqueta'}>{v.vigente ? 'Vigente' : 'Anterior'}</span>
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
