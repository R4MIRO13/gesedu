import { http, HttpResponse } from 'msw'
import type { SesionIniciada } from '../../compartido/auth/sesion'

/** Sesión de ejemplo: un administrador con los permisos de la demo. */
export function sesionDe(
  rol: string,
  permisos: string[],
  extra: { nombres?: string; cargo?: string | null; token?: string } = {},
): SesionIniciada {
  return {
    tokenAcceso: extra.token ?? `token-${rol.toLowerCase()}`,
    expiraEn: '2099-01-01T00:00:00-05:00',
    usuario: { nombres: extra.nombres ?? 'Carmen Salas', rol, cargo: extra.cargo ?? null, permisos },
  }
}

export const SESION_ADMIN = sesionDe(
  'ADMINISTRADOR',
  ['HORARIO_CONSULTAR', 'HORARIO_CONFIGURAR', 'AUDITORIA_CONSULTAR'],
  { cargo: 'Secretaría académica' },
)
export const SESION_ALUMNO = sesionDe('ALUMNO', ['HORARIO_CONSULTAR'], { nombres: 'Lucía García' })

/** Respuesta de error RFC 9457 como la devuelve el backend. */
export function problema(status: number, codigo: string, detail: string, extra: Record<string, unknown> = {}) {
  return HttpResponse.json(
    { type: `https://gesedu.pe/problemas/${codigo}`, title: detail, status, detail, codigo, ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

/**
 * Handlers por defecto. Sin cookie de refresh no hay sesión que restaurar, igual que con un navegador nuevo.
 * Las pruebas que necesitan más los sobrescriben con `servidor.use`.
 */
export const handlersBase = [
  http.post('*/api/v1/auth/refresco', () => problema(401, 'SESION_EXPIRADA', 'La sesión expiró; inicia sesión de nuevo')),
  http.post('*/api/v1/auth/cierre-sesion', () => new HttpResponse(null, { status: 204 })),
]
