import { obtenerSesion } from '../auth/sesion'
import { refrescarSesion } from '../auth/servicioSesion'
import { ApiError, errorDesdeRespuesta, SIN_CONEXION } from './errores'

export interface OpcionesApi {
  method?: string
  cuerpo?: unknown
  /** Cabeceras extra, por ejemplo `If-Match` en las ediciones con versión. */
  cabeceras?: Record<string, string>
  /** `blob` para descargar archivos (Excel, CSV, PDF); por defecto se espera JSON. */
  formato?: 'json' | 'blob'
}

export interface RespuestaApi<T> {
  datos: T
  /** Cabeceras de la respuesta (por ejemplo `ETag` del borrador de horario). */
  cabeceras: Headers
}

async function enviar(ruta: string, opciones: OpcionesApi, token: string | undefined): Promise<Response> {
  const cabeceras: Record<string, string> = {
    Accept: opciones.formato === 'blob' ? '*/*' : 'application/json, application/problem+json',
    ...opciones.cabeceras,
  }
  if (opciones.cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json'
  if (token) cabeceras.Authorization = `Bearer ${token}`
  try {
    return await fetch(`/api/v1${ruta}`, {
      method: opciones.method ?? 'GET',
      headers: cabeceras,
      body: opciones.cuerpo === undefined ? undefined : JSON.stringify(opciones.cuerpo),
      credentials: 'same-origin',
    })
  } catch {
    throw new ApiError(0, 'No se pudo conectar con el servidor', SIN_CONEXION)
  }
}

/**
 * Llama a la API con el token de acceso en memoria. Si responde 401 con un token puesto, intenta una sola
 * vez renovarlo con el refresh token de la cookie (DA-04) y repite la petición; si no se puede renovar, la
 * sesión queda cerrada. Los errores salen como `ApiError` (RFC 9457).
 */
export async function solicitar<T>(ruta: string, opciones: OpcionesApi = {}): Promise<RespuestaApi<T>> {
  let token = obtenerSesion()?.tokenAcceso
  let respuesta = await enviar(ruta, opciones, token)

  // Los endpoints de /auth/ manejan su propia sesión: un 401 allí no se resuelve renovando el token
  if (respuesta.status === 401 && token && !ruta.startsWith('/auth/')) {
    if (await refrescarSesion()) {
      token = obtenerSesion()?.tokenAcceso
      respuesta = await enviar(ruta, opciones, token)
    }
  }

  if (!respuesta.ok) {
    let cuerpo: unknown = null
    try {
      cuerpo = await respuesta.json()
    } catch {
      // cuerpo vacío o que no es JSON
    }
    throw errorDesdeRespuesta(respuesta.status, cuerpo)
  }
  if (respuesta.status === 204) return { datos: undefined as T, cabeceras: respuesta.headers }
  if (opciones.formato === 'blob') return { datos: (await respuesta.blob()) as T, cabeceras: respuesta.headers }
  return { datos: (await respuesta.json()) as T, cabeceras: respuesta.headers }
}

export async function api<T>(ruta: string, opciones: OpcionesApi = {}): Promise<T> {
  return (await solicitar<T>(ruta, opciones)).datos
}

/** Archivo descargado: el contenido y el nombre que propone el servidor (Content-Disposition). */
export interface ArchivoDescargado {
  blob: Blob
  nombre: string
}

export async function descargar(ruta: string, nombrePorDefecto: string): Promise<ArchivoDescargado> {
  const { datos, cabeceras } = await solicitar<Blob>(ruta, { formato: 'blob' })
  const disposicion = cabeceras.get('Content-Disposition') ?? ''
  const nombre = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposicion)?.[1]
  return { blob: datos, nombre: nombre ? decodeURIComponent(nombre) : nombrePorDefecto }
}

export { ApiError } from './errores'
