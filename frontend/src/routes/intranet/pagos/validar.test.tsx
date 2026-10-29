import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { ComprobanteBandeja, DetalleComprobante } from '../../../modulos/pagos/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const SESION_ADMIN_PAGOS = sesionDe('ADMINISTRADOR', ['COMPROBANTE_VALIDAR', 'PAGO_HISTORIAL_CONSULTAR'], { cargo: 'Tesorería' })

const FILAS: ComprobanteBandeja[] = [
  { id: 'c-1', enviado: '2027-09-28T10:15:00-05:00', estudiante: 'García Rojas, Lucía', codigoAlumno: '2026-0315', concepto: 'PENSION', periodo: '2027-08', montoPresentado: '367.50', numeroOperacion: '00458712', estado: 'PENDIENTE' },
  { id: 'c-2', enviado: '2027-09-29T09:00:00-05:00', estudiante: 'Flores Ramos, Camila', codigoAlumno: '2026-0316', concepto: 'PENSION', periodo: '2027-09', montoPresentado: '350.00', numeroOperacion: '00459120', estado: 'PENDIENTE' },
]

function detalle(parcial: Partial<DetalleComprobante> = {}): DetalleComprobante {
  return {
    id: 'c-1',
    estado: 'PENDIENTE',
    enviado: '2027-09-28T10:15:00-05:00',
    observacion: null,
    obligacion: { id: 'o-ago', alumno: 'García Rojas, Lucía', codigoAlumno: '2026-0315', concepto: 'PENSION', periodo: '2027-08', estado: 'VENCIDA', fechaVencimiento: '2027-08-31', montoBase: '350.00', montoPenalidad: '17.50', total: '367.50' },
    declarado: { apoderado: 'María Rojas Salazar', numeroOperacion: '00458712', fechaPago: '2027-09-28', medioPago: 'Transferencia - BCP', montoPresentado: '367.50' },
    verificaciones: { montoCubreTotal: true, montoCoincide: true, numeroOperacionNuevo: true, obligacionPorPagar: true },
    puedeRevisar: true,
    voucher: { url: '/api/v1/archivos-locales/token-g', metodo: 'GET', expiraEn: '2027-09-28T10:20:00Z' },
    ...parcial,
  }
}

interface Escritura {
  ruta: string
  cuerpo: unknown
}

function simular(d: DetalleComprobante = detalle(), escrituras: Escritura[] = []) {
  servidor.use(
    http.get('*/api/v1/comprobantes', ({ request }) => {
      const estado = new URL(request.url).searchParams.get('estado')
      return HttpResponse.json({ contenido: estado === 'PENDIENTE' ? FILAS : [], pagina: 0, tamano: 15, totalElementos: estado === 'PENDIENTE' ? 2 : 0, totalPaginas: 1 })
    }),
    http.get('*/api/v1/comprobantes/c-1', () => HttpResponse.json(d)),
    http.post('*/api/v1/comprobantes/c-1/validacion', () => {
      escrituras.push({ ruta: 'validacion', cuerpo: null })
      return HttpResponse.json({ comprobanteId: 'c-1', numeroBoleta: 'B001-00588', montoTotal: '367.50', fechaRegistro: '2027-09-29T10:00:00-05:00', estadoObligacion: 'PAGADA' })
    }),
    http.post('*/api/v1/comprobantes/c-1/observacion', async ({ request }) => {
      const cuerpo = (await request.json()) as { observacion: string }
      escrituras.push({ ruta: 'observacion', cuerpo })
      return HttpResponse.json({ comprobanteId: 'c-1', estado: 'OBSERVADO', observacion: cuerpo.observacion, estadoObligacion: 'VENCIDA', totalPendiente: '367.50' })
    }),
    http.post('*/api/v1/comprobantes/c-1/rechazo', async ({ request }) => {
      const cuerpo = (await request.json()) as { observacion: string }
      escrituras.push({ ruta: 'rechazo', cuerpo })
      return HttpResponse.json({ comprobanteId: 'c-1', estado: 'RECHAZADO', observacion: cuerpo.observacion, estadoObligacion: 'VENCIDA', totalPendiente: '367.50' })
    }),
  )
  return escrituras
}

describe('Validar comprobantes (UC-05)', () => {
  beforeEach(() => guardarSesion(SESION_ADMIN_PAGOS))

  it('paso 1: la bandeja lista los comprobantes pendientes con estudiante, concepto, monto y operación', async () => {
    simular()
    await renderizarRuta('/intranet/pagos/validar')

    const tabla = await screen.findByRole('table', { name: 'Comprobantes' })
    const filas = within(tabla).getAllByRole('row')
    expect(within(filas[1]!).getByText('García Rojas, Lucía')).toBeInTheDocument()
    expect(within(filas[1]!).getByText('Pensión agosto')).toBeInTheDocument()
    expect(within(filas[1]!).getByText('S/ 367.50')).toBeInTheDocument()
    expect(within(filas[1]!).getByText('00458712')).toBeInTheDocument()
    expect(screen.getByText('2 comprobantes')).toBeInTheDocument()
    expect(screen.getByText('Elige un comprobante de la lista para revisarlo.')).toBeInTheDocument()
  })

  it('pasos 2 a 4: el detalle muestra «Importe base + penalidad = total», lo declarado y las verificaciones', async () => {
    simular()
    await renderizarRuta('/intranet/pagos/validar?comprobante=c-1')

    const panel = await screen.findByRole('region', { name: /Comprobante de García Rojas, Lucía/ })
    expect(within(panel).getByText('Penalidad por mora')).toBeInTheDocument()
    expect(within(panel).getByText('S/ 17.50')).toBeInTheDocument()
    expect(within(panel).getByText('S/ 367.50', { selector: 'strong' })).toBeInTheDocument()
    expect(within(panel).getByText('María Rojas Salazar')).toBeInTheDocument()
    expect(within(panel).getByText('N.° de operación no registrado antes')).toBeInTheDocument()
    expect(within(panel).getByText('El monto cubre el importe base y la penalidad')).toBeInTheDocument()
    expect(within(panel).getByRole('link', { name: 'Ampliar voucher' })).toHaveAttribute('href', '/api/v1/archivos-locales/token-g')
  })

  it('pasos 5 a 9: «Validar y registrar boleta» muestra la boleta emitida', async () => {
    const escrituras = simular()
    await renderizarRuta('/intranet/pagos/validar?comprobante=c-1')

    await userEvent.click(await screen.findByRole('button', { name: 'Validar y registrar boleta' }))

    expect(await screen.findByText(/Pago registrado\. Boleta B001-00588 por S\/ 367\.50/)).toBeInTheDocument()
    expect(escrituras).toEqual([{ ruta: 'validacion', cuerpo: null }])
  })

  it('FA-2: un importe insuficiente bloquea la validación y se observa con un texto', async () => {
    const escrituras = simular(
      detalle({
        declarado: { apoderado: 'María Rojas Salazar', numeroOperacion: '00458712', fechaPago: '2027-09-28', medioPago: 'BCP', montoPresentado: '350.00' },
        verificaciones: { montoCubreTotal: false, montoCoincide: false, numeroOperacionNuevo: true, obligacionPorPagar: true },
      }),
    )
    await renderizarRuta('/intranet/pagos/validar?comprobante=c-1')

    expect(await screen.findByText(/no cubre el total a pagar/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Validar y registrar boleta' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Observar' }))
    await userEvent.click(screen.getByRole('button', { name: 'Observar comprobante' }))
    expect(await screen.findByText('Escribe el motivo para informar al apoderado.')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText(/^Observación para el apoderado/), 'El monto no cubre la penalidad')
    await userEvent.click(screen.getByRole('button', { name: 'Observar comprobante' }))

    expect(await screen.findByText(/Comprobante observado\. Se informó al apoderado el importe total pendiente: S\/ 367\.50/)).toBeInTheDocument()
    expect(escrituras).toEqual([{ ruta: 'observacion', cuerpo: { observacion: 'El monto no cubre la penalidad' } }])
  })

  it('FA-1: un comprobante que no corresponde se rechaza con su motivo', async () => {
    const escrituras = simular()
    await renderizarRuta('/intranet/pagos/validar?comprobante=c-1')

    await userEvent.click(await screen.findByRole('button', { name: 'Rechazar' }))
    await userEvent.type(screen.getByLabelText('Motivo del rechazo'), 'El voucher es de otro estudiante')
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar comprobante' }))

    expect(await screen.findByText(/Comprobante rechazado\. Se informó al apoderado que debe presentar la información correcta/)).toBeInTheDocument()
    expect(escrituras).toEqual([{ ruta: 'rechazo', cuerpo: { observacion: 'El voucher es de otro estudiante' } }])
  })

  it('si otra persona ya revisó el comprobante muestra el error de la API', async () => {
    simular()
    servidor.use(
      http.post('*/api/v1/comprobantes/c-1/validacion', () =>
        problema(422, 'OBLIGACION_YA_PAGADA', 'La obligación ya fue pagada'),
      ),
    )
    await renderizarRuta('/intranet/pagos/validar?comprobante=c-1')

    await userEvent.click(await screen.findByRole('button', { name: 'Validar y registrar boleta' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('La obligación ya fue pagada'))
  })

  it('un comprobante ya revisado no ofrece acciones', async () => {
    simular(detalle({ estado: 'APROBADO', puedeRevisar: false }))
    await renderizarRuta('/intranet/pagos/validar?comprobante=c-1')

    expect(await screen.findByText('Este comprobante ya no se puede revisar.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Validar y registrar boleta' })).not.toBeInTheDocument()
  })

  it('se filtra por estado y se avisa cuando no hay comprobantes', async () => {
    simular()
    await renderizarRuta('/intranet/pagos/validar')
    await screen.findByRole('table', { name: 'Comprobantes' })

    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'RECHAZADO')

    expect(await screen.findByText('No hay comprobantes rechazados.')).toBeInTheDocument()
  })

  it('sin el permiso COMPROBANTE_VALIDAR la ruta no se muestra', async () => {
    guardarSesion(sesionDe('APODERADO', ['OBLIGACION_CONSULTAR_PROPIA']))
    simular()
    await renderizarRuta('/intranet/pagos/validar')

    expect(screen.queryByRole('heading', { name: 'Validar comprobantes' })).not.toBeInTheDocument()
  })
})
