import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../../compartido/auth/sesion'
import { problema } from '../../../../test/msw/handlers'
import {
  borradorEjemplo,
  OPCIONES_MATEMATICA,
  OPCIONES_SIN_CURSO,
  SESION_GESTOR,
} from '../../../../test/msw/horario'
import { servidor } from '../../../../test/msw/servidor'
import { renderizarRuta } from '../../../../test/utilidades'

const RUTA = '/intranet/horarios/2027/elaborar?perspectiva=SECCION'

interface Peticion {
  ruta: string
  ifMatch: string | null
  cuerpo: unknown
}

/** Simula el backend de UC-21 y guarda las peticiones de escritura para revisarlas. */
function simularBackend(escrituras: Peticion[] = []) {
  servidor.use(
    http.get('*/api/v1/horarios/2027/borrador', ({ request }) => {
      const perspectiva = new URL(request.url).searchParams.get('perspectiva')
      return HttpResponse.json(borradorEjemplo({ perspectiva: perspectiva === 'DOCENTE' ? 'DOCENTE' : 'SECCION',
        ...(perspectiva === 'DOCENTE' ? { seccionId: null, docenteId: 'd-fernando', carga: null } : {}) }))
    }),
    http.get('*/api/v1/horarios/2027/borrador/opciones', ({ request }) => {
      const cursoId = new URL(request.url).searchParams.get('cursoId')
      return HttpResponse.json(cursoId ? OPCIONES_MATEMATICA : OPCIONES_SIN_CURSO)
    }),
    http.post('*/api/v1/horarios/2027/borrador/validaciones', async ({ request }) => {
      const { docenteId } = (await request.json()) as { docenteId: string }
      if (docenteId === 'd-rosa') {
        return HttpResponse.json({
          valido: false,
          conflictos: [
            {
              tipo: 'CRUCE_DOCENTE',
              mensaje: 'Prof. R. Quispe ya tiene clase en 4° B el martes de 9:15 a 10:00',
              asignacionId: 'a-9',
              seccionId: 's-4b',
              docenteId: 'd-rosa',
            },
          ],
        })
      }
      if (docenteId === 'd-carlos') {
        return HttpResponse.json({
          valido: false,
          conflictos: [
            { tipo: 'DOCENTE_NO_DISPONIBLE', mensaje: 'Prof. C. Ríos no está disponible el martes de 9:15 a 10:00',
              asignacionId: null, seccionId: null, docenteId: null },
          ],
        })
      }
      return HttpResponse.json({ valido: true, conflictos: [] })
    }),
    http.post('*/api/v1/horarios/2027/borrador/asignaciones', async ({ request }) => {
      const cuerpo = await request.json()
      escrituras.push({ ruta: 'POST', ifMatch: request.headers.get('If-Match'), cuerpo })
      const { docenteId } = cuerpo as { docenteId: string }
      if (docenteId === 'd-rosa') {
        return problema(409, 'CONFLICTO_HORARIO', 'Prof. R. Quispe ya tiene clase en 4° B el martes de 9:15 a 10:00', {
          conflictos: [{ tipo: 'CRUCE_DOCENTE', mensaje: 'Prof. R. Quispe ya tiene clase en 4° B el martes de 9:15 a 10:00',
            asignacionId: 'a-9', seccionId: 's-4b', docenteId: 'd-rosa' }],
        })
      }
      return HttpResponse.json({ id: 'a-2' }, { status: 201, headers: { ETag: '"4"' } })
    }),
  )
}

describe('Elaborar horario (UC-21)', () => {
  beforeEach(() => guardarSesion(SESION_GESTOR))

  it('muestra la matriz del borrador con la clase de la sección y su carga horaria', async () => {
    simularBackend()
    await renderizarRuta(RUTA)

    expect(await screen.findByRole('heading', { name: 'Elaborar horario 2027' })).toBeInTheDocument()
    const tabla = await screen.findByRole('table', { name: 'Horario semanal' })
    expect(screen.getByText('Borrador · versión 3')).toBeInTheDocument()
    const celda = within(tabla).getByRole('button', { name: 'Lunes 07:45 a 08:30' })
    expect(within(celda).getByText('Matemática')).toBeInTheDocument()
    expect(within(celda).getByText('Fernando Medina Soto')).toBeInTheDocument()
    expect(within(tabla).getByRole('button', { name: 'Martes 09:15 a 10:00' })).toHaveTextContent('Libre')

    const carga = screen.getByRole('region', { name: 'Carga horaria de la sección' })
    expect(within(carga).getByText('1 de 8')).toBeInTheDocument()
    expect(within(carga).getByText('1 / 5 h')).toBeInTheDocument()
  })

  it('el panel marca en vivo a los docentes disponibles, ocupados y no disponibles (RF-47)', async () => {
    simularBackend()
    await renderizarRuta(RUTA)
    const tabla = await screen.findByRole('table', { name: 'Horario semanal' })

    await userEvent.click(within(tabla).getByRole('button', { name: 'Martes 09:15 a 10:00' }))
    const panel = await screen.findByRole('region', { name: 'Asignación de clase' })
    await userEvent.click(await within(panel).findByRole('radio', { name: /Matemática/ }))

    expect(await within(panel).findByText('tiene clase en 4° B el martes de 9:15 a 10:00')).toBeInTheDocument()
    expect(within(panel).getByText('No disponible')).toBeInTheDocument()
    expect(within(panel).getByText('Disponible')).toBeInTheDocument()
    expect(within(panel).getByRole('radio', { name: /Matemática/ })).toBeChecked()
  })

  it('al elegir un docente con cruce muestra el conflicto en vivo y el guardado se rechaza con la causa (FE-2)', async () => {
    const escrituras: Peticion[] = []
    simularBackend(escrituras)
    await renderizarRuta(RUTA)
    const tabla = await screen.findByRole('table', { name: 'Horario semanal' })
    await userEvent.click(within(tabla).getByRole('button', { name: 'Martes 09:15 a 10:00' }))
    const panel = await screen.findByRole('region', { name: 'Asignación de clase' })
    await userEvent.click(await within(panel).findByRole('radio', { name: /Matemática/ }))

    await userEvent.click(await within(panel).findByRole('radio', { name: /Rosa Quispe/ }))

    const aviso = await within(panel).findByRole('alert')
    expect(aviso).toHaveTextContent('Prof. R. Quispe ya tiene clase en 4° B el martes de 9:15 a 10:00')

    await userEvent.click(within(panel).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(escrituras).toHaveLength(1))
    expect(escrituras[0]?.ifMatch).toBe('"3"')
    expect(await within(panel).findByRole('alert')).toHaveTextContent('Prof. R. Quispe ya tiene clase en 4° B')
    // El panel sigue abierto para elegir otro docente
    expect(within(panel).getByRole('button', { name: 'Guardar' })).toBeEnabled()
  })

  it('un docente no disponible también se avisa en vivo (FE-1)', async () => {
    simularBackend()
    await renderizarRuta(RUTA)
    const tabla = await screen.findByRole('table', { name: 'Horario semanal' })
    await userEvent.click(within(tabla).getByRole('button', { name: 'Martes 09:15 a 10:00' }))
    const panel = await screen.findByRole('region', { name: 'Asignación de clase' })
    await userEvent.click(await within(panel).findByRole('radio', { name: /Matemática/ }))

    await userEvent.click(await within(panel).findByRole('radio', { name: /Carlos Ríos/ }))

    expect(await within(panel).findByRole('alert')).toHaveTextContent(
      'Prof. C. Ríos no está disponible el martes de 9:15 a 10:00',
    )
  })

  it('con un docente libre guarda enviando la versión en If-Match y cierra el panel', async () => {
    const escrituras: Peticion[] = []
    simularBackend(escrituras)
    await renderizarRuta(RUTA)
    const tabla = await screen.findByRole('table', { name: 'Horario semanal' })
    await userEvent.click(within(tabla).getByRole('button', { name: 'Martes 09:15 a 10:00' }))
    const panel = await screen.findByRole('region', { name: 'Asignación de clase' })
    await userEvent.click(await within(panel).findByRole('radio', { name: /Matemática/ }))
    await userEvent.click(await within(panel).findByRole('radio', { name: /Fernando Medina/ }))
    expect(await within(panel).findByText('Sin conflictos: se puede guardar.')).toBeInTheDocument()

    await userEvent.click(within(panel).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(escrituras).toHaveLength(1))
    expect(escrituras[0]).toEqual({
      ruta: 'POST',
      ifMatch: '"3"',
      cuerpo: { bloqueId: 'b-mar1', seccionId: 's-3a', cursoId: 'c-mat', docenteId: 'd-fernando' },
    })
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Asignación de clase' })).not.toBeInTheDocument())
  })

  it('si otra persona cambió el borrador (409 por versión) pide recargar la matriz', async () => {
    simularBackend()
    servidor.use(
      http.post('*/api/v1/horarios/2027/borrador/asignaciones', () =>
        problema(409, 'CONFLICTO_CONCURRENCIA', 'El recurso cambió; recarga e inténtalo de nuevo'),
      ),
    )
    await renderizarRuta(RUTA)
    const tabla = await screen.findByRole('table', { name: 'Horario semanal' })
    await userEvent.click(within(tabla).getByRole('button', { name: 'Martes 09:15 a 10:00' }))
    const panel = await screen.findByRole('region', { name: 'Asignación de clase' })
    await userEvent.click(await within(panel).findByRole('radio', { name: /Matemática/ }))
    await userEvent.click(await within(panel).findByRole('radio', { name: /Fernando Medina/ }))

    await userEvent.click(within(panel).getByRole('button', { name: 'Guardar' }))

    expect(await within(panel).findByRole('alert')).toHaveTextContent('El horario cambió mientras lo editabas')
    await userEvent.click(within(panel).getByRole('button', { name: 'Recargar matriz' }))
    expect(within(panel).queryByRole('button', { name: 'Recargar matriz' })).not.toBeInTheDocument()
  })

  it('modifica o elimina una clase existente con If-Match', async () => {
    const escrituras: Peticion[] = []
    simularBackend(escrituras)
    servidor.use(
      http.delete('*/api/v1/horarios/2027/borrador/asignaciones/a-1', ({ request }) => {
        escrituras.push({ ruta: 'DELETE', ifMatch: request.headers.get('If-Match'), cuerpo: null })
        return new HttpResponse(null, { status: 204, headers: { ETag: '"4"' } })
      }),
    )
    await renderizarRuta(RUTA)
    const tabla = await screen.findByRole('table', { name: 'Horario semanal' })
    await userEvent.click(within(tabla).getByRole('button', { name: 'Lunes 07:45 a 08:30' }))
    const panel = await screen.findByRole('region', { name: 'Asignación de clase' })
    expect(within(panel).getByRole('heading', { name: 'Modificar clase · 3° A' })).toBeInTheDocument()

    await userEvent.click(within(panel).getByRole('button', { name: 'Eliminar clase' }))

    await waitFor(() => expect(escrituras).toEqual([{ ruta: 'DELETE', ifMatch: '"3"', cuerpo: null }]))
  })

  it('la perspectiva Docente muestra el mismo borrador desde el docente (FA-3)', async () => {
    simularBackend()
    await renderizarRuta(RUTA)
    await screen.findByRole('table', { name: 'Horario semanal' })

    await userEvent.click(screen.getByRole('radio', { name: 'Docente' }))

    const tabla = await screen.findByRole('table', { name: 'Horario semanal' })
    await waitFor(() => expect(screen.getByRole('radio', { name: 'Docente' })).toHaveAttribute('aria-checked', 'true'))
    // Desde el docente la celda muestra la sección y el curso
    expect(await within(tabla).findByText('3° A')).toBeInTheDocument()
    expect(screen.getByText('Borrador · versión 3')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Carga horaria de la sección' })).not.toBeInTheDocument()
  })

  it('quien no gestiona horarios no entra a la pantalla', async () => {
    const { sesionDe } = await import('../../../../test/msw/handlers')
    guardarSesion(sesionDe('ALUMNO', ['HORARIO_CONSULTAR']))
    const { router } = await renderizarRuta(RUTA)

    await waitFor(() => expect(router.state.location.pathname).toBe('/intranet/inicio'))
  })
})
