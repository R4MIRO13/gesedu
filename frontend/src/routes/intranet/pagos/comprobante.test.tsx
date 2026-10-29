import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { hoyLima } from '../../../compartido/formato'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { Obligacion, ObligacionesAlumno } from '../../../modulos/pagos/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const SESION_APODERADO = sesionDe('APODERADO', ['MATRICULA_CONSULTAR_PROPIA', 'OBLIGACION_CONSULTAR_PROPIA', 'COMPROBANTE_PRESENTAR'])

function obligacion(parcial: Partial<Obligacion> = {}): Obligacion {
  return {
    id: 'o-sep',
    concepto: 'PENSION',
    periodo: '2027-09',
    montoBase: '350.00',
    montoPenalidad: '0.00',
    total: '350.00',
    fechaVencimiento: '2027-09-30',
    estado: 'PENDIENTE',
    penalidadAplicadaEn: null,
    boleta: null,
    comprobante: null,
    puedePresentarComprobante: true,
    ...parcial,
  }
}

function datosDe(o: Obligacion): ObligacionesAlumno {
  return {
    alumnoId: 'a-lucia',
    codigoAlumno: '2026-0315',
    alumno: 'Lucía García Rojas',
    obligaciones: [o],
    resumen: { totalPagado: '0.00', totalPendiente: '350.00', deudaVencida: '0.00', obligaciones: 1, pagadas: 0, pendientes: 1, vencidas: 0, proximoVencimiento: null },
  }
}

interface Simulacion {
  comprobantes: unknown[]
  subidas: { nombre: string; contentType: string }[]
}

function simular(o: Obligacion = obligacion()): Simulacion {
  const s: Simulacion = { comprobantes: [], subidas: [] }
  servidor.use(
    http.get('*/api/v1/alumnos/a-lucia/obligaciones', () => HttpResponse.json(datosDe(o))),
    http.post('*/api/v1/obligaciones/o-sep/comprobantes/archivo', async ({ request }) => {
      const cuerpo = (await request.json()) as { nombreArchivo: string; contentType: string }
      s.subidas.push({ nombre: cuerpo.nombreArchivo, contentType: cuerpo.contentType })
      return HttpResponse.json({
        claveObjeto: `comprobantes/ap-1/${cuerpo.nombreArchivo}`,
        subida: { url: '/api/v1/archivos-locales/token-v', metodo: 'PUT', expiraEn: '2027-09-28T10:05:00Z', cabeceras: { 'Content-Type': cuerpo.contentType } },
      })
    }),
    http.put('*/api/v1/archivos-locales/token-v', () => new HttpResponse(null, { status: 200 })),
    http.post('*/api/v1/obligaciones/o-sep/comprobantes', async ({ request }) => {
      s.comprobantes.push(await request.json())
      return HttpResponse.json(
        { id: 'c-1', estado: 'PENDIENTE', observacion: null, numeroOperacion: '00458712', fechaPago: hoyLima(), montoPresentado: '350.00', medioPago: 'Transferencia bancaria - BCP' },
        { status: 201 },
      )
    }),
  )
  return s
}

const RUTA = '/intranet/pagos/comprobante?obligacion=o-sep&alumno=a-lucia'
const JPG = () => new File(['jpg'], 'voucher_septiembre.jpg', { type: 'image/jpeg' })

describe('Presentar comprobante (UC-04)', () => {
  beforeEach(() => guardarSesion(SESION_APODERADO))

  it('flujo básico: muestra la obligación, sube el voucher y confirma que queda pendiente de validación', async () => {
    const s = simular()
    await renderizarRuta(RUTA)

    expect(await screen.findByText('Pensión septiembre 2027')).toBeInTheDocument()
    expect(screen.getByText('S/ 350.00')).toBeInTheDocument()
    expect(screen.getByText('30/09/2027')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText(/^Número de operación/), '00458712')
    await userEvent.type(screen.getByLabelText(/^Banco o medio de pago/), 'Transferencia bancaria - BCP')
    await userEvent.upload(screen.getByLabelText(/^Foto o PDF del voucher/), JPG())
    expect(await screen.findByText('Adjuntado: voucher_septiembre.jpg')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Enviar comprobante' }))

    expect(await screen.findByText(/Está pendiente de validación/)).toBeInTheDocument()
    expect(s.subidas).toEqual([{ nombre: 'voucher_septiembre.jpg', contentType: 'image/jpeg' }])
    expect(s.comprobantes).toEqual([
      {
        numeroOperacion: '00458712',
        fechaPago: hoyLima(),
        montoPagado: '350.00',
        medioPago: 'Transferencia bancaria - BCP',
        claveObjeto: 'comprobantes/ap-1/voucher_septiembre.jpg',
        contentType: 'image/jpeg',
        tamanoBytes: 3,
      },
    ])
  })

  it('FA-1: con datos o voucher faltantes informa qué completar y no envía', async () => {
    const s = simular()
    await renderizarRuta(RUTA)

    await userEvent.click(await screen.findByRole('button', { name: 'Enviar comprobante' }))
    expect(await screen.findByText('Ingresa el número de operación.')).toBeInTheDocument()
    expect(screen.getByText('Indica el banco o medio de pago.')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText(/^Número de operación/), '00458712')
    await userEvent.type(screen.getByLabelText(/^Banco o medio de pago/), 'BCP')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar comprobante' }))
    expect(await screen.findByText('Adjunta la foto o el PDF del voucher.')).toBeInTheDocument()
    expect(s.comprobantes).toHaveLength(0)
  })

  it('rechaza en el navegador un archivo que no es JPG, PNG ni PDF', async () => {
    const s = simular()
    await renderizarRuta(RUTA)
    const entrada = await screen.findByLabelText(/^Foto o PDF del voucher/)

    await userEvent.upload(entrada, new File(['MZ'], 'virus.exe', { type: 'application/x-msdownload' }), { applyAccept: false })

    expect(await screen.findByText('Formatos admitidos: JPG, PNG o PDF.')).toBeInTheDocument()
    expect(s.subidas).toHaveLength(0)
  })

  it('un número de operación ya presentado se advierte bajo el campo', async () => {
    simular()
    servidor.use(
      http.post('*/api/v1/obligaciones/o-sep/comprobantes', () =>
        problema(409, 'NUMERO_OPERACION_DUPLICADO', 'El número de operación ya fue presentado en otro comprobante'),
      ),
    )
    await renderizarRuta(RUTA)

    await userEvent.type(await screen.findByLabelText(/^Número de operación/), '00458712')
    await userEvent.type(screen.getByLabelText(/^Banco o medio de pago/), 'BCP')
    await userEvent.upload(screen.getByLabelText(/^Foto o PDF del voucher/), JPG())
    await screen.findByText('Adjuntado: voucher_septiembre.jpg')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar comprobante' }))

    expect(await screen.findByText('El número de operación ya fue presentado en otro comprobante')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText(/^Número de operación/)).toHaveAttribute('aria-invalid', 'true'))
  })

  it('FA-2: una obligación pagada no muestra el formulario', async () => {
    simular(obligacion({ estado: 'PAGADA', puedePresentarComprobante: false }))
    await renderizarRuta(RUTA)

    expect(await screen.findByText('Esta obligación ya está pagada: no corresponde presentar un nuevo comprobante.')).toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Datos del pago' })).not.toBeInTheDocument()
  })

  it('con un comprobante pendiente de revisión no deja presentar otro', async () => {
    simular(obligacion({ puedePresentarComprobante: false, comprobante: { id: 'c-0', estado: 'PENDIENTE', observacion: null, numeroOperacion: '1', fechaPago: '2027-09-28', montoPresentado: '350.00', medioPago: 'BCP' } }))
    await renderizarRuta(RUTA)

    expect(await screen.findByText(/ya tiene un comprobante pendiente de revisión/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enviar comprobante' })).not.toBeInTheDocument()
  })

  it('sin obligación en la dirección pide elegirla primero', async () => {
    await renderizarRuta('/intranet/pagos/comprobante')

    expect(await screen.findByText('Elige primero la obligación en «Matrícula y pagos».')).toBeInTheDocument()
  })

  it('sin el permiso COMPROBANTE_PRESENTAR la ruta no se muestra', async () => {
    guardarSesion(sesionDe('DOCENTE', ['HORARIO_CONSULTAR']))
    simular()
    await renderizarRuta(RUTA)

    expect(screen.queryByRole('heading', { name: 'Presentar comprobante de pago' })).not.toBeInTheDocument()
  })
})
