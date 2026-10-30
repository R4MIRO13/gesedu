import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../compartido/auth/sesion'
import type { HorarioVigente } from '../../modulos/horarios/api'
import { problema, sesionDe } from '../../test/msw/handlers'
import { servidor } from '../../test/msw/servidor'
import { renderizarRuta } from '../../test/utilidades'

const BLOQUES: HorarioVigente['bloques'] = [
  { id: 'l1', diaSemana: 'LUNES', horaInicio: '07:45', horaFin: '08:30' },
  { id: 'l2', diaSemana: 'LUNES', horaInicio: '08:30', horaFin: '09:15' },
  { id: 'm1', diaSemana: 'MARTES', horaInicio: '07:45', horaFin: '08:30' },
]

function horario(extra: Partial<HorarioVigente> = {}): HorarioVigente {
  return {
    anio: 2027,
    numeroVersion: 2,
    vigenteDesde: '2027-02-10T09:30:00-05:00',
    perspectiva: 'SECCION',
    perspectivas: ['SECCION'],
    seccionId: 's3a',
    docenteId: null,
    bloques: BLOQUES,
    secciones: [{ id: 's3a', nombre: '3° A' }],
    docentes: [],
    asignaciones: [
      { id: 'a1', bloqueId: 'l1', seccionId: 's3a', seccion: '3° A', cursoId: 'mat', curso: 'Matemática', docenteId: 'd1', docente: 'Rosa Quispe Mendoza' },
      { id: 'a2', bloqueId: 'm1', seccionId: 's3a', seccion: '3° A', cursoId: 'ing', curso: 'Inglés', docenteId: 'd2', docente: 'Carmen Villanueva Paz' },
    ],
    ...extra,
  }
}

describe('Horario publicado (UC-23, RF-39, RF-45)', () => {
  beforeEach(() => guardarSesion(sesionDe('ALUMNO', ['HORARIO_CONSULTAR'], { nombres: 'Lucía García' })))

  it('el alumno ve su sección con la versión vigente y el detalle de un bloque, sin poder cambiar nada', async () => {
    servidor.use(http.get('*/api/v1/horarios/2027/vigente', () => HttpResponse.json(horario())))
    await renderizarRuta('/intranet/horario')

    expect(await screen.findByText(/Versión 2 publicada · vigente desde 10\/02\/2027/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Mi horario · 3° A · 2027/ })).toBeInTheDocument()
    expect(screen.queryByRole('tablist', { name: 'Perspectiva' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Sección')).not.toBeInTheDocument()

    await userEvent.click(screen.getAllByRole('button', { name: 'Lunes 07:45 a 08:30' })[0]!)
    const detalle = screen.getByRole('region', { name: 'Detalle del bloque' })
    expect(within(detalle).getByText('Matemática')).toBeInTheDocument()
    expect(within(detalle).getByText('Rosa Quispe Mendoza')).toBeInTheDocument()
    expect(within(detalle).getByText('Lunes')).toBeInTheDocument()
    expect(within(detalle).getByText('07:45 – 08:30')).toBeInTheDocument()
  })

  it('un bloque sin clase se muestra libre', async () => {
    servidor.use(http.get('*/api/v1/horarios/2027/vigente', () => HttpResponse.json(horario())))
    await renderizarRuta('/intranet/horario')

    await userEvent.click((await screen.findAllByRole('button', { name: 'Lunes 08:30 a 09:15' }))[0]!)

    expect(screen.getByText(/sin clase/)).toBeInTheDocument()
  })

  it('FA-1: sin horario publicado dice que aún no está disponible', async () => {
    servidor.use(http.get('*/api/v1/horarios/2027/vigente', () => problema(404, 'HORARIO_NO_PUBLICADO', 'El horario 2027 aún no ha sido publicado')))
    await renderizarRuta('/intranet/horario')

    expect(await screen.findByText(/El horario aún no se encuentra disponible/)).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('el apoderado con dos hijos elige la sección y la consulta se repite con ella', async () => {
    guardarSesion(sesionDe('APODERADO', ['HORARIO_CONSULTAR']))
    const pedidos: (string | null)[] = []
    servidor.use(
      http.get('*/api/v1/horarios/2027/vigente', ({ request }) => {
        const seccion = new URL(request.url).searchParams.get('seccionId')
        pedidos.push(seccion)
        const dos = [
          { id: 's3a', nombre: '3° A · Lucía' },
          { id: 's3b', nombre: '3° B · Andrés' },
        ]
        return HttpResponse.json(
          horario({ secciones: dos, seccionId: seccion ?? 's3a', asignaciones: seccion === 's3b' ? [] : horario().asignaciones }),
        )
      }),
    )
    await renderizarRuta('/intranet/horario')

    const selector = await screen.findByLabelText('Sección')
    await userEvent.selectOptions(selector, 's3b')

    expect(await screen.findByRole('heading', { name: /3° B · Andrés/ })).toBeInTheDocument()
    expect(pedidos).toEqual([null, 's3b'])
  })

  it('un apoderado sin hijos matriculados no ve una matriz vacía', async () => {
    guardarSesion(sesionDe('APODERADO', ['HORARIO_CONSULTAR']))
    servidor.use(http.get('*/api/v1/horarios/2027/vigente', () => HttpResponse.json(horario({ secciones: [], seccionId: null, asignaciones: [] }))))
    await renderizarRuta('/intranet/horario')

    expect(await screen.findByText(/No tienes una sección con matrícula vigente/)).toBeInTheDocument()
  })

  it('el administrador cambia de perspectiva y elige un docente', async () => {
    guardarSesion(sesionDe('ADMINISTRADOR', ['HORARIO_CONSULTAR', 'HORARIO_GESTIONAR']))
    const pedidos: string[] = []
    servidor.use(
      http.get('*/api/v1/horarios/2027/vigente', ({ request }) => {
        const url = new URL(request.url)
        pedidos.push(url.search)
        const docente = url.searchParams.get('perspectiva') === 'DOCENTE'
        return HttpResponse.json(
          horario({
            perspectivas: ['SECCION', 'DOCENTE'],
            perspectiva: docente ? 'DOCENTE' : 'SECCION',
            docenteId: docente ? 'd1' : null,
            docentes: [
              { id: 'd1', nombre: 'Rosa Quispe Mendoza' },
              { id: 'd2', nombre: 'Carmen Villanueva Paz' },
            ],
            secciones: [{ id: 's3a', nombre: '3° A' }, { id: 's3b', nombre: '3° B' }],
          }),
        )
      }),
    )
    await renderizarRuta('/intranet/horario')

    await userEvent.click(await screen.findByRole('tab', { name: 'Por docente' }))

    expect(await screen.findByLabelText('Docente')).toHaveValue('d1')
    expect(pedidos.at(-1)).toContain('perspectiva=DOCENTE')
  })

  it('exige HORARIO_CONSULTAR', async () => {
    guardarSesion(sesionDe('ALUMNO', []))
    servidor.use(http.get('*/api/v1/horarios/2027/vigente', () => HttpResponse.json(horario())))
    await renderizarRuta('/intranet/horario')

    expect(screen.queryByText(/Versión 2 publicada/)).not.toBeInTheDocument()
  })
})
