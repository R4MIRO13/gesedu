import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { HistorialFinanciero, Obligacion } from '../../../modulos/pagos/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const SESION_ADMIN = sesionDe('ADMINISTRADOR', ['PAGO_HISTORIAL_CONSULTAR'], { cargo: 'Tesorería' })

function obligacion(parcial: Partial<Obligacion>): Obligacion {
  return {
    id: 'o',
    concepto: 'PENSION',
    periodo: '2027-03',
    montoBase: '350.00',
    montoPenalidad: '0.00',
    total: '350.00',
    fechaVencimiento: '2027-03-31',
    estado: 'PENDIENTE',
    penalidadAplicadaEn: null,
    boleta: null,
    comprobante: null,
    puedePresentarComprobante: true,
    ...parcial,
  }
}

const HISTORIAL: HistorialFinanciero = {
  estudiante: { id: 'a-1', codigo: '2026-0315', nombre: 'Lucía García Rojas', documento: '76543218', apoderado: 'María Rojas Salazar' },
  matricula: { anio: 2027, grado: '3° de secundaria', seccion: '3° A', estado: 'Confirmada' },
  resumen: { totalPagado: '2100.00', totalPendiente: '1400.00', deudaVencida: '367.50', penalidadesAplicadas: '17.50', obligaciones: 3, pagadas: 1, pendientes: 1, vencidas: 1 },
  obligaciones: [
    obligacion({ id: 'o-1', concepto: 'MATRICULA', periodo: '2027', fechaVencimiento: '2027-02-15', estado: 'PAGADA' }),
    obligacion({ id: 'o-2', periodo: '2027-08', fechaVencimiento: '2027-08-31', estado: 'VENCIDA', montoPenalidad: '17.50', total: '367.50' }),
    obligacion({ id: 'o-3', periodo: '2027-09', fechaVencimiento: '2027-09-30' }),
  ],
  pagos: [
    { numeroBoleta: 'B001-00587', fechaRegistro: '2027-02-10T10:00:00-05:00', concepto: 'MATRICULA', periodo: '2027', monto: '350.00', numeroOperacion: '00451873', fechaPago: '2027-02-09', medioPago: 'Transferencia - BCP' },
  ],
  deudas: [
    { obligacionId: 'o-2', concepto: 'PENSION', periodo: '2027-08', fechaVencimiento: '2027-08-31', montoBase: '350.00', montoPenalidad: '17.50', total: '367.50', penalidadAplicadaEn: '2027-09-01T00:05:00-05:00' },
  ],
  sinHistorial: false,
  sinPagos: false,
}

function simular(h: HistorialFinanciero = HISTORIAL) {
  servidor.use(
    http.get('*/api/v1/alumnos/busqueda', ({ request }) => {
      const q = new URL(request.url).searchParams.get('q')
      return HttpResponse.json(q === 'lucia' ? [{ codigo: '2026-0315', nombre: 'Lucía García Rojas', documento: '76543218' }] : [])
    }),
    http.get('*/api/v1/alumnos/2026-0315/historial-financiero', () => HttpResponse.json(h)),
  )
}

describe('Historial financiero (UC-06)', () => {
  beforeEach(() => guardarSesion(SESION_ADMIN))

  it('busca al estudiante y abre su historial', async () => {
    simular()
    await renderizarRuta('/intranet/pagos/historial')

    await userEvent.type(await screen.findByLabelText('Buscar estudiante'), 'lucia')
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    await userEvent.click(await screen.findByRole('link', { name: 'Lucía García Rojas' }))

    expect(await screen.findByRole('heading', { name: 'Lucía García Rojas' })).toBeInTheDocument()
  })

  it('una búsqueda sin resultados lo dice', async () => {
    simular()
    await renderizarRuta('/intranet/pagos/historial')

    await userEvent.type(await screen.findByLabelText('Buscar estudiante'), 'zzz')
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }))

    expect(await screen.findByText('No encontramos estudiantes para «zzz».')).toBeInTheDocument()
  })

  it('flujo básico: cabecera, total pagado, pendiente, vencido con penalidades y las obligaciones', async () => {
    simular()
    await renderizarRuta('/intranet/pagos/historial/2026-0315')

    expect(await screen.findByText('2026-0315 · DNI 76543218')).toBeInTheDocument()
    expect(screen.getByText('3° A · 2027')).toBeInTheDocument()
    expect(screen.getByText('María Rojas Salazar')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Total pagado' })).getByText('S/ 2,100.00')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Pendiente' })).getByText('S/ 1,400.00')).toBeInTheDocument()
    const vencido = screen.getByRole('region', { name: 'Vencido con penalidades' })
    expect(within(vencido).getByText('S/ 367.50')).toBeInTheDocument()
    expect(within(vencido).getByText('1 obligación · incluye S/ 17.50 de penalidad')).toBeInTheDocument()
    const tabla = screen.getByRole('table', { name: 'Obligaciones' })
    expect(within(tabla).getAllByRole('row')).toHaveLength(4)
    expect(within(tabla).getByText('S/ 350.00 + penalidad S/ 17.50')).toBeInTheDocument()
    expect(screen.getByText(/Esta consulta no modifica obligaciones, pagos ni boletas/)).toBeInTheDocument()
  })

  it('las pestañas muestran las boletas con su operación y las deudas con la penalidad aplicada', async () => {
    simular()
    await renderizarRuta('/intranet/pagos/historial/2026-0315')

    await userEvent.click(await screen.findByRole('tab', { name: 'Boletas y pagos' }))
    const pagos = screen.getByRole('table', { name: 'Boletas y pagos' })
    expect(within(pagos).getByText('B001-00587')).toBeInTheDocument()
    expect(within(pagos).getByText('00451873')).toBeInTheDocument()
    expect(within(pagos).getByText('09/02/2027')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: 'Deudas y penalidades' }))
    const deudas = screen.getByRole('table', { name: 'Deudas y penalidades' })
    expect(within(deudas).getByText('Pensión agosto')).toBeInTheDocument()
    expect(within(deudas).getByText(/aplicada el 01\/09\/2027/)).toBeInTheDocument()
    expect(within(deudas).getByText('S/ 367.50')).toBeInTheDocument()
  })

  it('FA-1: sin obligaciones ni pagos informa que no hay información financiera', async () => {
    simular({ ...HISTORIAL, matricula: null, obligaciones: [], pagos: [], deudas: [], sinHistorial: true })
    await renderizarRuta('/intranet/pagos/historial/2026-0315')

    expect(await screen.findByText(/No existe información financiera disponible/)).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Obligaciones' })).not.toBeInTheDocument()
  })

  it('FA-2: con obligaciones pero sin pagos señala las pendientes y vencidas', async () => {
    simular({ ...HISTORIAL, pagos: [], sinPagos: true })
    await renderizarRuta('/intranet/pagos/historial/2026-0315')

    expect(await screen.findByText(/Todavía no se registraron pagos/)).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Obligaciones' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Boletas y pagos' }))
    expect(screen.getByText('Aún no hay boletas ni pagos registrados.')).toBeInTheDocument()
  })

  it('un estudiante que no existe muestra el error', async () => {
    servidor.use(http.get('*/api/v1/alumnos/NO-EXISTE/historial-financiero', () => problema(404, 'RECURSO_NO_ENCONTRADO', 'No existe el estudiante NO-EXISTE')))
    await renderizarRuta('/intranet/pagos/historial/NO-EXISTE')

    expect(await screen.findByRole('alert')).toHaveTextContent('No existe el estudiante NO-EXISTE')
  })

  it('sin el permiso PAGO_HISTORIAL_CONSULTAR la ruta no se muestra', async () => {
    guardarSesion(sesionDe('APODERADO', ['OBLIGACION_CONSULTAR_PROPIA']))
    simular()
    await renderizarRuta('/intranet/pagos/historial/2026-0315')

    expect(screen.queryByText('Historial financiero del estudiante')).not.toBeInTheDocument()
  })
})
