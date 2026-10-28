import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { obtenerSesion } from '../compartido/auth/sesion'
import { problema, SESION_ADMIN } from '../test/msw/handlers'
import { servidor } from '../test/msw/servidor'
import { renderizarRuta } from '../test/utilidades'

describe('Login', () => {
  it('con credenciales correctas guarda la sesión en memoria y entra a la intranet', async () => {
    servidor.use(
      http.post('*/api/v1/auth/inicio-sesion', async ({ request }) => {
        expect(await request.json()).toEqual({ correo: 'admin@demo.gesedu.pe', contrasena: 'Gesedu2027!' })
        return HttpResponse.json(SESION_ADMIN)
      }),
    )
    const { router } = await renderizarRuta('/login')

    await userEvent.type(await screen.findByLabelText('Correo electrónico'), 'admin@demo.gesedu.pe')
    await userEvent.type(screen.getByLabelText('Contraseña'), 'Gesedu2027!')
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/intranet/inicio'))
    expect(obtenerSesion()?.usuario.rol).toBe('ADMINISTRADOR')
    // El token no se guarda en el navegador (DA-04)
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain('token')
  })

  it('con credenciales incorrectas muestra el mensaje del servidor y no inicia sesión', async () => {
    servidor.use(
      http.post('*/api/v1/auth/inicio-sesion', () =>
        problema(401, 'CREDENCIALES_INVALIDAS', 'Correo o contraseña incorrectos'),
      ),
    )
    await renderizarRuta('/login')

    await userEvent.type(await screen.findByLabelText('Correo electrónico'), 'admin@demo.gesedu.pe')
    await userEvent.type(screen.getByLabelText('Contraseña'), 'mala')
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos')
    expect(obtenerSesion()).toBeNull()
  })

  it('no envía el formulario vacío', async () => {
    await renderizarRuta('/login')

    await userEvent.click(await screen.findByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ingresa tu correo y tu contraseña.')
  })
})
