import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { usePermiso } from '../../../compartido/auth/usePermiso'
import {
  MOTIVOS,
  urlDocumento,
  useConfirmarMatricula,
  useDetalleSolicitud,
  useRechazarSolicitud,
  useRegistrarEvaluacion,
  type DetalleSolicitud,
  type MotivoRechazo,
  type ResultadoConfirmacion,
  type ResultadoRechazo,
} from '../../../modulos/matricula/api'
import {
  esquemaConfirmacion,
  esquemaEvaluacion,
  esquemaRechazo,
  type DatosConfirmacion,
  type DatosEvaluacion,
  type DatosRechazo,
} from '../../../modulos/matricula/esquemas'
import { esConflictoDeVersion, mensajeDeMatricula } from '../../../modulos/matricula/mensajes'

export const Route = createFileRoute('/intranet/matriculas/$codigo')({
  beforeLoad: () => exigirPermiso('MATRICULA_EVALUAR'),
  component: Evaluacion,
})

const TIPOS = { NUEVO_INGRESO: 'Nuevo ingreso', RECURRENTE: 'Recurrente' } as const
const ADMISION = { ADMITIDO: 'Admitido', NO_ADMITIDO: 'No admitido', NO_APLICA: 'Sin resultado' } as const
const CONCEPTOS: Record<string, string> = { MATRICULA: 'Matrícula', PENSION: 'Pensión', CUOTA_INGRESO: 'Cuota de ingreso' }

function Evaluacion() {
  const { codigo } = Route.useParams()
  const detalle = useDetalleSolicitud(codigo)
  const [confirmada, setConfirmada] = useState<ResultadoConfirmacion | null>(null)
  const [rechazada, setRechazada] = useState<ResultadoRechazo | null>(null)

  if (detalle.isPending) return <p className="muted">Cargando…</p>
  if (detalle.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {mensajeDeMatricula(detalle.error)}
      </p>
    )
  }
  const s = detalle.data

  return (
    <>
      <p>
        <Link to="/intranet/matriculas">← Volver a la bandeja</Link>
      </p>
      <div className="encabezado">
        <div>
          <h1>
            Solicitud <code>{s.codigo}</code>
          </h1>
          <p className="muted">
            {s.alumno.nombre} · {s.grado} · año {s.anioEscolar}
          </p>
        </div>
        <span className="etiqueta">{s.estadoVisible}</span>
      </div>

      {confirmada && (
        <p className="alerta alerta-ok" role="status">
          Matrícula confirmada: {confirmada.seccion} pasa a {confirmada.vacantesRestantes}{' '}
          {confirmada.vacantesRestantes === 1 ? 'vacante' : 'vacantes'} y se generan {confirmada.compromisosGenerados}{' '}
          compromisos de pago por S/ {confirmada.total}. Se avisó al apoderado por correo.
        </p>
      )}
      {rechazada && (
        <p className="alerta alerta-ok" role="status">
          Solicitud rechazada: {rechazada.estadoVisible}. Se avisó al apoderado por correo.
        </p>
      )}
      {s.estado === 'RECHAZADA' && s.observacion && <p className="alerta">Observación: {s.observacion}</p>}

      <div className="rejilla">
        <DatosGenerales s={s} />
        <Documentos s={s} />
      </div>
      <Validaciones s={s} />
      {s.estado === 'PENDIENTE_REVISION' && (
        <Acciones s={s} alConfirmar={setConfirmada} alRechazar={setRechazada} />
      )}
    </>
  )
}

function DatosGenerales({ s }: { s: DetalleSolicitud }) {
  const puedeRegistrar = usePermiso('MATRICULA_REGISTRAR') && s.estado === 'PENDIENTE_REVISION'
  const registrar = useRegistrarEvaluacion(s.codigo)
  const { register, handleSubmit } = useForm<DatosEvaluacion>({
    resolver: zodResolver(esquemaEvaluacion),
    values: { tipoEstudiante: s.tipoEstudiante, estadoAdmision: s.estadoAdmision },
  })

  return (
    <section className="tarjeta" aria-labelledby="titulo-datos">
      <h2 id="titulo-datos">Datos de la solicitud</h2>
      <dl>
        <dt>Alumno</dt>
        <dd>
          {s.alumno.nombre} · DNI {s.alumno.documento} · código {s.alumno.codigo}
        </dd>
        <dt>Apoderado</dt>
        <dd>
          {s.apoderado.nombre} ({s.apoderado.parentesco.toLowerCase()}) · {s.apoderado.correo ?? 'sin correo'}
          {s.apoderado.telefono ? ` · ${s.apoderado.telefono}` : ''}
        </dd>
        <dt>Fecha de solicitud</dt>
        <dd>{s.fechaSolicitud}</dd>
        <dt>Documentación</dt>
        <dd>{s.estadoDocumentacion === 'COMPLETA' ? 'Completa' : 'Incompleta'}</dd>
      </dl>
      {puedeRegistrar ? (
        <form
          className="fila"
          onSubmit={handleSubmit((datos) => registrar.mutate({ ...datos, version: s.version }))}
          aria-label="Tipo de estudiante y admisión"
        >
          <label className="campo">
            Tipo de estudiante
            <select {...register('tipoEstudiante')}>
              {Object.entries(TIPOS).map(([valor, texto]) => (
                <option key={valor} value={valor}>
                  {texto}
                </option>
              ))}
            </select>
          </label>
          <label className="campo">
            Resultado de admisión
            <select {...register('estadoAdmision')}>
              {Object.entries(ADMISION).map(([valor, texto]) => (
                <option key={valor} value={valor}>
                  {texto}
                </option>
              ))}
            </select>
          </label>
          <div className="acciones">
            <button className="btn" type="submit" disabled={registrar.isPending}>
              Guardar
            </button>
          </div>
          {registrar.error && (
            <p className="alerta alerta-error" role="alert">
              {mensajeDeMatricula(registrar.error)}
            </p>
          )}
        </form>
      ) : (
        <p className="muted">
          {TIPOS[s.tipoEstudiante]} · admisión: {ADMISION[s.estadoAdmision]}
        </p>
      )}
    </section>
  )
}

function Documentos({ s }: { s: DetalleSolicitud }) {
  const [error, setError] = useState<string | null>(null)

  async function ver(documentoId: string) {
    setError(null)
    try {
      window.open(await urlDocumento(s.codigo, documentoId), '_blank', 'noopener')
    } catch (e) {
      setError(mensajeDeMatricula(e))
    }
  }

  return (
    <section className="tarjeta" aria-labelledby="titulo-documentos">
      <h2 id="titulo-documentos">Documentos adjuntos</h2>
      {s.documentos.length === 0 && <p className="muted">La solicitud no tiene documentos.</p>}
      <ul>
        {s.documentos.map((d) => (
          <li key={d.id}>
            {d.nombreArchivo}{' '}
            <button type="button" className="btn" onClick={() => void ver(d.id)} aria-label={`Ver ${d.nombreArchivo}`}>
              Ver
            </button>
          </li>
        ))}
      </ul>
      {error && (
        <p className="alerta alerta-error" role="alert">
          {error}
        </p>
      )}
    </section>
  )
}

function Validaciones({ s }: { s: DetalleSolicitud }) {
  return (
    <section className="tarjeta" aria-labelledby="titulo-validaciones">
      <h2 id="titulo-validaciones">Validaciones automáticas</h2>
      <ul className="validaciones">
        {s.validaciones.map((v) => (
          <li key={v.regla}>
            <span className={v.aprobada ? 'etiqueta etiqueta-si' : 'etiqueta etiqueta-no'}>{v.aprobada ? 'Cumple' : 'No cumple'}</span>{' '}
            {v.mensaje}
          </li>
        ))}
      </ul>
    </section>
  )
}

function Acciones({
  s,
  alConfirmar,
  alRechazar,
}: {
  s: DetalleSolicitud
  alConfirmar: (r: ResultadoConfirmacion) => void
  alRechazar: (r: ResultadoRechazo) => void
}) {
  const puedeRegistrar = usePermiso('MATRICULA_REGISTRAR')
  const confirmar = useConfirmarMatricula(s.codigo)
  const rechazar = useRechazarSolicitud(s.codigo)
  const sugeridos = s.validaciones.filter((v) => !v.aprobada && v.motivoSugerido).map((v) => v.motivoSugerido as MotivoRechazo)
  const sinVacantes = s.secciones.every((sec) => sec.vacantes === 0)
  const motivoInicial: MotivoRechazo = sugeridos[0] ?? (sinVacantes ? 'SIN_VACANTE' : 'VOLUNTAD_APODERADO')

  const formConfirmacion = useForm<DatosConfirmacion>({
    resolver: zodResolver(esquemaConfirmacion),
    defaultValues: { seccionId: '', costosAceptados: false },
  })
  const formRechazo = useForm<DatosRechazo>({
    resolver: zodResolver(esquemaRechazo),
    values: { motivo: motivoInicial, observacion: '' },
  })

  if (!puedeRegistrar) {
    return <p className="muted">Tu cuenta puede revisar la solicitud, pero no confirmarla ni rechazarla.</p>
  }

  const errorOperacion = confirmar.error ?? rechazar.error

  return (
    <>
      {errorOperacion && (
        <div className="alerta alerta-error" role="alert">
          {mensajeDeMatricula(errorOperacion)}
          {esConflictoDeVersion(errorOperacion) && (
            <>
              {' '}
              <button type="button" className="btn" onClick={() => window.location.reload()}>
                Recargar
              </button>
            </>
          )}
        </div>
      )}

      <form
        className="tarjeta formulario"
        aria-label="Confirmar matrícula"
        noValidate
        onSubmit={formConfirmacion.handleSubmit((datos) =>
          confirmar.mutate({ seccionId: datos.seccionId, version: s.version }, { onSuccess: alConfirmar }),
        )}
      >
        <h2>Confirmar matrícula</h2>
        <fieldset className="grupo-opciones">
          <legend>Sección</legend>
          {s.secciones.map((sec) => (
            <label key={sec.id} className="opcion-radio">
              <input type="radio" value={sec.id} disabled={sec.vacantes === 0} {...formConfirmacion.register('seccionId')} />
              <span>{sec.nombre}</span>
              <span className="muted">
                {sec.vacantes} de {sec.capacidad} vacantes
              </span>
            </label>
          ))}
          {formConfirmacion.formState.errors.seccionId && (
            <span className="error-campo">{formConfirmacion.formState.errors.seccionId.message}</span>
          )}
        </fieldset>

        <h3>Resumen de costos</h3>
        <div className="tabla-scroll">
          <table aria-label="Resumen de costos">
            <thead>
              <tr>
                <th>Concepto</th>
                <th>Periodo</th>
                <th>Vence</th>
                <th>Monto</th>
              </tr>
            </thead>
            <tbody>
              {s.costos.lineas.map((l) => (
                <tr key={`${l.concepto}-${l.periodo}`}>
                  <td>{CONCEPTOS[l.concepto] ?? l.concepto}</td>
                  <td>{l.periodo}</td>
                  <td>{l.fechaVencimiento}</td>
                  <td className="nowrap">S/ {l.monto}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th colSpan={3}>Total ({s.costos.lineas.length} compromisos)</th>
                <th className="nowrap">S/ {s.costos.total}</th>
              </tr>
            </tfoot>
          </table>
        </div>
        <p className="muted">Penalidad por pago vencido: {s.costos.porcentajePenalidad}% sobre la cuota.</p>

        <label className="opcion-radio">
          <input type="checkbox" {...formConfirmacion.register('costosAceptados')} />
          <span>El apoderado aceptó los costos</span>
        </label>
        {formConfirmacion.formState.errors.costosAceptados && (
          <span className="error-campo">{formConfirmacion.formState.errors.costosAceptados.message}</span>
        )}
        {!s.puedeConfirmar && (
          <p className="alerta alerta-error">
            No se puede confirmar mientras una validación no se cumpla. Corrige el dato o rechaza la solicitud.
          </p>
        )}
        <div className="acciones">
          <button className="btn btn-primario" type="submit" disabled={!s.puedeConfirmar || confirmar.isPending}>
            {confirmar.isPending ? 'Confirmando…' : 'Confirmar matrícula'}
          </button>
        </div>
      </form>

      <form
        className="tarjeta formulario"
        aria-label="Rechazar solicitud"
        noValidate
        onSubmit={formRechazo.handleSubmit((datos) =>
          rechazar.mutate({ ...datos, version: s.version }, { onSuccess: alRechazar }),
        )}
      >
        <h2>Rechazar solicitud</h2>
        <label className="campo">
          Motivo
          <select {...formRechazo.register('motivo')}>
            {(Object.keys(MOTIVOS) as MotivoRechazo[]).map((m) => (
              <option key={m} value={m}>
                {MOTIVOS[m]}
                {sugeridos.includes(m) ? ' (según las validaciones)' : ''}
              </option>
            ))}
          </select>
          {formRechazo.formState.errors.motivo && <span className="error-campo">{formRechazo.formState.errors.motivo.message}</span>}
        </label>
        <label className="campo">
          Observación (opcional)
          <textarea rows={3} {...formRechazo.register('observacion')} />
          {formRechazo.formState.errors.observacion && (
            <span className="error-campo">{formRechazo.formState.errors.observacion.message}</span>
          )}
        </label>
        <div className="acciones">
          <button className="btn" type="submit" disabled={rechazar.isPending}>
            {rechazar.isPending ? 'Rechazando…' : 'Rechazar solicitud'}
          </button>
        </div>
      </form>
    </>
  )
}
