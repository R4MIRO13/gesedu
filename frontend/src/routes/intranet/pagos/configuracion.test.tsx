import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { ConfiguracionCobros } from '../../../modulos/pagos/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const SESION_COBROS = sesionDe('ADMINISTRADOR', ['COBRO_CONFIGURAR'], { cargo: 'Tesorería' })

const SIN_CONFIGURAR: ConfiguracionCobros = { anio: 2027, porcentajePenalidad: null, lineas: [], configurado: false }
const CONFIGURADO: ConfiguracionCobros = {
  anio: 2027,
  porcentajePenalidad: 5,
  configurado: true,
  lineas: [
    { concepto: 'MATRICULA', periodo: '2027', monto: '350.00', fechaVencimiento: '2027-02-15' },
    { concepto: 'PENSION', periodo: '2027-03', monto: '350.00', fechaVencimiento: '2027-03-31' },
  ],
}

function simular(inicial: ConfiguracionCobros, cuerpos: unknown[] = []) {
  servidor.use(
    http.get('*/api/v1/anios-escolares/2027/cobros', () => HttpResponse.json(inicial)),
    http.put('*/api/v1/anios-escolares/2027/cobros', async ({ request }) => {
      const cuerpo = (await request.json()) as { porcentajePenalidad: number; lineas: ConfiguracionCobros['lineas'] }
      cuerpos.push(cuerpo)
      return HttpResponse.json({ anio: 2027, porcentajePenalidad: cuerpo.porcentajePenalidad, lineas: cuerpo.lineas, configurado: true })
    }),
  )
}

describe('Configuración de cobros (RF-06)', () => {
  beforeEach(() => guardarSesion(SESION_COBROS))

  it('sin configuración propone matrícula y diez pensiones y exige montos', async () => {
    simular(SIN_CONFIGURAR)
    await renderizarRuta('/intranet/pagos/configuracion')

    expect(await screen.findByText('Sin configurar')).toBeInTheDocument()
    expect(screen.getByLabelText('Monto de Matrícula 2027')).toBeInTheDocument()
    expect(screen.getByLabelText('Monto de Pensión 2027-12')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Guardar configuración' }))
    expect(await screen.findByText('Ingresa un porcentaje válido, por ejemplo 5.')).toBeInTheDocument()
    expect(screen.getAllByText('Ingresa un monto válido, por ejemplo 350.00.')).toHaveLength(11)
  })

  it('guarda la penalidad y el cronograma como texto decimal', async () => {
    const cuerpos: unknown[] = []
    simular(CONFIGURADO, cuerpos)
    await renderizarRuta('/intranet/pagos/configuracion')

    const penalidad = await screen.findByLabelText('Penalidad por pago vencido (%)')
    expect(penalidad).toHaveValue('5')
    await userEvent.clear(penalidad)
    await userEvent.type(penalidad, '7.5')
    const pension = screen.getByLabelText('Monto de Pensión 2027-03')
    await userEvent.clear(pension)
    await userEvent.type(pension, '360.50')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar configuración' }))

    expect(await screen.findByText('Se guardó la configuración de cobros.')).toBeInTheDocument()
    expect(cuerpos).toEqual([
      {
        porcentajePenalidad: 7.5,
        lineas: [
          { concepto: 'MATRICULA', periodo: '2027', monto: '350.00', fechaVencimiento: '2027-02-15' },
          { concepto: 'PENSION', periodo: '2027-03', monto: '360.50', fechaVencimiento: '2027-03-31' },
        ],
      },
    ])
  })

  it('permite agregar y quitar la cuota de ingreso', async () => {
    simular(CONFIGURADO)
    await renderizarRuta('/intranet/pagos/configuracion')

    await userEvent.click(await screen.findByRole('button', { name: 'Agregar cuota de ingreso' }))
    expect(screen.getByLabelText('Monto de Cuota de ingreso 2027')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Agregar cuota de ingreso' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Quitar' }))
    expect(screen.queryByLabelText('Monto de Cuota de ingreso 2027')).not.toBeInTheDocument()
  })

  it('muestra el error de la API al guardar', async () => {
    simular(CONFIGURADO)
    servidor.use(
      http.put('*/api/v1/anios-escolares/2027/cobros', () =>
        problema(400, 'VALIDACION_INVALIDA', 'El periodo 2001-03 no corresponde al año 2027'),
      ),
    )
    await renderizarRuta('/intranet/pagos/configuracion')

    await userEvent.click(await screen.findByRole('button', { name: 'Guardar configuración' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('El periodo 2001-03 no corresponde al año 2027'))
  })

  it('sin el permiso COBRO_CONFIGURAR la ruta no se muestra', async () => {
    guardarSesion(sesionDe('APODERADO', ['OBLIGACION_CONSULTAR_PROPIA']))
    simular(CONFIGURADO)
    await renderizarRuta('/intranet/pagos/configuracion')

    expect(screen.queryByLabelText('Penalidad por pago vencido (%)')).not.toBeInTheDocument()
  })
})
