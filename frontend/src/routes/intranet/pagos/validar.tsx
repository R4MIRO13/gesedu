import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { ApiError } from '../../../compartido/api/errores'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { fechaCorta, soles } from '../../../compartido/formato'
import {
  ESTADOS_REVISION,
  nombreObligacion,
  useBandejaComprobantes,
  useDetalleComprobante,
  useRevisarComprobante,
  useValidarComprobante,
  type DetalleComprobante,
  type EstadoRevision,
  type ResultadoRevision,
  type ResultadoValidacion,
} from '../../../modulos/pagos/api'
import { esquemaRevision, type DatosRevision } from '../../../modulos/pagos/esquemas'

export const Route = createFileRoute('/intranet/pagos/validar')({
  beforeLoad: () => exigirPermiso('COMPROBANTE_VALIDAR'),
  validateSearch: (busqueda: Record<string, unknown>): { comprobante?: string } => ({
    comprobante: typeof busqueda.comprobante === 'string' ? busqueda.comprobante : undefined,
  }),
  component: ValidarComprobantes,
})

const formatoFechaHora = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Lima' })
const ETIQUETA_REVISION: Record<EstadoRevision, string> = {
  PENDIENTE: 'En revisión',
  APROBADO: 'Aprobado',
  OBSERVADO: 'Observado',
  RECHAZADO: 'Rechazado',
}

function ValidarComprobantes() {
  const { comprobante } = Route.useSearch()
  const [estado, setEstado] = useState<EstadoRevision>('PENDIENTE')
  const [pagina, setPagina] = useState(0)
  const bandeja = useBandejaComprobantes(estado, pagina)
  const datos = bandeja.data

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Validar comprobantes</h1>
          <p className="muted">Comprobantes de pago presentados por los apoderados.</p>
        </div>
        <label className="campo">
          Estado
          <select
            value={estado}
            onChange={(e) => {
              setEstado(e.target.value as EstadoRevision)
              setPagina(0)
            }}
          >
            {ESTADOS_REVISION.map((e) => (
              <option key={e.valor} value={e.valor}>
                {e.texto}
              </option>
            ))}
          </select>
        </label>
      </div>

      {bandeja.isPending && <p className="muted">Cargando…</p>}
      {bandeja.isError && (
        <p className="alerta alerta-error" role="alert">
          {bandeja.error.message}
        </p>
      )}

      <div className="dos-columnas">
        {datos && (
          <section aria-label="Comprobantes">
            <p className="muted">{datos.totalElementos} {datos.totalElementos === 1 ? 'comprobante' : 'comprobantes'}</p>
            <div className="tabla-scroll">
              <table aria-label="Comprobantes">
                <thead>
                  <tr>
                    <th>Enviado</th>
                    <th>Estudiante</th>
                    <th>Concepto</th>
                    <th>Monto</th>
                    <th>N.° op.</th>
                  </tr>
                </thead>
                <tbody>
                  {datos.contenido.map((c) => (
                    <tr key={c.id} className={c.id === comprobante ? 'fila-activa' : undefined}>
                      <td className="nowrap">{formatoFechaHora.format(new Date(c.enviado))}</td>
                      <td>
                        <Link to="/intranet/pagos/validar" search={{ comprobante: c.id }} aria-label={`Revisar el comprobante de ${c.estudiante}`}>
                          {c.estudiante}
                        </Link>
                      </td>
                      <td>{nombreObligacion(c.concepto, c.periodo)}</td>
                      <td className="nowrap">{soles(c.montoPresentado)}</td>
                      <td>{c.numeroOperacion}</td>
                    </tr>
                  ))}
                  {datos.contenido.length === 0 && (
                    <tr>
                      <td colSpan={5} className="muted vacio">
                        No hay comprobantes {ESTADOS_REVISION.find((e) => e.valor === estado)?.texto.toLowerCase()}.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <div className="paginacion">
              <button className="btn" disabled={datos.pagina === 0} onClick={() => setPagina((p) => p - 1)}>
                ← Anterior
              </button>
              <span className="muted">
                Página {datos.pagina + 1} de {Math.max(datos.totalPaginas, 1)}
              </span>
              <button className="btn" disabled={datos.pagina + 1 >= datos.totalPaginas} onClick={() => setPagina((p) => p + 1)}>
                Siguiente →
              </button>
            </div>
          </section>
        )}

        {comprobante ? <Revision id={comprobante} /> : <p className="muted">Elige un comprobante de la lista para revisarlo.</p>}
      </div>
    </>
  )
}

function Revision({ id }: { id: string }) {
  const detalle = useDetalleComprobante(id)
  if (detalle.isPending) return <p className="muted">Cargando comprobante…</p>
  if (detalle.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {detalle.error.message}
      </p>
    )
  }
  return <Detalle key={detalle.data.id} d={detalle.data} />
}

function Detalle({ d }: { d: DetalleComprobante }) {
  const validar = useValidarComprobante()
  const [boleta, setBoleta] = useState<ResultadoValidacion | null>(null)
  const [revisado, setRevisado] = useState<ResultadoRevision | null>(null)
  const [formulario, setFormulario] = useState<'observacion' | 'rechazo' | null>(null)
  const { obligacion: o, declarado, verificaciones: v } = d
  const conPenalidad = Number(o.montoPenalidad) > 0

  if (boleta) {
    return (
      <section className="tarjeta" aria-label="Resultado de la validación">
        <p className="alerta alerta-ok" role="status">
          Pago registrado. Boleta {boleta.numeroBoleta} por {soles(boleta.montoTotal)}. Se informó al apoderado.
        </p>
      </section>
    )
  }
  if (revisado) {
    return (
      <section className="tarjeta" aria-label="Resultado de la revisión">
        <p className="alerta alerta-ok" role="status">
          {revisado.estado === 'OBSERVADO'
            ? `Comprobante observado. Se informó al apoderado el importe total pendiente: ${soles(revisado.totalPendiente)}.`
            : 'Comprobante rechazado. Se informó al apoderado que debe presentar la información correcta.'}
        </p>
      </section>
    )
  }

  return (
    <section className="tarjeta" aria-labelledby="titulo-revision">
      <div className="encabezado">
        <h2 id="titulo-revision">Comprobante de {o.alumno}</h2>
        <span className="etiqueta">{ETIQUETA_REVISION[d.estado]}</span>
      </div>

      <dl className="datos">
        <dt>Obligación</dt>
        <dd>
          {nombreObligacion(o.concepto, o.periodo)} {o.periodo.slice(0, 4)}
        </dd>
        <dt>Vence</dt>
        <dd>{fechaCorta(o.fechaVencimiento)}</dd>
        <dt>Estado</dt>
        <dd>
          <span className={o.estado === 'VENCIDA' ? 'etiqueta etiqueta-no' : 'etiqueta'}>
            {o.estado === 'PAGADA' ? 'Pagada' : o.estado === 'VENCIDA' ? 'Vencida' : 'Pendiente'}
          </span>
        </dd>
        <dt>Importe base</dt>
        <dd>{soles(o.montoBase)}</dd>
        {conPenalidad && (
          <>
            <dt>Penalidad por mora</dt>
            <dd>{soles(o.montoPenalidad)}</dd>
          </>
        )}
        <dt>Total a pagar</dt>
        <dd>
          <strong>{soles(o.total)}</strong>
        </dd>
      </dl>

      <h3>Declarado por el apoderado</h3>
      <dl className="datos">
        <dt>Apoderado</dt>
        <dd>{declarado.apoderado}</dd>
        <dt>N.° operación</dt>
        <dd>{declarado.numeroOperacion}</dd>
        <dt>Fecha de pago</dt>
        <dd>{fechaCorta(declarado.fechaPago)}</dd>
        <dt>Medio</dt>
        <dd>{declarado.medioPago}</dd>
        <dt>Monto</dt>
        <dd>{soles(declarado.montoPresentado)}</dd>
      </dl>
      <p>
        <a href={d.voucher.url} target="_blank" rel="noreferrer">
          Ampliar voucher
        </a>
      </p>

      <h3>Verificaciones</h3>
      <ul className="validaciones">
        <li>
          <Cumple si={v.numeroOperacionNuevo} />{' '}
          {v.numeroOperacionNuevo ? 'N.° de operación no registrado antes' : 'El N.° de operación ya figura en otro comprobante'}
        </li>
        <li>
          <Cumple si={v.montoCoincide} />{' '}
          {v.montoCoincide
            ? 'El monto cubre el importe base y la penalidad'
            : v.montoCubreTotal
              ? `El monto pagado (${soles(declarado.montoPresentado)}) excede el total a pagar (${soles(o.total)})`
              : `El monto pagado (${soles(declarado.montoPresentado)}) no cubre el total a pagar (${soles(o.total)})`}
        </li>
        {!v.obligacionPorPagar && (
          <li>
            <Cumple si={false} /> La obligación ya está pagada
          </li>
        )}
      </ul>
      <p className="muted">Comprueba además que el voucher corresponda al estudiante y al concepto.</p>

      {d.observacion && <p className="alerta">Observación registrada: {d.observacion}</p>}
      {validar.error && (
        <p className="alerta alerta-error" role="alert">
          {validar.error instanceof ApiError ? validar.error.message : 'No se pudo validar el pago. Inténtalo de nuevo.'}
        </p>
      )}

      {d.puedeRevisar ? (
        formulario ? (
          <FormularioRevision id={d.id} accion={formulario} totalPendiente={o.total} alTerminar={setRevisado} alCancelar={() => setFormulario(null)} />
        ) : (
          <div className="acciones">
            <button type="button" className="btn" onClick={() => setFormulario('rechazo')}>
              Rechazar
            </button>
            <button type="button" className="btn" onClick={() => setFormulario('observacion')}>
              Observar
            </button>
            <button
              type="button"
              className="btn btn-primario"
              disabled={validar.isPending || !v.montoCoincide}
              onClick={() => validar.mutate(d.id, { onSuccess: setBoleta })}
            >
              {validar.isPending ? 'Registrando…' : 'Validar y registrar boleta'}
            </button>
          </div>
        )
      ) : (
        <p className="muted">Este comprobante ya no se puede revisar.</p>
      )}
    </section>
  )
}

function FormularioRevision({
  id,
  accion,
  totalPendiente,
  alTerminar,
  alCancelar,
}: {
  id: string
  accion: 'observacion' | 'rechazo'
  totalPendiente: string
  alTerminar: (r: ResultadoRevision) => void
  alCancelar: () => void
}) {
  const revisar = useRevisarComprobante(accion)
  const form = useForm<DatosRevision>({ resolver: zodResolver(esquemaRevision), defaultValues: { observacion: '' } })
  const etiqueta = accion === 'observacion' ? 'Observación para el apoderado' : 'Motivo del rechazo'
  return (
    <form
      className="formulario"
      aria-label={accion === 'observacion' ? 'Observar comprobante' : 'Rechazar comprobante'}
      noValidate
      onSubmit={form.handleSubmit((d) => revisar.mutate({ id, observacion: d.observacion }, { onSuccess: alTerminar }))}
    >
      <label className="campo">
        {etiqueta}
        <textarea rows={3} {...form.register('observacion')} aria-invalid={!!form.formState.errors.observacion} />
        {form.formState.errors.observacion && <span className="error-campo">{form.formState.errors.observacion.message}</span>}
      </label>
      {accion === 'observacion' && <p className="muted">Al apoderado se le informará el importe total pendiente: {soles(totalPendiente)}.</p>}
      {revisar.error && (
        <p className="alerta alerta-error" role="alert">
          {revisar.error instanceof ApiError ? revisar.error.message : 'No se pudo completar la acción.'}
        </p>
      )}
      <div className="acciones">
        <button type="button" className="btn" onClick={alCancelar}>
          Cancelar
        </button>
        <button type="submit" className="btn btn-primario" disabled={revisar.isPending}>
          {accion === 'observacion' ? 'Observar comprobante' : 'Rechazar comprobante'}
        </button>
      </div>
    </form>
  )
}

function Cumple({ si }: { si: boolean }) {
  return <span className={si ? 'etiqueta etiqueta-si' : 'etiqueta etiqueta-no'}>{si ? 'Cumple' : 'No cumple'}</span>
}
