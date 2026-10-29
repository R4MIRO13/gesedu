import { tienePermiso, useSesion } from './sesion'

/** `true` si la sesión actual tiene el permiso. Solo sirve para mostrar u ocultar opciones. */
export function usePermiso(permiso: string): boolean {
  return tienePermiso(useSesion(), permiso)
}
