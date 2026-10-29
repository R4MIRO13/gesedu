import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { exigirPermiso } from '../../../../compartido/auth/guardias'
import { fechaCorta, soles } from '../../../../compartido/formato'
import { nombreObligacion, useHistorialFinanciero, type HistorialFinanciero, type Obligacion } from '../../../../modulos/pagos/api'

export const Route = createFileRoute('/intranet/pagos/historial/$codigo')({
  beforeLoad: () => exigirPermiso('PAGO_HISTORIAL_CONSULTAR'),
  component: Historial,
})

type Pestana = 'obligaciones' | 'pagos' | 'deudas'

const ESTADO = { PENDIENTE: 'Pendiente', PAGADA: 'Pagada', VENCIDA: 'Vencida' } as const
const formatoFechaHora = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Lima' })

function Historial() {
  const { codigo } = Route.useParams()
  const historial = useHistorialFinanciero(codigo)

  if (historial.isPending) return <p className="muted">Cargando…</p>
  if (historial.isError) {
    return (
      <>
        <p className="alerta alerta-error" role="alert">
          {historial.error.message}
        </p>
        <Link to="/intranet/pagos/historial">← Buscar otro estudiante</Link>
      </>
    )
  }
  return <Contenido h={historial.data} />
}

function Contenido({ h }: { h: HistorialFinanciero }) {
  const [pestana, setPestana] = useState<Pestana>('obligaciones')
  const { estudiante: e, matricula: m, resumen: r } = h

  return (
    <>
      <p>
        <Link to="/intranet/pagos/historial">← Buscar otro estudiante</Link>
      </p>
      <div className="encabezado">
        <div>
          <h1>Historial financiero del estudiante</h1>
          <p className="muted">Solo consulta. Esta consulta no modifica obligaciones, pagos ni boletas.</p>
        </div>
      </div>

      <section className="tarjeta" aria-labelledby="titulo-estudiante">
        <h2 id="titulo-estudiante">{e.nombre}</h2>
        <dl className="datos">
          <dt>Código</dt>
          <dd>
            {e.codigo} · DNI {e.documento}
          </dd>
          <dt>Grado y sección</dt>
          <dd>{m ? `${m.seccion} · ${m.anio}` : 'Sin matrícula confirmada'}</dd>
          <dt>Apoderado</dt>
          <dd>{e.apoderado ?? '—'}</dd>
          <dt>Matrícula</dt>
          <dd>{m ? <span className="etiqueta etiqueta-si">{m.estado}</span> : <span className="etiqueta">Sin matrícula</span>}</dd>
        </dl>
      </section>

      {h.sinHistorial ? (
        <p className="alerta" role="status">
          No existe información financiera disponible para este estudiante: todavía no tiene obligaciones ni pagos registrados.
        </p>
      ) : (
        <>
          <div className="rejilla">
            <section className="tarjeta" aria-label="Total pagado">
              <h3>Total pagado</h3>
              <p className="cifra">{soles(r.totalPagado)}</p>
              <p className="muted">{r.pagadas} obligaciones pagadas</p>
            </section>
            <section className="tarjeta" aria-label="Pendiente">
              <h3>Pendiente</h3>
              <p className="cifra">{soles(r.totalPendiente)}</p>
              <p className="muted">{r.pendientes} obligaciones por vencer</p>
            </section>
            <section className="tarjeta" aria-label="Vencido con penalidades">
              <h3>Vencido con penalidades</h3>
              <p className={r.vencidas > 0 ? 'cifra cifra-alerta' : 'cifra'}>{soles(r.deudaVencida)}</p>
              <p className="muted">
                {r.vencidas} {r.vencidas === 1 ? 'obligación' : 'obligaciones'} · incluye {soles(r.penalidadesAplicadas)} de penalidad
              </p>
            </section>
          </div>

          {h.sinPagos && (
            <p className="alerta" role="status">
              Todavía no se registraron pagos: se muestran las obligaciones pendientes y vencidas.
            </p>
          )}

          <div className="pestanas" role="tablist" aria-label="Historial">
            {(
              [
                ['obligaciones', 'Obligaciones'],
                ['pagos', 'Boletas y pagos'],
                ['deudas', 'Deudas y penalidades'],
              ] as const
            ).map(([valor, texto]) => (
              <button
                key={valor}
                type="button"
                role="tab"
                aria-selected={pestana === valor}
                className={pestana === valor ? 'pestana pestana-activa' : 'pestana'}
                onClick={() => setPestana(valor)}
              >
                {texto}
              </button>
            ))}
          </div>

          {pestana === 'obligaciones' && <TablaObligaciones filas={h.obligaciones} />}
          {pestana === 'pagos' && <TablaPagos h={h} />}
          {pestana === 'deudas' && <TablaDeudas h={h} />}
        </>
      )}
    </>
  )
}

function TablaObligaciones({ filas }: { filas: Obligacion[] }) {
  return (
    <div className="tabla-scroll" role="tabpanel">
      <table aria-label="Obligaciones">
        <thead>
          <tr>
            <th>Concepto</th>
            <th>Periodo</th>
            <th>Importe</th>
            <th>Vencimiento</th>
            <th>Estado</th>
          </tr>
        </thead>
        <tbody>
          {filas.map((o) => (
            <tr key={o.id}>
              <td>{nombreObligacion(o.concepto, o.periodo).split(' ')[0]}</td>
              <td>{o.periodo}</td>
              <td className="nowrap">
                {Number(o.montoPenalidad) > 0 ? (
                  <>
                    {soles(o.montoBase)} + penalidad {soles(o.montoPenalidad)}
                  </>
                ) : (
                  soles(o.total)
                )}
              </td>
              <td className="nowrap">{fechaCorta(o.fechaVencimiento)}</td>
              <td>
                <span className={o.estado === 'PAGADA' ? 'etiqueta etiqueta-si' : o.estado === 'VENCIDA' ? 'etiqueta etiqueta-no' : 'etiqueta'}>
                  {ESTADO[o.estado]}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function TablaPagos({ h }: { h: HistorialFinanciero }) {
  if (h.pagos.length === 0) {
    return (
      <p className="muted vacio" role="tabpanel">
        Aún no hay boletas ni pagos registrados.
      </p>
    )
  }
  return (
    <div className="tabla-scroll" role="tabpanel">
      <table aria-label="Boletas y pagos">
        <thead>
          <tr>
            <th>Boleta</th>
            <th>Registrada</th>
            <th>Concepto</th>
            <th>Importe</th>
            <th>Fecha de pago</th>
            <th>N.° de operación</th>
          </tr>
        </thead>
        <tbody>
          {h.pagos.map((p) => (
            <tr key={p.numeroBoleta}>
              <td>{p.numeroBoleta}</td>
              <td className="nowrap">{formatoFechaHora.format(new Date(p.fechaRegistro))}</td>
              <td>{nombreObligacion(p.concepto, p.periodo)}</td>
              <td className="nowrap">{soles(p.monto)}</td>
              <td className="nowrap">{p.fechaPago ? fechaCorta(p.fechaPago) : '—'}</td>
              <td>{p.numeroOperacion ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function TablaDeudas({ h }: { h: HistorialFinanciero }) {
  if (h.deudas.length === 0) {
    return (
      <p className="muted vacio" role="tabpanel">
        No tiene obligaciones vencidas ni penalidades.
      </p>
    )
  }
  return (
    <div className="tabla-scroll" role="tabpanel">
      <table aria-label="Deudas y penalidades">
        <thead>
          <tr>
            <th>Concepto</th>
            <th>Venció</th>
            <th>Importe base</th>
            <th>Penalidad</th>
            <th>Total</th>
          </tr>
        </thead>
        <tbody>
          {h.deudas.map((d) => (
            <tr key={d.obligacionId}>
              <td>{nombreObligacion(d.concepto, d.periodo)}</td>
              <td className="nowrap">{fechaCorta(d.fechaVencimiento)}</td>
              <td className="nowrap">{soles(d.montoBase)}</td>
              <td className="nowrap">
                {soles(d.montoPenalidad)}
                {d.penalidadAplicadaEn && <span className="muted"> (aplicada el {fechaCorta(d.penalidadAplicadaEn)})</span>}
              </td>
              <td className="nowrap">{soles(d.total)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
