import { cerrarSesion, obtenerSesion } from './sesion'

/** Error RFC 9457 (`application/problem+json`) con la propiedad `codigo` del backend. */
export class ApiError extends Error {
  status: number
  codigo: string | null

  constructor(status: number, mensaje: string, codigo: string | null) {
    super(mensaje)
    this.status = status
    this.codigo = codigo
  }
}

interface Problema {
  detail?: string
  title?: string
  codigo?: string
}

export async function api<T>(ruta: string, opciones: { method?: string; cuerpo?: unknown } = {}): Promise<T> {
  const token = obtenerSesion()?.tokenAcceso
  const cabeceras: Record<string, string> = { Accept: 'application/json, application/problem+json' }
  if (opciones.cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json'
  if (token) cabeceras.Authorization = `Bearer ${token}`

  let respuesta: Response
  try {
    respuesta = await fetch(`/api/v1${ruta}`, {
      method: opciones.method ?? 'GET',
      headers: cabeceras,
      body: opciones.cuerpo === undefined ? undefined : JSON.stringify(opciones.cuerpo),
    })
  } catch {
    throw new ApiError(0, 'No se pudo conectar con el servidor', 'SIN_CONEXION')
  }

  if (!respuesta.ok) {
    let problema: Problema = {}
    try {
      problema = (await respuesta.json()) as Problema
    } catch {
      // cuerpo vacío o no JSON
    }
    // Token vencido o inválido en una ruta protegida: la sesión ya no sirve
    if (respuesta.status === 401 && token) cerrarSesion()
    throw new ApiError(
      respuesta.status,
      problema.detail ?? problema.title ?? `Error ${respuesta.status}`,
      problema.codigo ?? null,
    )
  }
  if (respuesta.status === 204) return undefined as T
  return (await respuesta.json()) as T
}
