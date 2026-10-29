import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { guardarSesion, obtenerSesion } from '../auth/sesion'
import { SESION_ADMIN, SESION_ALUMNO } from '../../test/msw/handlers'
import { servidor } from '../../test/msw/servidor'
import { renderizarRuta } from '../../test/utilidades'

describe('Layout de la intranet', () => {
  it('muestra en el menú solo las opciones permitidas por los permisos de la sesión (RF-50)', async () => {
    guardarSesion(SESION_ALUMNO)
    await renderizarRuta('/intranet/inicio')

    const menu = await screen.findByRole('navigation', { name: 'Principal' })
    expect(within(menu).getByRole('link', { name: 'Inicio' })).toBeInTheDocument()
    expect(within(menu).getByRole('link', { name: 'Bloques horarios' })).toBeInTheDocument()
    expect(within(menu).queryByRole('link', { name: 'Auditoría' })).not.toBeInTheDocument()
    // La misma navegación está en la barra inferior de móvil
    const movil = screen.getByRole('navigation', { name: 'Navegación móvil' })
    expect(within(movil).getByRole('link', { name: 'Bloques horarios' })).toBeInTheDocument()
  })

  it('el administrador ve también Auditoría y su cargo', async () => {
    guardarSesion(SESION_ADMIN)
    await renderizarRuta('/intranet/inicio')

    const menu = await screen.findByRole('navigation', { name: 'Principal' })
    expect(within(menu).getByRole('link', { name: 'Auditoría' })).toBeInTheDocument()
    expect(screen.getAllByText('Secretaría académica').length).toBeGreaterThan(0)
  })

  it('sin sesión la intranet redirige al login', async () => {
    const { router } = await renderizarRuta('/intranet/inicio')

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
  })

  it('una ruta sin permiso redirige al inicio', async () => {
    guardarSesion(SESION_ALUMNO)
    const { router } = await renderizarRuta('/intranet/auditoria')

    await waitFor(() => expect(router.state.location.pathname).toBe('/intranet/inicio'))
  })

  it('Salir cierra la sesión en el servidor, borra la local y vuelve al login', async () => {
    guardarSesion(SESION_ADMIN)
    let cierres = 0
    servidor.use(
      http.post('*/api/v1/auth/cierre-sesion', () => {
        cierres++
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { router } = await renderizarRuta('/intranet/inicio')

    await userEvent.click(await screen.findByRole('button', { name: 'Salir' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(cierres).toBe(1)
    expect(obtenerSesion()).toBeNull()
  })
})
