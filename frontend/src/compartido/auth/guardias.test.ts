import { isRedirect } from '@tanstack/react-router'
import { describe, expect, it } from 'vitest'
import { SESION_ADMIN, SESION_ALUMNO } from '../../test/msw/handlers'
import { exigirPermiso, exigirSesion, redirigirSiHaySesion } from './guardias'
import { guardarSesion } from './sesion'

/** Ejecuta la guardia y devuelve a dónde redirige (o `null` si deja pasar). */
function destino(guardia: () => void): string | null {
  try {
    guardia()
    return null
  } catch (e) {
    if (isRedirect(e)) return String(e.options.to)
    throw e
  }
}

describe('guardias de ruta', () => {
  it('sin sesión mandan al login', () => {
    expect(destino(exigirSesion)).toBe('/login')
    expect(destino(() => exigirPermiso('AUDITORIA_CONSULTAR'))).toBe('/login')
  })

  it('con sesión dejan pasar', () => {
    guardarSesion(SESION_ADMIN)
    expect(destino(exigirSesion)).toBeNull()
    expect(destino(() => exigirPermiso('AUDITORIA_CONSULTAR'))).toBeNull()
  })

  it('sin el permiso mandan al inicio', () => {
    guardarSesion(SESION_ALUMNO)
    expect(destino(() => exigirPermiso('AUDITORIA_CONSULTAR'))).toBe('/intranet/inicio')
    expect(destino(() => exigirPermiso('HORARIO_CONSULTAR'))).toBeNull()
  })

  it('el login y las pantallas públicas redirigen al inicio si ya hay sesión', () => {
    expect(destino(redirigirSiHaySesion)).toBeNull()
    guardarSesion(SESION_ALUMNO)
    expect(destino(redirigirSiHaySesion)).toBe('/intranet/inicio')
  })
})
