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

// Demo: la sesión vive en sessionStorage para sobrevivir a F5 (sin refresh token, S-27).
const CLAVE = 'gesedu.sesion'
const oyentes = new Set<() => void>()
let cache: { crudo: string | null; sesion: SesionIniciada | null } | null = null

function leer(): SesionIniciada | null {
  let crudo: string | null = null
  try {
    crudo = sessionStorage.getItem(CLAVE)
  } catch {
    return cache?.sesion ?? null
  }
  if (cache && cache.crudo === crudo) return cache.sesion
  let sesion: SesionIniciada | null = null
  if (crudo) {
    try {
      sesion = JSON.parse(crudo) as SesionIniciada
    } catch {
      sesion = null
    }
  }
  cache = { crudo, sesion }
  return sesion
}

export function obtenerSesion(): SesionIniciada | null {
  const sesion = leer()
  if (sesion && new Date(sesion.expiraEn).getTime() <= Date.now()) {
    cerrarSesion()
    return null
  }
  return sesion
}

export function guardarSesion(sesion: SesionIniciada) {
  try {
    sessionStorage.setItem(CLAVE, JSON.stringify(sesion))
  } catch {
    // sin almacenamiento: la sesión se pierde al recargar
  }
  oyentes.forEach((o) => o())
}

export function cerrarSesion() {
  try {
    sessionStorage.removeItem(CLAVE)
  } catch {
    // nada que borrar
  }
  cache = null
  oyentes.forEach((o) => o())
}

export function useSesion(): SesionIniciada | null {
  return useSyncExternalStore(
    (cb) => {
      oyentes.add(cb)
      return () => oyentes.delete(cb)
    },
    leer,
  )
}

export function tienePermiso(sesion: SesionIniciada | null, permiso: string): boolean {
  return sesion?.usuario.permisos.includes(permiso) ?? false
}

export const ROLES: Record<string, string> = {
  ADMINISTRADOR: 'Administrador',
  DOCENTE: 'Docente',
  ALUMNO: 'Alumno',
  APODERADO: 'Apoderado',
}
