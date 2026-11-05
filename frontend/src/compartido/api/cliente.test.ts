import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { guardarSesion, obtenerSesion } from '../auth/sesion'
import { problema, SESION_ADMIN, sesionDe } from '../../test/msw/handlers'
import { servidor } from '../../test/msw/servidor'
import { api, ApiError } from './cliente'

describe('cliente de la API', () => {
  it('manda el token de acceso como Bearer y devuelve el JSON', async () => {
    guardarSesion(SESION_ADMIN)
    let autorizacion: string | null = null
    servidor.use(
      http.get('*/api/v1/prueba', ({ request }) => {
        autorizacion = request.headers.get('Authorization')
        return HttpResponse.json({ ok: true })
      }),
    )

    expect(await api<{ ok: boolean }>('/prueba')).toEqual({ ok: true })
    expect(autorizacion).toBe('Bearer token-administrador')
  })

  it('devuelve undefined con 204 y envía el cuerpo como JSON', async () => {
    let tipo: string | null = null
    let cuerpo: unknown = null
    servidor.use(
      http.post('*/api/v1/prueba', async ({ request }) => {
        tipo = request.headers.get('Content-Type')
        cuerpo = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )

    expect(await api('/prueba', { method: 'POST', cuerpo: { a: 1 } })).toBeUndefined()
    expect(tipo).toBe('application/json')
    expect(cuerpo).toEqual({ a: 1 })
  })

  it('convierte un problem+json en ApiError con el código, los errores por campo y las propiedades extra', async () => {
    servidor.use(
      http.post('*/api/v1/prueba', () =>
        problema(400, 'VALIDACION_INVALIDA', 'Revisa los datos', {
          errores: [{ campo: 'correo', mensaje: 'no es válido' }],
          bloqueadoHasta: '2027-03-01T10:15:00-05:00',
        }),
      ),
    )

    const error = await api('/prueba', { method: 'POST', cuerpo: {} }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(400)
    expect(apiError.codigo).toBe('VALIDACION_INVALIDA')
    expect(apiError.message).toBe('Revisa los datos')
    expect(apiError.errores).toEqual([{ campo: 'correo', mensaje: 'no es válido' }])
    expect(apiError.propiedades).toEqual({ bloqueadoHasta: '2027-03-01T10:15:00-05:00' })
  })

  it('tolera respuestas de error sin cuerpo JSON', async () => {
    servidor.use(http.get('*/api/v1/prueba', () => new HttpResponse('Bad gateway', { status: 502 })))

    const error = (await api('/prueba').catch((e: unknown) => e)) as ApiError

    expect(error.status).toBe(502)
    expect(error.codigo).toBeNull()
    expect(error.message).toBe('Error 502')
  })

  it('si no hay conexión lanza SIN_CONEXION', async () => {
    servidor.use(http.get('*/api/v1/prueba', () => HttpResponse.error()))

    const error = (await api('/prueba').catch((e: unknown) => e)) as ApiError

    expect(error.codigo).toBe('SIN_CONEXION')
    expect(error.status).toBe(0)
  })

  it('una petición cancelada con AbortSignal sale como AbortError, no como fallo de conexión', async () => {
    servidor.use(http.get('*/api/v1/lenta', async () => {
      await new Promise((r) => setTimeout(r, 200))
      return HttpResponse.json({ ok: true })
    }))
    const control = new AbortController()

    const pendiente = api('/lenta', { signal: control.signal }).catch((e: unknown) => e)
    control.abort()
    const error = await pendiente

    expect(error).not.toBeInstanceOf(ApiError)
    expect((error as Error).name).toBe('AbortError')
  })

  it('tras un 401 renueva el token con el refresh y repite la petición una sola vez', async () => {
    guardarSesion(sesionDe('ADMINISTRADOR', ['HORARIO_CONSULTAR'], { token: 'viejo' }))
    const usados: (string | null)[] = []
    servidor.use(
      http.get('*/api/v1/prueba', ({ request }) => {
        usados.push(request.headers.get('Authorization'))
        return request.headers.get('Authorization') === 'Bearer nuevo'
          ? HttpResponse.json({ ok: true })
          : problema(401, 'SESION_EXPIRADA', 'expiró')
      }),
      http.post('*/api/v1/auth/refresco', () =>
        HttpResponse.json(sesionDe('ADMINISTRADOR', ['HORARIO_CONSULTAR'], { token: 'nuevo' })),
      ),
    )

    expect(await api('/prueba')).toEqual({ ok: true })
    expect(usados).toEqual(['Bearer viejo', 'Bearer nuevo'])
    expect(obtenerSesion()?.tokenAcceso).toBe('nuevo')
  })

  it('varias peticiones que reciben 401 a la vez comparten un solo refresco', async () => {
    guardarSesion(sesionDe('ADMINISTRADOR', [], { token: 'viejo' }))
    let refrescos = 0
    servidor.use(
      http.get('*/api/v1/prueba', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer nuevo'
          ? HttpResponse.json({ ok: true })
          : problema(401, 'SESION_EXPIRADA', 'expiró'),
      ),
      http.post('*/api/v1/auth/refresco', async () => {
        refrescos++
        await new Promise((r) => setTimeout(r, 20))
        return HttpResponse.json(sesionDe('ADMINISTRADOR', [], { token: 'nuevo' }))
      }),
    )

    const resultados = await Promise.all([api('/prueba'), api('/prueba'), api('/prueba')])

    expect(resultados).toHaveLength(3)
    expect(refrescos).toBe(1)
  })

  it('si el refresh falla, el 401 llega como ApiError y la sesión queda cerrada', async () => {
    guardarSesion(SESION_ADMIN)
    servidor.use(http.get('*/api/v1/prueba', () => problema(401, 'SESION_EXPIRADA', 'expiró')))

    const error = (await api('/prueba').catch((e: unknown) => e)) as ApiError

    expect(error.status).toBe(401)
    expect(error.codigo).toBe('SESION_EXPIRADA')
    expect(obtenerSesion()).toBeNull()
  })

  it('un 401 de /auth/ no intenta renovar la sesión', async () => {
    guardarSesion(SESION_ADMIN)
    let refrescos = 0
    servidor.use(
      http.post('*/api/v1/auth/inicio-sesion', () => problema(401, 'CREDENCIALES_INVALIDAS', 'Correo o contraseña incorrectos')),
      http.post('*/api/v1/auth/refresco', () => {
        refrescos++
        return problema(401, 'SESION_EXPIRADA', 'expiró')
      }),
    )

    const error = (await api('/auth/inicio-sesion', { method: 'POST', cuerpo: {} }).catch((e: unknown) => e)) as ApiError

    expect(error.codigo).toBe('CREDENCIALES_INVALIDAS')
    expect(refrescos).toBe(0)
  })

  it('sin token no intenta renovar: un 401 público se devuelve tal cual', async () => {
    let refrescos = 0
    servidor.use(
      http.get('*/api/v1/prueba', () => problema(401, 'NO_AUTENTICADO', 'Debes iniciar sesión')),
      http.post('*/api/v1/auth/refresco', () => {
        refrescos++
        return problema(401, 'SESION_EXPIRADA', 'expiró')
      }),
    )

    await expect(api('/prueba')).rejects.toMatchObject({ status: 401, codigo: 'NO_AUTENTICADO' })
    expect(refrescos).toBe(0)
  })
})
