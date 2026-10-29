import { redirect } from '@tanstack/react-router'
import { obtenerSesion, tienePermiso } from './sesion'

/*
 * Guardias de ruta (`beforeLoad`). Solo ordenan la navegación: la decisión de acceso la toma siempre el backend
 * (DA-05). Sin sesión se va a /login; sin el permiso, al inicio.
 */

export function exigirSesion() {
  if (!obtenerSesion()) throw redirect({ to: '/login' })
}

export function exigirPermiso(permiso: string) {
  exigirSesion()
  if (!tienePermiso(obtenerSesion(), permiso)) throw redirect({ to: '/intranet/inicio' })
}

/** Para /login y las pantallas públicas: con sesión abierta no tiene sentido mostrarlas. */
export function redirigirSiHaySesion() {
  if (obtenerSesion()) throw redirect({ to: '/intranet/inicio' })
}
