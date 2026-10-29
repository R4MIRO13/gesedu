import { guardarSesion, limpiarSesion, obtenerSesion, type SesionIniciada } from './sesion'

/*
 * Renovación y cierre de la sesión contra el backend. Usa `fetch` directamente (no el cliente `api`) para no
 * entrar en un ciclo: el cliente es quien llama a `refrescarSesion` cuando recibe un 401.
 */

let enCurso: Promise<boolean> | null = null

/**
 * Cambia el refresh token de la cookie por un token de acceso nuevo (el refresh rota en cada uso). Varias
 * llamadas simultáneas comparten una sola petición: usar dos veces el mismo refresh token se tomaría por un robo
 * y cerraría la sesión (DA-04).
 *
 * @returns `true` si hay sesión renovada; `false` si no se pudo (y, si el servidor la rechazó, la sesión local
 *          queda cerrada)
 */
export function refrescarSesion(): Promise<boolean> {
  enCurso ??= pedirRefresco().finally(() => {
    enCurso = null
  })
  return enCurso
}

async function pedirRefresco(): Promise<boolean> {
  try {
    const respuesta = await fetch('/api/v1/auth/refresco', {
      method: 'POST',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    })
    if (respuesta.ok) {
      guardarSesion((await respuesta.json()) as SesionIniciada)
      return true
    }
    // 401: el refresh token falta, venció o fue revocado. Otros errores (429, 5xx) son pasajeros.
    if (respuesta.status === 401) limpiarSesion()
    return false
  } catch {
    return false
  }
}

let restauracion: Promise<void> | null = null

/** Recupera la sesión al cargar la página con el refresh token de la cookie. Se ejecuta una sola vez. */
export function restaurarSesion(): Promise<void> {
  restauracion ??= refrescarSesion().then(() => undefined)
  return restauracion
}

/** Solo para pruebas: permite volver a restaurar la sesión como si se recargara la página. */
export function reiniciarRestauracion() {
  restauracion = null
}

async function pedirCierre(token: string | undefined): Promise<Response> {
  return fetch('/api/v1/auth/cierre-sesion', {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    credentials: 'same-origin',
  })
}

/**
 * Cierra la sesión: el servidor revoca el refresh token (el siguiente refresco da 401) y se borra la sesión
 * local. Si el token de acceso ya venció, primero se renueva para poder cerrar la sesión de verdad.
 */
export async function cerrarSesionRemota(): Promise<void> {
  try {
    let respuesta = await pedirCierre(obtenerSesion()?.tokenAcceso)
    if (respuesta.status === 401 && (await refrescarSesion())) {
      respuesta = await pedirCierre(obtenerSesion()?.tokenAcceso)
    }
  } catch {
    // sin conexión: igual se cierra la sesión local
  } finally {
    limpiarSesion()
  }
}
