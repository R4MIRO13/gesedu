import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { ApiError } from '../../../compartido/api/errores'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { fechaCorta, hoyLima, soles } from '../../../compartido/formato'
import {
  nombreObligacion,
  subirVoucher,
  TAMANO_MAXIMO_VOUCHER,
  TIPOS_VOUCHER_PERMITIDOS,
  useObligaciones,
  useRegistrarComprobante,
  type Obligacion,
  type ObligacionesAlumno,
  type VoucherSubido,
} from '../../../modulos/pagos/api'
import { esquemaComprobante, type DatosComprobante } from '../../../modulos/pagos/esquemas'

export const Route = createFileRoute('/intranet/pagos/comprobante')({
  beforeLoad: () => exigirPermiso('COMPROBANTE_PRESENTAR'),
  validateSearch: (busqueda: Record<string, unknown>): { obligacion?: string; alumno?: string } => ({
    obligacion: typeof busqueda.obligacion === 'string' ? busqueda.obligacion : undefined,
    alumno: typeof busqueda.alumno === 'string' ? busqueda.alumno : undefined,
  }),
  component: PresentarComprobante,
})

const ESTADO = { PENDIENTE: 'Pendiente', PAGADA: 'Pagada', VENCIDA: 'Vencida' } as const

function PresentarComprobante() {
  const { obligacion: obligacionId, alumno } = Route.useSearch()
  const obligaciones = useObligaciones(alumno)

  if (!obligacionId || !alumno) {
    return (
      <>
        <h1>Presentar comprobante de pago</h1>
        <p className="muted">Elige primero la obligación en «Matrícula y pagos».</p>
        <Link to="/intranet/pagos" className="btn">
          Ir a Matrícula y pagos
        </Link>
      </>
    )
  }
  if (obligaciones.isPending) return <p className="muted">Cargando…</p>
  if (obligaciones.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {obligaciones.error.message}
      </p>
    )
  }
  const obligacion = obligaciones.data.obligaciones.find((o) => o.id === obligacionId)
  if (!obligacion) {
    return (
      <>
        <h1>Presentar comprobante de pago</h1>
        <p className="alerta alerta-error" role="alert">
          No encontramos esa obligación entre las de tu estudiante.
        </p>
        <Link to="/intranet/pagos" search={{ alumno }} className="btn">
          Volver a Matrícula y pagos
        </Link>
      </>
    )
  }
  return <Formulario datos={obligaciones.data} obligacion={obligacion} />
}

function Formulario({ datos, obligacion }: { datos: ObligacionesAlumno; obligacion: Obligacion }) {
  const registrar = useRegistrarComprobante(obligacion.id)
  const [voucher, setVoucher] = useState<VoucherSubido | null>(null)
  const [subiendo, setSubiendo] = useState(false)
  const [errorVoucher, setErrorVoucher] = useState<string | null>(null)
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null)
  const [recibido, setRecibido] = useState(false)
  const form = useForm<DatosComprobante>({
    resolver: zodResolver(esquemaComprobante),
    defaultValues: { numeroOperacion: '', fechaPago: hoyLima(), montoPagado: obligacion.total, medioPago: '' },
  })
  const errores = form.formState.errors
  const nombre = nombreObligacion(obligacion.concepto, obligacion.periodo)

  async function adjuntar(archivo: File | undefined) {
    if (!archivo) return
    setErrorVoucher(null)
    if (!TIPOS_VOUCHER_PERMITIDOS.includes(archivo.type)) return setErrorVoucher('Formatos admitidos: JPG, PNG o PDF.')
    if (archivo.size > TAMANO_MAXIMO_VOUCHER) return setErrorVoucher('El archivo debe pesar hasta 5 MB.')
    setSubiendo(true)
    try {
      setVoucher(await subirVoucher(obligacion.id, archivo))
    } catch (error) {
      setErrorVoucher(error instanceof ApiError ? error.message : 'No se pudo subir el archivo.')
    } finally {
      setSubiendo(false)
    }
  }

  function enviar(d: DatosComprobante) {
    setErrorEnvio(null)
    // FA-1: sin el voucher no hay comprobante
    if (!voucher) return setErrorVoucher('Adjunta la foto o el PDF del voucher.')
    registrar.mutate(
      { numeroOperacion: d.numeroOperacion.trim(), fechaPago: d.fechaPago, montoPagado: d.montoPagado.trim(), medioPago: d.medioPago.trim(), voucher },
      {
        onSuccess: () => setRecibido(true),
        onError: (error) => {
          if (!(error instanceof ApiError)) return setErrorEnvio('No se pudo enviar el comprobante. Inténtalo de nuevo.')
          if (error.codigo === 'NUMERO_OPERACION_DUPLICADO') form.setError('numeroOperacion', { message: error.message })
          else if (error.codigo === 'VALIDACION_INVALIDA' && error.errores.length > 0) {
            for (const e of error.errores) form.setError(e.campo as keyof DatosComprobante, { message: e.mensaje })
          } else setErrorEnvio(error.message)
        },
      },
    )
  }

  if (recibido) {
    return (
      <section className="tarjeta" aria-labelledby="titulo-recibido">
        <h1 id="titulo-recibido">Comprobante recibido</h1>
        <p className="alerta alerta-ok" role="status">
          Recibimos tu comprobante de {nombre.toLowerCase()} {obligacion.periodo.slice(0, 4)}. Está pendiente de validación: la
          obligación cambiará a «Pagada» cuando el colegio lo valide.
        </p>
        <Link to="/intranet/pagos" search={{ alumno: datos.alumnoId }} className="btn btn-primario">
          Volver a Matrícula y pagos
        </Link>
      </section>
    )
  }

  const bloqueada = obligacion.estado === 'PAGADA' || !obligacion.puedePresentarComprobante
  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Presentar comprobante de pago</h1>
        </div>
      </div>

      <section className="tarjeta" aria-labelledby="titulo-obligacion">
        <h2 id="titulo-obligacion">Obligación seleccionada</h2>
        <dl className="datos">
          <dt>Obligación</dt>
          <dd>
            {nombre} {obligacion.periodo.slice(0, 4)}
          </dd>
          <dt>Estudiante</dt>
          <dd>{datos.alumno}</dd>
          <dt>Importe</dt>
          <dd>{soles(obligacion.total)}</dd>
          <dt>Vence</dt>
          <dd>{fechaCorta(obligacion.fechaVencimiento)}</dd>
          <dt>Estado</dt>
          <dd>
            <span className={obligacion.estado === 'VENCIDA' ? 'etiqueta etiqueta-no' : 'etiqueta'}>{ESTADO[obligacion.estado]}</span>
          </dd>
        </dl>
        <Link to="/intranet/pagos" search={{ alumno: datos.alumnoId }}>
          Cambiar obligación
        </Link>
      </section>

      {obligacion.estado === 'PAGADA' && (
        <p className="alerta" role="status">
          Esta obligación ya está pagada: no corresponde presentar un nuevo comprobante.
        </p>
      )}
      {obligacion.estado !== 'PAGADA' && !obligacion.puedePresentarComprobante && (
        <p className="alerta" role="status">
          Esta obligación ya tiene un comprobante pendiente de revisión. Espera a que el colegio lo valide.
        </p>
      )}
      {errorEnvio && (
        <p className="alerta alerta-error" role="alert">
          {errorEnvio}
        </p>
      )}

      {!bloqueada && (
        <form className="tarjeta formulario" onSubmit={form.handleSubmit(enviar)} noValidate aria-label="Datos del pago">
          <h2>Datos del pago</h2>
          <div className="fila">
            <label className="campo">
              Número de operación *
              <input placeholder="Ej. 00458712" autoComplete="off" {...form.register('numeroOperacion')} aria-invalid={!!errores.numeroOperacion} />
              <span className="muted">Ingresa el número de operación que figura en tu voucher.</span>
              {errores.numeroOperacion && <span className="error-campo">{errores.numeroOperacion.message}</span>}
            </label>
            <label className="campo">
              Fecha de pago *
              <input type="date" max={hoyLima()} {...form.register('fechaPago')} aria-invalid={!!errores.fechaPago} />
              {errores.fechaPago && <span className="error-campo">{errores.fechaPago.message}</span>}
            </label>
          </div>
          <div className="fila">
            <label className="campo">
              Banco o medio de pago *
              <input placeholder="Ej. Transferencia bancaria - BCP" autoComplete="off" {...form.register('medioPago')} aria-invalid={!!errores.medioPago} />
              {errores.medioPago && <span className="error-campo">{errores.medioPago.message}</span>}
            </label>
            <label className="campo">
              Monto pagado (S/) *
              <input inputMode="decimal" {...form.register('montoPagado')} aria-invalid={!!errores.montoPagado} />
              {errores.montoPagado && <span className="error-campo">{errores.montoPagado.message}</span>}
            </label>
          </div>
          <div className="campo">
            <label htmlFor="voucher">Foto o PDF del voucher *</label>
            <input
              id="voucher"
              type="file"
              accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
              disabled={subiendo}
              onChange={(e) => void adjuntar(e.target.files?.[0])}
              aria-invalid={!!errorVoucher}
            />
            <span className="muted">Formatos: JPG, PNG o PDF · hasta 5 MB.</span>
            {subiendo && <span role="status">Subiendo…</span>}
            {voucher && <span className="etiqueta etiqueta-si">Adjuntado: {voucher.nombreArchivo}</span>}
            {errorVoucher && <span className="error-campo">{errorVoucher}</span>}
          </div>
          <p className="muted">El estado de la obligación cambiará a «Pagada» cuando el colegio valide tu comprobante.</p>
          <div className="acciones">
            <button className="btn btn-primario" type="submit" disabled={registrar.isPending || subiendo}>
              {registrar.isPending ? 'Enviando…' : 'Enviar comprobante'}
            </button>
            <Link to="/intranet/pagos" search={{ alumno: datos.alumnoId }} className="btn">
              Cancelar
            </Link>
          </div>
        </form>
      )}
    </>
  )
}
