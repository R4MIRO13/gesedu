import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { guardarSesion } from '../../compartido/auth/sesion'
import type { AsistenciaHoy, RegistroAsistenciaDocente } from '../../modulos/asistencia/api'
import { RelojServidor } from '../../modulos/asistencia/componentes/RelojServidor'
import { problema, sesionDe } from '../../test/msw/handlers'
import { servidor } from '../../test/msw/servidor'
import { renderizarRuta } from '../../test/utilidades'

function hoy(extra: Partial<AsistenciaHoy> = {}): AsistenciaHoy {
  return {
    fecha: '2027-03-15',
    horaServidor: '2027-03-15T07:43:12-05:00',
    primerBloque: '07:45:00',
    limitePuntual: '07:50:00',
    toleranciaMinutos: 5,
    cierreJornada: '15:30:00',
    puedeMarcar: true,
    motivo: null,
    mensaje: null,
    registro: null,
    ...extra,
  }
}

const PUNTUAL: RegistroAsistenciaDocente = { id: 'r1', fecha: '2027-03-15', horaRegistro: '2027-03-15T07:44:30-05:00', estado: 'PUNTUAL' }

const HISTORIAL: RegistroAsistenciaDocente[] = [
  { id: 'h1', fecha: '2027-03-12', horaRegistro: '2027-03-12T07:41:00-05:00', estado: 'PUNTUAL' },
  { id: 'h2', fecha: '2027-03-11', horaRegistro: '2027-03-11T07:52:00-05:00', estado: 'TARDANZA' },
  { id: 'h3', fecha: '2027-03-10', horaRegistro: null, estado: 'FALTA' },
]

/** Simula el backend; `estado` cambia cuando se marca, como lo haría el servidor. */
function simular(inicial: AsistenciaHoy, opciones: { marcaciones?: Request[]; historial?: RegistroAsistenciaDocente[] } = {}) {
  let actual = inicial
  servidor.use(
    http.get('*/api/v1/asistencia-docente/hoy', () => HttpResponse.json(actual)),
    http.get('*/api/v1/asistencia-docente/mias', () => HttpResponse.json(opciones.historial ?? HISTORIAL)),
    http.post('*/api/v1/asistencia-docente/marcaciones', ({ request }) => {
      opciones.marcaciones?.push(request.clone())
      actual = hoy({ puedeMarcar: false, motivo: 'ASISTENCIA_YA_REGISTRADA', mensaje: 'Ya registraste tu asistencia de hoy.', registro: PUNTUAL })
      return HttpResponse.json(PUNTUAL, { status: 201 })
    }),
  )
}

describe('Mi asistencia (UC-07, RF-18 a RF-20)', () => {
  beforeEach(() => guardarSesion(sesionDe('DOCENTE', ['ASISTENCIA_DOCENTE_MARCAR', 'HORARIO_CONSULTAR'], { nombres: 'Rosa Quispe' })))

  it('muestra el día, la hora del servidor, la primera clase, la tolerancia y el límite puntual', async () => {
    simular(hoy())
    await renderizarRuta('/intranet/mi-asistencia')

    expect(await screen.findByText('Lunes 15/03/2027')).toBeInTheDocument()
    expect(screen.getByLabelText('Hora del servidor')).toHaveTextContent('7:43:12 a. m.')
    expect(screen.getByText('7:45 a. m.')).toBeInTheDocument()
    expect(screen.getByText(/Tolerancia para docentes: 5 minutos · eres puntual hasta las 7:50 a. m\./)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Registrar asistencia' })).toBeEnabled()
  })

  it('al registrar manda la petición sin hora ni fecha y muestra el resultado del servidor', async () => {
    const marcaciones: Request[] = []
    simular(hoy(), { marcaciones })
    await renderizarRuta('/intranet/mi-asistencia')

    await userEvent.click(await screen.findByRole('button', { name: 'Registrar asistencia' }))

    expect(await screen.findByText(/Asistencia registrada a las 7:44 a\. m\.: Puntual\./)).toBeInTheDocument()
    expect(marcaciones).toHaveLength(1)
    expect(await marcaciones[0]!.text()).toBe('')
    // Tras registrar, la pantalla se actualiza: ya no se puede marcar otra vez
    await waitFor(() => expect(screen.getByRole('button', { name: 'Registrar asistencia' })).toBeDisabled())
    // El resultado se dice una sola vez: no se repite como «Hoy quedó registrada…»
    expect(screen.getAllByText(/7:44 a\. m\./).length).toBeGreaterThan(0)
    expect(screen.queryByText(/Hoy quedó registrada tu asistencia/)).not.toBeInTheDocument()
  })

  it('FA-1: si ya estaba registrada muestra el estado existente y no deja marcar', async () => {
    simular(hoy({ puedeMarcar: false, motivo: 'ASISTENCIA_YA_REGISTRADA', mensaje: 'Ya registraste tu asistencia de hoy.', registro: PUNTUAL }))
    await renderizarRuta('/intranet/mi-asistencia')

    expect(await screen.findByText(/Hoy quedó registrada tu asistencia a las 7:44 a\. m\./)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Registrar asistencia' })).toBeDisabled()
  })

  it('FA-1: si otra solicitud ganó la carrera (409) muestra el aviso y la pantalla se actualiza', async () => {
    let marcada = false
    servidor.use(
      http.get('*/api/v1/asistencia-docente/hoy', () =>
        HttpResponse.json(marcada ? hoy({ puedeMarcar: false, motivo: 'ASISTENCIA_YA_REGISTRADA', registro: PUNTUAL }) : hoy()),
      ),
      http.get('*/api/v1/asistencia-docente/mias', () => HttpResponse.json([])),
      http.post('*/api/v1/asistencia-docente/marcaciones', () => {
        marcada = true
        return problema(409, 'ASISTENCIA_YA_REGISTRADA', 'Ya registraste tu asistencia de hoy.')
      }),
    )
    await renderizarRuta('/intranet/mi-asistencia')

    await userEvent.click(await screen.findByRole('button', { name: 'Registrar asistencia' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ya registraste tu asistencia de hoy.')
    expect(await screen.findByText(/Hoy quedó registrada tu asistencia/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Registrar asistencia' })).toBeDisabled()
  })

  it.each([
    ['DIA_NO_LECTIVO', 'Hoy no es un día lectivo: no se registra asistencia.'],
    ['JORNADA_CERRADA', 'La jornada cerró a las 15:30: ya no se puede marcar la asistencia.'],
    ['SIN_CLASES_PROGRAMADAS', 'No tienes clases programadas hoy: no se registra asistencia.'],
  ] as const)('FA-2 y FA-3: con %s explica el motivo y no deja marcar', async (motivo, mensaje) => {
    simular(hoy({ puedeMarcar: false, motivo, mensaje, primerBloque: null, limitePuntual: null }))
    await renderizarRuta('/intranet/mi-asistencia')

    expect(await screen.findByText(mensaje)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Registrar asistencia' })).toBeDisabled()
    expect(screen.queryByText(/Tu primera clase hoy/)).not.toBeInTheDocument()
  })

  it('lista hoy («Sin registrar») y los últimos días con su estado; la falta del cierre lo indica', async () => {
    simular(hoy())
    await renderizarRuta('/intranet/mi-asistencia')

    const tabla = await screen.findByRole('table', { name: 'Últimos días de asistencia' })
    // Intl separa la hora de «a. m.» con un espacio fino: se normaliza para comparar
    const filas = Array.from(tabla.querySelectorAll('tbody tr')).map((f) => (f.textContent ?? '').replace(/\s/g, ' '))
    expect(filas).toHaveLength(4)
    expect(filas[0]).toContain('Lun 15/03 (hoy)')
    expect(filas[0]).toContain('Sin registrar')
    expect(filas[1]).toContain('Vie 12/03')
    expect(filas[1]).toContain('7:41 a. m.')
    expect(filas[1]).toContain('Puntual')
    expect(filas[2]).toContain('Tardanza')
    expect(filas[3]).toContain('Falta')
    expect(filas[3]).toContain('Generado al cierre de jornada')
  })

  it('muestra como mucho cinco días, con hoy incluido', async () => {
    const muchos: RegistroAsistenciaDocente[] = Array.from({ length: 8 }, (_, i) => ({
      id: `m${i}`,
      fecha: `2027-03-${String(12 - i).padStart(2, '0')}`,
      horaRegistro: `2027-03-${String(12 - i).padStart(2, '0')}T07:40:00-05:00`,
      estado: 'PUNTUAL',
    }))
    simular(hoy({ registro: PUNTUAL, puedeMarcar: false, motivo: 'ASISTENCIA_YA_REGISTRADA' }), { historial: [PUNTUAL, ...muchos] })
    await renderizarRuta('/intranet/mi-asistencia')

    const tabla = await screen.findByRole('table', { name: 'Últimos días de asistencia' })
    expect(tabla.querySelectorAll('tbody tr')).toHaveLength(5)
  })

  it('un usuario sin ASISTENCIA_DOCENTE_MARCAR no entra', async () => {
    guardarSesion(sesionDe('ALUMNO', ['HORARIO_CONSULTAR']))
    simular(hoy())
    await renderizarRuta('/intranet/mi-asistencia')

    expect(screen.queryByRole('button', { name: 'Registrar asistencia' })).not.toBeInTheDocument()
  })

  it('el menú ofrece «Mi asistencia» al docente', async () => {
    simular(hoy())
    await renderizarRuta('/intranet/inicio')

    const enlaces = await screen.findAllByRole('link', { name: /Mi asistencia/ })
    expect(enlaces.length).toBeGreaterThan(0)
    for (const enlace of enlaces) expect(enlace).toHaveAttribute('href', '/intranet/mi-asistencia')
  })
})

describe('RelojServidor', () => {
  beforeEach(() => vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'performance'] }))
  afterEach(() => vi.useRealTimers())

  it('avanza segundo a segundo a partir de la hora del servidor, no de la del dispositivo', () => {
    render(<RelojServidor horaServidor="2027-03-15T07:43:12-05:00" />)
    expect(screen.getByLabelText('Hora del servidor')).toHaveTextContent('7:43:12 a. m.')

    act(() => {
      vi.advanceTimersByTime(3000)
    })

    expect(screen.getByLabelText('Hora del servidor')).toHaveTextContent('7:43:15 a. m.')
  })
})
