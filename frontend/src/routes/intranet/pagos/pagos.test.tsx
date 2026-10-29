import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { HijoMatricula } from '../../../modulos/matricula/api'
import type { Obligacion, ObligacionesAlumno } from '../../../modulos/pagos/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const SESION_APODERADO = sesionDe('APODERADO', ['MATRICULA_CONSULTAR_PROPIA', 'OBLIGACION_CONSULTAR_PROPIA', 'COMPROBANTE_PRESENTAR'], {
  nombres: 'María Rojas',
})

const LUCIA: HijoMatricula = {
  alumnoId: 'a-lucia',
  codigoAlumno: '2026-0315',
  alumno: 'Lucía García Rojas',
  matricula: { matriculaId: 'm-1', anio: 2027, grado: '3° de secundaria', seccion: '3° A', estado: 'Confirmada', fechaMatricula: '2027-01-06' },
}
const ANDRES: HijoMatricula = { alumnoId: 'a-andres', codigoAlumno: '2026-0316', alumno: 'Andrés García Rojas', matricula: null }

function obligacion(parcial: Partial<Obligacion>): Obligacion {
  return {
    id: 'o-0',
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

const OBLIGACIONES: ObligacionesAlumno = {
  alumnoId: 'a-lucia',
  codigoAlumno: '2026-0315',
  alumno: 'Lucía García Rojas',
  obligaciones: [
    obligacion({ id: 'o-mat', concepto: 'MATRICULA', periodo: '2027', fechaVencimiento: '2027-02-15', estado: 'PAGADA', puedePresentarComprobante: false, boleta: { numero: 'B001-00587', fechaRegistro: '2027-02-10T10:00:00-05:00', monto: '350.00' } }),
    obligacion({ id: 'o-ago', periodo: '2027-08', fechaVencimiento: '2027-08-31', estado: 'VENCIDA', montoPenalidad: '17.50', total: '367.50' }),
    obligacion({ id: 'o-sep', periodo: '2027-09', fechaVencimiento: '2027-09-30', puedePresentarComprobante: false, comprobante: { id: 'c-1', estado: 'PENDIENTE', observacion: null, numeroOperacion: '00458712', fechaPago: '2027-09-28', montoPresentado: '350.00', medioPago: 'Transferencia' } }),
    obligacion({ id: 'o-oct', periodo: '2027-10', fechaVencimiento: '2027-10-31', comprobante: { id: 'c-2', estado: 'OBSERVADO', observacion: 'El monto no cubre la penalidad', numeroOperacion: '00458713', fechaPago: '2027-10-28', montoPresentado: '300.00', medioPago: 'Transferencia' } }),
    obligacion({ id: 'o-nov', periodo: '2027-11', fechaVencimiento: '2027-11-30' }),
  ],
  resumen: {
    totalPagado: '350.00',
    totalPendiente: '1417.50',
    deudaVencida: '367.50',
    obligaciones: 5,
    pagadas: 1,
    pendientes: 3,
    vencidas: 1,
    proximoVencimiento: { concepto: 'PENSION', periodo: '2027-09', fechaVencimiento: '2027-09-30', total: '350.00' },
  },
}

function simular(hijos: HijoMatricula[], obligaciones: Record<string, ObligacionesAlumno> = { 'a-lucia': OBLIGACIONES }) {
  servidor.use(
    http.get('*/api/v1/matriculas/mias', () => HttpResponse.json(hijos)),
    http.get('*/api/v1/alumnos/:id/obligaciones', ({ params }) => {
      const o = obligaciones[String(params.id)]
      return o ? HttpResponse.json(o) : problema(403, 'FUERA_DE_ALCANCE', 'El estudiante no está a tu cargo')
    }),
  )
}

describe('Matrícula y pagos (UC-03)', () => {
  beforeEach(() => guardarSesion(SESION_APODERADO))

  it('flujo básico: muestra la matrícula, la deuda vencida, el próximo vencimiento y cada obligación', async () => {
    simular([LUCIA, ANDRES])
    await renderizarRuta('/intranet/pagos')

    const matricula = await screen.findByRole('region', { name: 'Matrícula' })
    expect(within(matricula).getByText('Confirmada')).toBeInTheDocument()
    expect(within(matricula).getByText('3° de secundaria, sección A')).toBeInTheDocument()
    expect(within(await screen.findByRole('region', { name: 'Deuda vencida' })).getByText('S/ 367.50')).toBeInTheDocument()
    expect(screen.getByText('1 obligación vencida')).toBeInTheDocument()
    const proximo = screen.getByRole('region', { name: 'Próximo vencimiento' })
    expect(within(proximo).getByText('S/ 350.00')).toBeInTheDocument()
    expect(within(proximo).getByText('Pensión septiembre · 30/09/2027')).toBeInTheDocument()

    const filas = within(screen.getByRole('table', { name: 'Obligaciones' })).getAllByRole('row')
    expect(filas).toHaveLength(6)
    expect(within(filas[1]!).getByText('Boleta B001-00587')).toBeInTheDocument()
    expect(within(filas[1]!).getByText('Pagada')).toBeInTheDocument()
    expect(within(filas[3]!).getByText('Comprobante en revisión')).toBeInTheDocument()
  })

  it('la penalidad se muestra como «Importe base + penalidad = total»', async () => {
    simular([LUCIA])
    await renderizarRuta('/intranet/pagos')

    const tabla = await screen.findByRole('table', { name: 'Obligaciones' })
    const agosto = within(tabla).getByText('Pensión agosto').closest('tr')!
    expect(within(agosto).getByText('Importe base S/ 350.00 + penalidad S/ 17.50 = S/ 367.50')).toBeInTheDocument()
    expect(within(agosto).getByText('Vencida')).toBeInTheDocument()
    expect(within(agosto).getAllByText('S/ 367.50').length).toBeGreaterThan(0)
  })

  it('muestra la observación de un comprobante observado', async () => {
    simular([LUCIA])
    await renderizarRuta('/intranet/pagos')

    expect(await screen.findByText('Comprobante observado: El monto no cubre la penalidad')).toBeInTheDocument()
  })

  it('FA-1: sin obligaciones generadas lo informa', async () => {
    simular([LUCIA], {
      'a-lucia': { ...OBLIGACIONES, obligaciones: [], resumen: { ...OBLIGACIONES.resumen, obligaciones: 0, pagadas: 0, vencidas: 0, deudaVencida: '0.00', proximoVencimiento: null } },
    })
    await renderizarRuta('/intranet/pagos')

    expect(await screen.findByText('Aún no existen obligaciones de pago para el año 2027.')).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Obligaciones' })).not.toBeInTheDocument()
  })

  it('FA-2: un estudiante sin matrícula vigente no muestra obligaciones', async () => {
    simular([LUCIA, ANDRES])
    await renderizarRuta('/intranet/pagos?alumno=a-andres')

    expect(await screen.findByText(/no tiene una matrícula vigente/)).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Obligaciones' })).not.toBeInTheDocument()
  })

  it('se cambia de estudiante con los botones de arriba', async () => {
    simular([LUCIA, ANDRES])
    await renderizarRuta('/intranet/pagos')
    await screen.findByRole('table', { name: 'Obligaciones' })

    await userEvent.click(screen.getByRole('link', { name: 'Andrés' }))

    expect(await screen.findByText(/no tiene una matrícula vigente/)).toBeInTheDocument()
  })

  it('sin hijos registrados lo dice', async () => {
    simular([])
    await renderizarRuta('/intranet/pagos')

    expect(await screen.findByText('No tienes estudiantes a tu cargo registrados.')).toBeInTheDocument()
  })

  it('sin el permiso OBLIGACION_CONSULTAR_PROPIA la ruta no se muestra', async () => {
    guardarSesion(sesionDe('DOCENTE', ['HORARIO_CONSULTAR']))
    simular([LUCIA])
    await renderizarRuta('/intranet/pagos')

    expect(screen.queryByRole('heading', { name: 'Matrícula y pagos' })).not.toBeInTheDocument()
  })
})
