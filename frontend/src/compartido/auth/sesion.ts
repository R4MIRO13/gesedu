import { useSyncExternalStore } from 'react'

export interface DatosUsuario {
  nombres: string
  rol: string
  cargo: string | null
  permisos: string[]
}

export interface SesionIniciada {
  tokenAcceso: string
  expiraEn: string
  usuario: DatosUsuario
}

/**
 * La sesión vive solo en memoria (DA-04, S-06): el token de acceso nunca se guarda en el navegador. Al recargar
 * la página se recupera con el refresh token de la cookie HttpOnly (`restaurarSesion`).
 */
let sesion: SesionIniciada | null = null
const oyentes = new Set<() => void>()

function notificar() {
  oyentes.forEach((o) => o())
}

export function obtenerSesion(): SesionIniciada | null {
  return sesion
}

export function guardarSesion(nueva: SesionIniciada) {
  sesion = nueva
  notificar()
}

/** Olvida la sesión local. No avisa al servidor: para eso está `cerrarSesionRemota`. */
export function limpiarSesion() {
  sesion = null
  notificar()
}

export function useSesion(): SesionIniciada | null {
  return useSyncExternalStore(
    (alCambiar) => {
      oyentes.add(alCambiar)
      return () => oyentes.delete(alCambiar)
    },
    obtenerSesion,
  )
}

export function tienePermiso(actual: SesionIniciada | null, permiso: string): boolean {
  return actual?.usuario.permisos.includes(permiso) ?? false
}

export const ROLES: Record<string, string> = {
  ADMINISTRADOR: 'Administrador',
  DOCENTE: 'Docente',
  ALUMNO: 'Alumno',
  APODERADO: 'Apoderado',
}
