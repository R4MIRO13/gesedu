import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { StrictMode } from 'react'
import { describe, expect, it } from 'vitest'
import { SESION_ADMIN, sesionDe } from '../../test/msw/handlers'
import { servidor } from '../../test/msw/servidor'
import { SesionProvider } from './SesionProvider'
import { cerrarSesionRemota } from './servicioSesion'
import { guardarSesion, obtenerSesion, tienePermiso } from './sesion'
import { usePermiso } from './usePermiso'

function Permiso({ permiso }: { permiso: string }) {
  return <p>{usePermiso(permiso) ? 'sí' : 'no'}</p>
}

describe('SesionProvider', () => {
  it('recupera la sesión con el refresh token de la cookie antes de mostrar la aplicación', async () => {
    servidor.use(http.post('*/api/v1/auth/refresco', () => HttpResponse.json(SESION_ADMIN)))

    render(
      <SesionProvider>
        <Permiso permiso="AUDITORIA_CONSULTAR" />
      </SesionProvider>,
    )

    expect(screen.getByRole('status')).toHaveTextContent('Cargando')
    expect(await screen.findByText('sí')).toBeInTheDocument()
    expect(obtenerSesion()?.usuario.nombres).toBe('Carmen Salas')
  })

  it('sin cookie válida muestra la aplicación sin sesión', async () => {
    render(
      <SesionProvider>
        <Permiso permiso="AUDITORIA_CONSULTAR" />
      </SesionProvider>,
    )

    expect(await screen.findByText('no')).toBeInTheDocument()
    expect(obtenerSesion()).toBeNull()
  })

  it('con StrictMode (efecto doble) hace un solo refresco: reusar el refresh token cerraría la sesión', async () => {
    let refrescos = 0
    servidor.use(
      http.post('*/api/v1/auth/refresco', () => {
        refrescos++
        return HttpResponse.json(SESION_ADMIN)
      }),
    )

    render(
      <StrictMode>
        <SesionProvider>
          <Permiso permiso="HORARIO_CONSULTAR" />
        </SesionProvider>
      </StrictMode>,
    )

    expect(await screen.findByText('sí')).toBeInTheDocument()
    expect(refrescos).toBe(1)
  })
})

describe('cierre de sesión', () => {
  it('avisa al servidor con el token y borra la sesión local', async () => {
    guardarSesion(SESION_ADMIN)
    let autorizacion: string | null = null
    servidor.use(
      http.post('*/api/v1/auth/cierre-sesion', ({ request }) => {
        autorizacion = request.headers.get('Authorization')
        return new HttpResponse(null, { status: 204 })
      }),
    )

    await cerrarSesionRemota()

    expect(autorizacion).toBe('Bearer token-administrador')
    expect(obtenerSesion()).toBeNull()
  })

  it('si el token de acceso venció, lo renueva para poder revocar el refresh token', async () => {
    guardarSesion(sesionDe('ADMINISTRADOR', [], { token: 'viejo' }))
    const usados: (string | null)[] = []
    servidor.use(
      http.post('*/api/v1/auth/cierre-sesion', ({ request }) => {
        usados.push(request.headers.get('Authorization'))
        return request.headers.get('Authorization') === 'Bearer nuevo'
          ? new HttpResponse(null, { status: 204 })
          : new HttpResponse(null, { status: 401 })
      }),
      http.post('*/api/v1/auth/refresco', () =>
        HttpResponse.json(sesionDe('ADMINISTRADOR', [], { token: 'nuevo' })),
      ),
    )

    await cerrarSesionRemota()

    expect(usados).toEqual(['Bearer viejo', 'Bearer nuevo'])
    expect(obtenerSesion()).toBeNull()
  })

  it('aunque el servidor no responda, la sesión local se cierra', async () => {
    guardarSesion(SESION_ADMIN)
    servidor.use(http.post('*/api/v1/auth/cierre-sesion', () => HttpResponse.error()))

    await cerrarSesionRemota()

    expect(obtenerSesion()).toBeNull()
    expect(tienePermiso(obtenerSesion(), 'HORARIO_CONSULTAR')).toBe(false)
  })
})
