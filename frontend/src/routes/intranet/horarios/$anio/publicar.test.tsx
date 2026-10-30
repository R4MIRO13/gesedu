import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../../compartido/auth/sesion'
import type { Completitud, Publicacion, SeccionCompletitud } from '../../../../modulos/horarios/api'
import { problema, sesionDe } from '../../../../test/msw/handlers'
import { servidor } from '../../../../test/msw/servidor'
import { renderizarRuta } from '../../../../test/utilidades'

const SESION = sesionDe('ADMINISTRADOR', ['HORARIO_GESTIONAR', 'HORARIO_CONSULTAR'], { cargo: 'Secretaría académica' })

function completa(id: string, nombre: string): SeccionCompletitud {
  return { seccionId: id, seccion: nombre, horasAsignadas: 22, horasRequeridas: 22, estado: 'COMPLETO', faltan: 0, sobran: 0, detalle: [] }
}

const SECCIONES_CON_OBSERVACIONES: SeccionCompletitud[] = [
  completa('1a', '1° A'),
  {
    seccionId: '3a',
    seccion: '3° A',
    horasAsignadas: 19,
    horasRequeridas: 22,
    estado: 'FALTAN',
    faltan: 3,
    sobran: 0,
    detalle: [
      { cursoId: 'ing', curso: 'Inglés', asignadas: 2, requeridas: 3 },
      { cursoId: 'ef', curso: 'Educación Física', asignadas: 0, requeridas: 2 },
    ],
  },
  {
    seccionId: '4b',
    seccion: '4° B',
    horasAsignadas: 23,
    horasRequeridas: 22,
    estado: 'EXCEDE',
    faltan: 0,
    sobran: 1,
    detalle: [{ cursoId: 'ef', curso: 'Educación Física', asignadas: 3, requeridas: 2 }],
  },
]

function completitud(extra: Partial<Completitud> = {}): Completitud {
  return {
    anio: 2027,
    versionBorrador: 7,
    secciones: SECCIONES_CON_OBSERVACIONES,
    completas: 1,
    conObservaciones: 2,
    publicable: false,
    advertencias: [],
    vigente: null,
    sinCambios: false,
    ...extra,
  }
}

const COMPLETAS = [completa('1a', '1° A'), completa('3a', '3° A')]

function publicacion(numero: number, vigente = true): Publicacion {
  return { id: `p${numero}`, numeroVersion: numero, fechaHoraPublicacion: '2027-02-10T09:30:00-05:00', vigente, publicadoPor: 'Carmen Salas Paredes', clases: 44 }
}

function simular(datos: Completitud, versiones: Publicacion[] = []) {
  servidor.use(
    http.get('*/api/v1/horarios/2027/borrador/completitud', () => HttpResponse.json(datos)),
    http.get('*/api/v1/horarios/2027/publicaciones', () => HttpResponse.json(versiones)),
  )
}

describe('Publicar horario (UC-22, RF-43, RF-44)', () => {
  beforeEach(() => guardarSesion(SESION))

  it('FA-1: muestra cada sección con su estado y su detalle, avisa de las observaciones y no deja publicar', async () => {
    simular(completitud())
    await renderizarRuta('/intranet/horarios/2027/publicar')

    const tabla = await screen.findByRole('table', { name: 'Completitud por sección' })
    expect(within(tabla).getByRole('row', { name: /3° A/ })).toHaveTextContent('Faltan 3 h')
    expect(within(tabla).getByRole('row', { name: /3° A/ })).toHaveTextContent('Inglés 2/3 h · Educación Física 0/2 h')
    expect(within(tabla).getByRole('row', { name: /4° B/ })).toHaveTextContent('Excede 1 h')
    expect(within(tabla).getByRole('row', { name: /1° A/ })).toHaveTextContent('Completo')
    expect(screen.getByText(/Hay 2 secciones con observaciones\. Corrígelas antes de publicar\. \(3° A y 4° B\)/)).toBeInTheDocument()
    expect(screen.getByText('Completas 1')).toBeInTheDocument()
    expect(screen.getByText('Con observaciones 2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Publicar horario' })).toBeDisabled()
    expect(screen.getByRole('link', { name: 'Volver a elaborar horario' })).toHaveAttribute('href', expect.stringContaining('/intranet/horarios/2027/elaborar'))
  })

  it('con todo completo pide confirmación y publica con la versión del borrador revisado', async () => {
    const recibido: (string | null)[] = []
    simular(completitud({ secciones: COMPLETAS, completas: 2, conObservaciones: 0, publicable: true }))
    servidor.use(
      http.post('*/api/v1/horarios/2027/publicaciones', ({ request }) => {
        recibido.push(request.headers.get('If-Match'))
        return HttpResponse.json(publicacion(1), { status: 201 })
      }),
    )
    await renderizarRuta('/intranet/horarios/2027/publicar')

    await userEvent.click(await screen.findByRole('button', { name: 'Publicar horario' }))
    const dialogo = screen.getByRole('alertdialog', { name: 'Confirmar publicación' })
    expect(dialogo).toHaveTextContent('La versión 1 pasará a ser la vigente')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar publicación' }))

    expect(await screen.findByText(/Horario publicado: la versión 1 es la vigente desde 10\/02\/2027/)).toBeInTheDocument()
    expect(recibido).toEqual(['"7"'])
  })

  it('FE-1: cancelar la publicación no manda nada', async () => {
    let publicaciones = 0
    simular(completitud({ secciones: COMPLETAS, completas: 2, conObservaciones: 0, publicable: true }))
    servidor.use(
      http.post('*/api/v1/horarios/2027/publicaciones', () => {
        publicaciones++
        return HttpResponse.json(publicacion(1), { status: 201 })
      }),
    )
    await renderizarRuta('/intranet/horarios/2027/publicar')

    await userEvent.click(await screen.findByRole('button', { name: 'Publicar horario' }))
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Publicar horario' })).toBeEnabled()
    expect(publicaciones).toBe(0)
  })

  it('al publicar una nueva versión avisa que la anterior deja de ser la vigente y lista las versiones', async () => {
    simular(
      completitud({ secciones: COMPLETAS, completas: 2, conObservaciones: 0, publicable: true, vigente: publicacion(1) }),
      [publicacion(1)],
    )
    await renderizarRuta('/intranet/horarios/2027/publicar')

    await userEvent.click(await screen.findByRole('button', { name: 'Publicar horario' }))
    expect(screen.getByRole('alertdialog')).toHaveTextContent('La versión 2 pasará a ser la vigente y la versión 1 dejará de serlo')
    const versiones = screen.getByRole('table', { name: 'Versiones publicadas del horario' })
    expect(versiones).toHaveTextContent('Vigente')
    expect(versiones).toHaveTextContent('Carmen Salas Paredes')
  })

  it('si el borrador cambió mientras se revisaba (409) lo explica y vuelve a calcular la validación', async () => {
    let lecturas = 0
    servidor.use(
      http.get('*/api/v1/horarios/2027/borrador/completitud', () => {
        lecturas++
        return HttpResponse.json(completitud({ secciones: COMPLETAS, completas: 2, conObservaciones: 0, publicable: true, versionBorrador: 7 + lecturas }))
      }),
      http.get('*/api/v1/horarios/2027/publicaciones', () => HttpResponse.json([])),
      http.post('*/api/v1/horarios/2027/publicaciones', () => problema(409, 'CONFLICTO_CONCURRENCIA', 'El recurso cambió; recarga e inténtalo de nuevo')),
    )
    await renderizarRuta('/intranet/horarios/2027/publicar')

    await userEvent.click(await screen.findByRole('button', { name: 'Publicar horario' }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar publicación' }))

    expect(await screen.findByText(/El borrador cambió desde que lo revisaste/)).toBeInTheDocument()
    expect(lecturas).toBeGreaterThanOrEqual(2)
  })

  it('muestra las advertencias de disponibilidad sin bloquear la publicación (C-40)', async () => {
    simular(
      completitud({
        secciones: COMPLETAS,
        completas: 2,
        conObservaciones: 0,
        publicable: true,
        advertencias: [{ tipo: 'DISPONIBILIDAD_SIN_CONFIRMAR', mensaje: '1 docente habilitado no confirmó su disponibilidad: Marta Ibáñez Ruiz.', docentes: ['Marta Ibáñez Ruiz'] }],
      }),
    )
    await renderizarRuta('/intranet/horarios/2027/publicar')

    expect(await screen.findByText(/Marta Ibáñez Ruiz\. No impide publicar\./)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Publicar horario' })).toBeEnabled()
  })

  it('avisa si el borrador es idéntico a la versión vigente', async () => {
    simular(completitud({ secciones: COMPLETAS, completas: 2, conObservaciones: 0, publicable: true, vigente: publicacion(3), sinCambios: true }))
    await renderizarRuta('/intranet/horarios/2027/publicar')

    expect(await screen.findByText(/El borrador es igual a la versión 3 vigente/)).toBeInTheDocument()
  })

  it('exige HORARIO_GESTIONAR', async () => {
    guardarSesion(sesionDe('DOCENTE', ['HORARIO_CONSULTAR']))
    simular(completitud())
    await renderizarRuta('/intranet/horarios/2027/publicar')

    expect(screen.queryByRole('button', { name: 'Publicar horario' })).not.toBeInTheDocument()
  })
})
