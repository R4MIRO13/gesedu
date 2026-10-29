import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { guardarSesion } from '../compartido/auth/sesion'
import { problema, SESION_ADMIN } from '../test/msw/handlers'
import { servidor } from '../test/msw/servidor'
import { renderizarRuta } from '../test/utilidades'

const MENSAJE = 'Si el correo está registrado, recibirás un enlace para restablecer tu contraseña. El enlace vale 30 minutos.'

describe('Recuperar contraseña', () => {
  it('el login ofrece el enlace «¿Olvidaste tu contraseña?»', async () => {
    const { router } = await renderizarRuta('/login')

    await userEvent.click(await screen.findByRole('link', { name: '¿Olvidaste tu contraseña?' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/recuperar-contrasena'))
  })

  it('pide el enlace y muestra el mismo mensaje exista o no la cuenta', async () => {
    const correos: string[] = []
    servidor.use(
      http.post('*/api/v1/auth/recuperacion', async ({ request }) => {
        correos.push(((await request.json()) as { correo: string }).correo)
        return HttpResponse.json({ mensaje: MENSAJE }, { status: 202 })
      }),
    )
    await renderizarRuta('/recuperar-contrasena')

    await userEvent.type(await screen.findByLabelText(/^Correo/), 'nadie@demo.gesedu.pe')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar enlace' }))

    expect(await screen.findByRole('status')).toHaveTextContent(MENSAJE)
    expect(correos).toEqual(['nadie@demo.gesedu.pe'])
    expect(screen.queryByRole('button', { name: 'Enviar enlace' })).not.toBeInTheDocument()
  })

  it('valida el formato del correo antes de enviar', async () => {
    let llamadas = 0
    servidor.use(
      http.post('*/api/v1/auth/recuperacion', () => {
        llamadas++
        return HttpResponse.json({ mensaje: MENSAJE }, { status: 202 })
      }),
    )
    await renderizarRuta('/recuperar-contrasena')

    await userEvent.click(await screen.findByRole('button', { name: 'Enviar enlace' }))
    expect(await screen.findByText('Ingresa tu correo.')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText(/^Correo/), 'sin-arroba')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar enlace' }))
    expect(await screen.findByText('Ingresa un correo válido.')).toBeInTheDocument()
    expect(llamadas).toBe(0)
  })

  it('con sesión abierta no se muestra y lleva al inicio', async () => {
    guardarSesion(SESION_ADMIN)
    const { router } = await renderizarRuta('/recuperar-contrasena')

    await waitFor(() => expect(router.state.location.pathname).toBe('/intranet/inicio'))
  })
})

describe('Restablecer contraseña', () => {
  async function llenar(nueva: string, repetida: string) {
    await userEvent.type(await screen.findByLabelText(/^Contraseña nueva/), nueva)
    await userEvent.type(screen.getByLabelText(/^Repite la contraseña/), repetida)
    await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' }))
  }

  it('manda el token del enlace y la contraseña nueva, y ofrece volver a iniciar sesión', async () => {
    let cuerpo: unknown = null
    servidor.use(
      http.post('*/api/v1/auth/recuperacion/confirmacion', async ({ request }) => {
        cuerpo = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await renderizarRuta('/restablecer-contrasena?token=abc123')

    await llenar('NuevaClave2027', 'NuevaClave2027')

    expect(await screen.findByRole('status')).toHaveTextContent('Tu contraseña se actualizó')
    expect(cuerpo).toEqual({ token: 'abc123', nuevaContrasena: 'NuevaClave2027' })
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toBeInTheDocument()
  })

  it('aplica la política de contraseñas y exige que coincidan', async () => {
    let llamadas = 0
    servidor.use(
      http.post('*/api/v1/auth/recuperacion/confirmacion', () => {
        llamadas++
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await renderizarRuta('/restablecer-contrasena?token=abc123')

    await llenar('corta1', 'corta1')
    expect((await screen.findAllByText(/entre 8 y 72 caracteres/)).length).toBeGreaterThan(0)

    await userEvent.clear(screen.getByLabelText(/^Contraseña nueva/))
    await userEvent.clear(screen.getByLabelText(/^Repite la contraseña/))
    await llenar('NuevaClave2027', 'OtraClave2027')
    expect(await screen.findByText('Las contraseñas no coinciden.')).toBeInTheDocument()
    expect(llamadas).toBe(0)
  })

  it('un enlace vencido o ya usado ofrece pedir otro', async () => {
    servidor.use(
      http.post('*/api/v1/auth/recuperacion/confirmacion', () =>
        problema(400, 'TOKEN_RECUPERACION_INVALIDO', 'El enlace de recuperación no es válido o venció'),
      ),
    )
    await renderizarRuta('/restablecer-contrasena?token=viejo')

    await llenar('NuevaClave2027', 'NuevaClave2027')

    expect(await screen.findByRole('alert')).toHaveTextContent('El enlace no es válido o ya venció')
    expect(screen.getByRole('link', { name: 'Pedir otro enlace' })).toBeInTheDocument()
  })

  it('sin token en la dirección muestra directamente que el enlace no sirve', async () => {
    await renderizarRuta('/restablecer-contrasena')

    expect(await screen.findByRole('alert')).toHaveTextContent('El enlace no es válido o ya venció')
  })
})
