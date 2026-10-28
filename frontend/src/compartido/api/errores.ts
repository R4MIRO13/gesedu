/** Error de un campo, tal como lo devuelve el backend en `errores[]` (400 VALIDACION_INVALIDA). */
export interface ErrorCampo {
  campo: string
  mensaje: string
}

/** Cuerpo RFC 9457 (`application/problem+json`) con las propiedades extra del backend. */
interface Problema {
  detail?: string
  title?: string
  codigo?: string
  errores?: ErrorCampo[]
  [propiedad: string]: unknown
}

/** Error de la API: estado HTTP, `codigo` de negocio y, si existen, errores por campo y propiedades extra. */
export class ApiError extends Error {
  readonly status: number
  readonly codigo: string | null
  readonly errores: ErrorCampo[]
  readonly propiedades: Record<string, unknown>

  constructor(
    status: number,
    mensaje: string,
    codigo: string | null,
    errores: ErrorCampo[] = [],
    propiedades: Record<string, unknown> = {},
  ) {
    super(mensaje)
    this.name = 'ApiError'
    this.status = status
    this.codigo = codigo
    this.errores = errores
    this.propiedades = propiedades
  }
}

/** Convierte el cuerpo de una respuesta de error en un `ApiError`; tolera cuerpos vacíos o que no son JSON. */
export function errorDesdeRespuesta(status: number, cuerpo: unknown): ApiError {
  const problema: Problema = typeof cuerpo === 'object' && cuerpo !== null ? (cuerpo as Problema) : {}
  const { detail, title, codigo, errores, type: _tipo, status: _estado, instance: _instancia, ...extra } = problema
  return new ApiError(
    status,
    detail ?? title ?? `Error ${status}`,
    typeof codigo === 'string' ? codigo : null,
    Array.isArray(errores) ? errores : [],
    extra,
  )
}

export const SIN_CONEXION = 'SIN_CONEXION'
