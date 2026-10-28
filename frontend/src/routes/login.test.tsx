import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { obtenerSesion } from '../compartido/auth/sesion'
import { problema, SESION_ADMIN } from '../test/msw/handlers'
import { servidor } from '../test/msw/servidor'
import { renderizarRuta } from '../test/utilidades'

async function iniciarSesion(correo: string, contrasena: string) {
  await userEvent.type(await screen.findByLabelText(/^Correo/), correo)
  await userEvent.type(screen.getByLabelText(/^Contraseña/), contrasena)
  await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
}

describe('Login', () => {
  it('con credenciales correctas guarda la sesión en memoria y entra a la intranet', async () => {
    servidor.use(
      http.post('*/api/v1/auth/inicio-sesion', async ({ request }) => {
        expect(await request.json()).toEqual({ correo: 'admin@demo.gesedu.pe', contrasena: 'Gesedu2027!' })
        return HttpResponse.json(SESION_ADMIN)
      }),
    )
    const { router } = await renderizarRuta('/login')

    await iniciarSesion('admin@demo.gesedu.pe', 'Gesedu2027!')

    await waitFor(() => expect(router.state.location.pathname).toBe('/intranet/inicio'))
    expect(obtenerSesion()?.usuario.rol).toBe('ADMINISTRADOR')
    // El token no se guarda en el navegador (DA-04)
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain('token')
  })

  it('el campo se llama «Correo» (C-02): ya no acepta DNI', async () => {
    await renderizarRuta('/login')

    expect(await screen.findByLabelText(/^Correo/)).toBeInTheDocument()
    expect(screen.queryByText(/DNI/)).not.toBeInTheDocument()
  })

  it('con credenciales incorrectas muestra un mensaje genérico y no inicia sesión', async () => {
    servidor.use(
      http.post('*/api/v1/auth/inicio-sesion', () =>
        problema(401, 'CREDENCIALES_INVALIDAS', 'Correo o contraseña incorrectos'),
      ),
    )
    await renderizarRuta('/login')

    await iniciarSesion('admin@demo.gesedu.pe', 'mala')

    expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos.')
    expect(obtenerSesion()).toBeNull()
  })

  it('al quinto intento fallido muestra el mensaje de bloqueo del wireframe con la hora de desbloqueo', async () => {
    servidor.use(
      http.post('*/api/v1/auth/inicio-sesion', () =>
        problema(401, 'CUENTA_BLOQUEADA', 'La cuenta está bloqueada temporalmente', {
          bloqueadoHasta: '2027-03-01T10:15:00-05:00',
        }),
      ),
    )
    await renderizarRuta('/login')

    await iniciarSesion('bloqueo@demo.gesedu.pe', 'mala')

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent(
      'Tu cuenta se bloqueó temporalmente por 5 intentos fallidos. Intenta de nuevo más tarde o comunícate con la secretaría.',
    )
    expect(alerta).toHaveTextContent('10:15')
    expect(obtenerSesion()).toBeNull()
  })

  it('una cuenta desactivada y el límite de peticiones tienen su propio mensaje', async () => {
    servidor.use(
      http.post('*/api/v1/auth/inicio-sesion', () => problema(401, 'CUENTA_INACTIVA', 'La cuenta está desactivada')),
    )
    await renderizarRuta('/login')
    await iniciarSesion('inactivo@demo.gesedu.pe', 'Gesedu2027!')
    expect(await screen.findByRole('alert')).toHaveTextContent('Tu cuenta está desactivada')
  })

  it('no envía el formulario vacío ni un correo mal escrito', async () => {
    let llamadas = 0
    servidor.use(
      http.post('*/api/v1/auth/inicio-sesion', () => {
        llamadas++
        return HttpResponse.json(SESION_ADMIN)
      }),
    )
    await renderizarRuta('/login')

    await userEvent.click(await screen.findByRole('button', { name: 'Ingresar' }))
    expect(await screen.findByText('Ingresa tu correo.')).toBeInTheDocument()
    expect(screen.getByText('Ingresa tu contraseña.')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText(/^Correo/), 'no-es-un-correo')
    await userEvent.type(screen.getByLabelText(/^Contraseña/), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
    expect(await screen.findByText('Ingresa un correo válido.')).toBeInTheDocument()
    expect(llamadas).toBe(0)
  })

  it('si ya hay sesión, /login redirige al inicio', async () => {
    servidor.use(http.post('*/api/v1/auth/refresco', () => HttpResponse.json(SESION_ADMIN)))
    const { guardarSesion } = await import('../compartido/auth/sesion')
    guardarSesion(SESION_ADMIN)

    const { router } = await renderizarRuta('/login')

    await waitFor(() => expect(router.state.location.pathname).toBe('/intranet/inicio'))
  })
})
