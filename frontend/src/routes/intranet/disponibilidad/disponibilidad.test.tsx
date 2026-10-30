import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { BloqueHorario, Disponibilidad, ValidacionDisponibilidad } from '../../../modulos/horarios/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const BLOQUES: BloqueHorario[] = [
  { id: 'l1', diaSemana: 'LUNES', horaInicio: '07:45', horaFin: '08:30' },
  { id: 'l2', diaSemana: 'LUNES', horaInicio: '10:15', horaFin: '11:00' },
  { id: 'm1', diaSemana: 'MARTES', horaInicio: '07:45', horaFin: '08:30' },
  { id: 'm2', diaSemana: 'MARTES', horaInicio: '10:15', horaFin: '11:00' },
]

const ABIERTO = {
  anio: 2027,
  configurado: true,
  fechaHoraInicio: '2027-01-04T08:00:00-05:00',
  fechaHoraFin: '2099-01-15T23:59:00-05:00',
  estado: 'HABILITADO' as const,
  abierto: true,
}

function disponibilidad(extra: Partial<Disponibilidad> = {}): Disponibilidad {
  return {
    anio: 2027,
    vinculado: true,
    vinculacionId: 'v1',
    docente: 'Rosa Quispe Mendoza',
    cargaSemanalReferencia: 3,
    cursos: [{ id: 'c1', nombre: 'Matemática' }],
    periodo: ABIERTO,
    bloques: BLOQUES,
    bloqueIds: ['l1'],
    observaciones: null,
    ultimaConfirmacion: '2027-01-10T16:12:00-05:00',
    validacion: null,
    ...extra,
  }
}

function validacionDe(ids: string[], carga = 3): ValidacionDisponibilidad {
  const faltan = Math.max(0, carga - ids.length)
  return {
    bloquesMarcados: ids.length,
    cargaSemanalReferencia: carga,
    faltan,
    suficiente: faltan === 0,
    mensaje: faltan === 0 ? null : `Tu disponibilidad podría ser insuficiente para tu carga horaria. Faltan al menos ${faltan} ${faltan === 1 ? 'bloque' : 'bloques'}.`,
  }
}

function simular(datos: Disponibilidad, confirmaciones: unknown[] = []) {
  servidor.use(
    http.get('*/api/v1/anios-escolares/2027/disponibilidad/mia', () => HttpResponse.json(datos)),
    http.post('*/api/v1/anios-escolares/2027/disponibilidad/mia/validacion', async ({ request }) => {
      const c = (await request.json()) as { bloqueIds: string[] }
      return HttpResponse.json(validacionDe(c.bloqueIds, datos.cargaSemanalReferencia))
    }),
    http.put('*/api/v1/anios-escolares/2027/disponibilidad/mia', async ({ request }) => {
      const c = (await request.json()) as { bloqueIds: string[]; observaciones: string }
      confirmaciones.push(c)
      return HttpResponse.json({
        ...datos,
        bloqueIds: c.bloqueIds,
        observaciones: c.observaciones || null,
        ultimaConfirmacion: '2027-01-12T09:30:00-05:00',
        validacion: validacionDe(c.bloqueIds, datos.cargaSemanalReferencia),
      })
    }),
  )
}

describe('Mi disponibilidad horaria (UC-20, RF-46)', () => {
  beforeEach(() => guardarSesion(sesionDe('DOCENTE', ['DISPONIBILIDAD_REGISTRAR', 'HORARIO_CONSULTAR'], { nombres: 'Rosa Quispe' })))

  it('muestra la matriz con lo ya registrado, el periodo abierto y la última confirmación', async () => {
    simular(disponibilidad())
    await renderizarRuta('/intranet/disponibilidad/2027')

    expect(await screen.findByText(/Periodo de registro abierto hasta 15\/01\/2099/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Lunes de 07:45 a 08:30: disponible' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Martes de 07:45 a 08:30: no disponible' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByText('3 horas semanales')).toBeInTheDocument()
    expect(screen.getByText(/Última versión confirmada: 10\/01\/2027/)).toBeInTheDocument()
    // El hueco entre las dos franjas es el recreo
    expect(screen.getByText('Recreo')).toBeInTheDocument()
  })

  it('FA-2: al marcar menos bloques que su carga advierte cuántos faltan, y deja de advertir al completarlos', async () => {
    simular(disponibilidad({ bloqueIds: [] }))
    await renderizarRuta('/intranet/disponibilidad/2027')

    await userEvent.click(await screen.findByRole('button', { name: 'Lunes de 07:45 a 08:30: no disponible' }))
    expect(await screen.findByText('Tu disponibilidad podría ser insuficiente para tu carga horaria. Faltan al menos 2 bloques.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Martes de 07:45 a 08:30: no disponible' }))
    expect(await screen.findByText(/Faltan al menos 1 bloque\./)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Martes de 10:15 a 11:00: no disponible' }))
    await screen.findByText('Bloques disponibles marcados')
    expect(screen.queryByText(/Tu disponibilidad podría ser insuficiente/)).not.toBeInTheDocument()
  })

  it('confirma los bloques y las observaciones y muestra lo que quedó registrado', async () => {
    const confirmaciones: unknown[] = []
    simular(disponibilidad({ bloqueIds: [] }), confirmaciones)
    await renderizarRuta('/intranet/disponibilidad/2027')

    await userEvent.click(await screen.findByRole('button', { name: 'Lunes de 07:45 a 08:30: no disponible' }))
    await userEvent.click(screen.getByRole('button', { name: 'Martes de 10:15 a 11:00: no disponible' }))
    await userEvent.type(screen.getByLabelText('Observaciones'), 'Los viernes dicto en otra institución')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar disponibilidad' }))

    expect(await screen.findByText('Se confirmó tu disponibilidad: 2 bloques disponibles.')).toBeInTheDocument()
    expect(confirmaciones).toEqual([{ bloqueIds: ['l1', 'm2'], observaciones: 'Los viernes dicto en otra institución' }])
    expect(screen.getByText(/Última versión confirmada: 12\/01\/2027/)).toBeInTheDocument()
    expect(screen.getByLabelText('Observaciones')).toHaveValue('Los viernes dicto en otra institución')
  })

  it('FE-1: con el periodo cerrado la matriz es de solo lectura y no se puede confirmar', async () => {
    simular(disponibilidad({ periodo: { ...ABIERTO, abierto: false, estado: 'CERRADO' } }))
    await renderizarRuta('/intranet/disponibilidad/2027')

    expect(await screen.findByText(/El periodo de registro está cerrado: ya no se permiten modificaciones/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Lunes de 07:45 a 08:30: disponible' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Confirmar disponibilidad' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Observaciones')).toBeDisabled()
  })

  it('si el servidor rechaza la confirmación por periodo cerrado, muestra su mensaje', async () => {
    simular(disponibilidad())
    servidor.use(
      http.put('*/api/v1/anios-escolares/2027/disponibilidad/mia', () =>
        problema(422, 'PERIODO_DISPONIBILIDAD_CERRADO', 'El periodo de registro de disponibilidad está cerrado: se mantiene tu última disponibilidad confirmada'),
      ),
    )
    await renderizarRuta('/intranet/disponibilidad/2027')

    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar disponibilidad' }))

    expect(await screen.findByText(/se mantiene tu última disponibilidad confirmada/)).toBeInTheDocument()
  })

  it('un docente no vinculado al año ve el aviso y no la matriz', async () => {
    simular(disponibilidad({ vinculado: false, vinculacionId: null, bloqueIds: [], cursos: [], cargaSemanalReferencia: 0 }))
    await renderizarRuta('/intranet/disponibilidad/2027')

    expect(await screen.findByText(/No estás habilitado como docente para el año 2027/)).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('exige DISPONIBILIDAD_REGISTRAR', async () => {
    guardarSesion(sesionDe('ALUMNO', ['HORARIO_CONSULTAR']))
    simular(disponibilidad())
    await renderizarRuta('/intranet/disponibilidad/2027')

    expect(screen.queryByRole('button', { name: 'Confirmar disponibilidad' })).not.toBeInTheDocument()
  })
})
