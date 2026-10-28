import { ApiError } from '../../compartido/api/errores'

const formatoHora = new Intl.DateTimeFormat('es-PE', { timeStyle: 'short', timeZone: 'America/Lima' })

/**
 * Mensaje del inicio de sesión para cada error del backend (RF-52, UC-24 FA-3 y FA-4). Las credenciales
 * incorrectas llevan un mensaje genérico: no revela si el correo existe.
 */
export function mensajeDeInicioSesion(error: unknown): string {
  if (!(error instanceof ApiError)) return 'No se pudo iniciar sesión. Inténtalo de nuevo.'
  switch (error.codigo) {
    case 'CUENTA_BLOQUEADA': {
      const hasta = error.propiedades.bloqueadoHasta
      const hora = typeof hasta === 'string' ? formatoHora.format(new Date(hasta)) : null
      return (
        'Tu cuenta se bloqueó temporalmente por 5 intentos fallidos. Intenta de nuevo más tarde o comunícate con la secretaría.' +
        (hora ? ` Podrás volver a intentarlo a las ${hora}.` : '')
      )
    }
    case 'CUENTA_INACTIVA':
      return 'Tu cuenta está desactivada. Comunícate con la secretaría.'
    case 'LIMITE_PETICIONES_EXCEDIDO':
      return 'Hiciste demasiados intentos seguidos. Espera un minuto e inténtalo de nuevo.'
    case 'SIN_CONEXION':
      return 'No se pudo conectar con el servidor. Revisa tu conexión.'
    case 'CREDENCIALES_INVALIDAS':
      return 'Correo o contraseña incorrectos.'
    default:
      return error.message
  }
}
