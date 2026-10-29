import { createFileRoute, Link } from '@tanstack/react-router'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { fechaCorta, soles } from '../../../compartido/formato'
import { useMisHijos, type HijoMatricula } from '../../../modulos/matricula/api'
import { nombreObligacion, useObligaciones, type Obligacion, type ObligacionesAlumno } from '../../../modulos/pagos/api'

export const Route = createFileRoute('/intranet/pagos/')({
  beforeLoad: () => exigirPermiso('OBLIGACION_CONSULTAR_PROPIA'),
  validateSearch: (busqueda: Record<string, unknown>): { alumno?: string } => ({
    alumno: typeof busqueda.alumno === 'string' ? busqueda.alumno : undefined,
  }),
  component: MatriculaYPagos,
})

function MatriculaYPagos() {
  const { alumno } = Route.useSearch()
  const hijos = useMisHijos()

  if (hijos.isPending) return <p className="muted">Cargando…</p>
  if (hijos.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {hijos.error.message}
      </p>
    )
  }
  if (hijos.data.length === 0) {
    return (
      <>
        <h1>Matrícula y pagos</h1>
        <p className="muted vacio">No tienes estudiantes a tu cargo registrados.</p>
      </>
    )
  }
  const elegido = hijos.data.find((h) => h.alumnoId === alumno) ?? hijos.data[0]!

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Matrícula y pagos</h1>
          <p className="muted">Los pagos se realizan en el banco o por transferencia; aquí solo se presenta el comprobante para su validación.</p>
        </div>
      </div>

      <nav className="pestanas" aria-label="Estudiante">
        <span className="muted">Estudiante:</span>
        {hijos.data.map((h) => (
          <Link
            key={h.alumnoId}
            to="/intranet/pagos"
            search={{ alumno: h.alumnoId }}
            className={h.alumnoId === elegido.alumnoId ? 'btn btn-primario' : 'btn'}
            aria-current={h.alumnoId === elegido.alumnoId ? 'true' : undefined}
          >
            {h.alumno.split(' ')[0]}
            {h.matricula ? ` · ${h.matricula.seccion}` : ''}
          </Link>
        ))}
      </nav>

      <Estudiante hijo={elegido} />
    </>
  )
}

function Estudiante({ hijo }: { hijo: HijoMatricula }) {
  const obligaciones = useObligaciones(hijo.matricula ? hijo.alumnoId : undefined)
  const m = hijo.matricula

  return (
    <>
      <section className="tarjeta" aria-labelledby="titulo-matricula">
        <h2 id="titulo-matricula">Matrícula</h2>
        {m ? (
          <dl className="datos">
            <dt>Estado</dt>
            <dd>
              <span className="etiqueta etiqueta-si">{m.estado}</span>
            </dd>
            <dt>Estudiante</dt>
            <dd>{hijo.alumno}</dd>
            <dt>Año</dt>
            <dd>{m.anio}</dd>
            <dt>Grado</dt>
            <dd>
              {m.grado}, sección {m.seccion.split(' ')[1]}
            </dd>
          </dl>
        ) : (
          <p className="alerta" role="status">
            {hijo.alumno} no tiene una matrícula vigente. No se muestran obligaciones de un año escolar activo.
          </p>
        )}
      </section>

      {m && obligaciones.isPending && <p className="muted">Cargando obligaciones…</p>}
      {m && obligaciones.isError && (
        <p className="alerta alerta-error" role="alert">
          {obligaciones.error.message}
        </p>
      )}
      {m && obligaciones.data && <Obligaciones datos={obligaciones.data} anio={m.anio} />}
    </>
  )
}

function Obligaciones({ datos, anio }: { datos: ObligacionesAlumno; anio: number }) {
  const { resumen } = datos
  if (datos.obligaciones.length === 0) {
    return (
      <p className="alerta" role="status">
        Aún no existen obligaciones de pago para el año {anio}.
      </p>
    )
  }
  const proximo = resumen.proximoVencimiento

  return (
    <>
      <div className="rejilla">
        <section className="tarjeta" aria-label="Deuda vencida">
          <h3>Deuda vencida</h3>
          <p className={resumen.vencidas > 0 ? 'cifra cifra-alerta' : 'cifra'}>{soles(resumen.deudaVencida)}</p>
          <p className="muted">
            {resumen.vencidas === 0 ? 'Sin obligaciones vencidas' : `${resumen.vencidas} ${resumen.vencidas === 1 ? 'obligación vencida' : 'obligaciones vencidas'}`}
          </p>
        </section>
        <section className="tarjeta" aria-label="Próximo vencimiento">
          <h3>Próximo vencimiento</h3>
          {proximo ? (
            <>
              <p className="cifra">{soles(proximo.total)}</p>
              <p className="muted">
                {nombreObligacion(proximo.concepto, proximo.periodo)} · {fechaCorta(proximo.fechaVencimiento)}
              </p>
            </>
          ) : (
            <p className="muted">No tienes pagos por vencer.</p>
          )}
        </section>
      </div>

      <h2>
        Obligaciones {anio}{' '}
        <span className="muted">
          · {resumen.obligaciones} {resumen.obligaciones === 1 ? 'obligación' : 'obligaciones'} · {resumen.pagadas} {resumen.pagadas === 1 ? 'pagada' : 'pagadas'}
        </span>
      </h2>
      <div className="tabla-scroll">
        <table aria-label="Obligaciones">
          <thead>
            <tr>
              <th>Concepto</th>
              <th>Vence</th>
              <th>Importe</th>
              <th>Detalle</th>
              <th>Estado</th>
              <th>
                <span className="solo-lectores">Acciones</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {datos.obligaciones.map((o) => (
              <Fila key={o.id} o={o} alumnoId={datos.alumnoId} />
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

const ETIQUETA_ESTADO = { PENDIENTE: 'Pendiente', PAGADA: 'Pagada', VENCIDA: 'Vencida' } as const

function Fila({ o, alumnoId }: { o: Obligacion; alumnoId: string }) {
  const conPenalidad = Number(o.montoPenalidad) > 0
  return (
    <tr>
      <td>{nombreObligacion(o.concepto, o.periodo)}</td>
      <td className="nowrap">{fechaCorta(o.fechaVencimiento)}</td>
      <td className="nowrap">{soles(o.total)}</td>
      <td>
        <Detalle o={o} conPenalidad={conPenalidad} />
      </td>
      <td>
        <span className={o.estado === 'PAGADA' ? 'etiqueta etiqueta-si' : o.estado === 'VENCIDA' ? 'etiqueta etiqueta-no' : 'etiqueta'}>
          {ETIQUETA_ESTADO[o.estado]}
        </span>
      </td>
      <td>
        {o.puedePresentarComprobante && (
          <Link
            to="/intranet/pagos/comprobante"
            search={{ obligacion: o.id, alumno: alumnoId }}
            className="btn"
            aria-label={`Subir comprobante de ${nombreObligacion(o.concepto, o.periodo)}`}
          >
            Subir comprobante
          </Link>
        )}
      </td>
    </tr>
  )
}

function Detalle({ o, conPenalidad }: { o: Obligacion; conPenalidad: boolean }) {
  if (o.boleta) return <>Boleta {o.boleta.numero}</>
  if (o.comprobante?.estado === 'PENDIENTE') return <span className="muted">Comprobante en revisión</span>
  return (
    <>
      {conPenalidad && (
        <span>
          Importe base {soles(o.montoBase)} + penalidad {soles(o.montoPenalidad)} = {soles(o.total)}
        </span>
      )}
      {o.comprobante?.estado === 'OBSERVADO' && (
        <div className="alerta">Comprobante observado: {o.comprobante.observacion}</div>
      )}
      {o.comprobante?.estado === 'RECHAZADO' && (
        <div className="alerta alerta-error">Comprobante rechazado: {o.comprobante.observacion}</div>
      )}
      {!conPenalidad && !o.comprobante && <span className="muted">—</span>}
    </>
  )
}
