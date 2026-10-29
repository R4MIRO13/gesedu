import { ApiError } from '../../compartido/api/errores'

/**
 * Mensaje de cada error de la evaluación de matrícula (UC-02): las validaciones que fallan (FA-1 a FA-3), la falta
 * de vacantes (FA-4) y los cambios simultáneos.
 */
export function mensajeDeMatricula(error: unknown): string {
  if (!(error instanceof ApiError)) return 'No se pudo completar la operación. Inténtalo de nuevo.'
  switch (error.codigo) {
    case 'SIN_VACANTE':
      return 'La sección elegida ya no tiene vacantes. Elige otra sección o rechaza la solicitud por falta de vacante.'
    case 'CONFLICTO_CONCURRENCIA':
      return 'La solicitud cambió mientras la revisabas (otra persona la evaluó). Recarga para ver su estado actual.'
    case 'MATRICULA_DUPLICADA':
      return 'El alumno ya tiene una matrícula activa en el año. Rechaza la solicitud por duplicidad.'
    case 'DEUDA_VENCIDA':
      return `${error.message}. Rechaza la solicitud por deudas.`
    case 'NO_ADMITIDO':
      return `${error.message}. Rechaza la solicitud por no admitido.`
    case 'SIN_CONEXION':
      return 'No se pudo conectar con el servidor. Revisa tu conexión.'
    default:
      return error.message
  }
}

export function esConflictoDeVersion(error: unknown): boolean {
  return error instanceof ApiError && error.codigo === 'CONFLICTO_CONCURRENCIA'
}

/** Campo del formulario de la solicitud al que corresponde el `campo` que devuelve la API (`alumno.documento`…). */
export function campoDeSolicitud(campo: string): string {
  const mapa: Record<string, string> = {
    'alumno.nombres': 'nombres',
    'alumno.apellidos': 'apellidos',
    'alumno.documento': 'documento',
    'alumno.fechaNacimiento': 'fechaNacimiento',
    'alumno.parentesco': 'parentesco',
    telefonoApoderado: 'telefono',
    gradoId: 'gradoId',
  }
  return mapa[campo] ?? campo
}
