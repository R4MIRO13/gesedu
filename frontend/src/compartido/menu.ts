import { linkOptions } from '@tanstack/react-router'

/**
 * Año con el que trabaja la intranet hasta que exista la configuración del año escolar (UC-13): es el año de la
 * demo (docs/PLAN.md § 7.2). Cuando UC-13 esté lista saldrá del año ACTIVO.
 */
export const ANIO_TRABAJO = 2027

/** Opciones de la intranet; la SPA solo oculta, la autorización real la hace el backend. */
export const OPCIONES_MENU = [
  {
    enlace: linkOptions({ to: '/intranet/horarios/bloques' }),
    titulo: 'Bloques horarios',
    descripcion: 'Franjas de clase del año escolar.',
    permiso: 'HORARIO_CONSULTAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/horarios/$anio/elaborar', params: { anio: String(ANIO_TRABAJO) }, search: { perspectiva: 'SECCION' } }),
    titulo: 'Elaborar horario',
    descripcion: 'Arma el horario del año: secciones, cursos y docentes, sin cruces.',
    permiso: 'HORARIO_GESTIONAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/matriculas' }),
    titulo: 'Matrículas',
    descripcion: 'Evalúa las solicitudes: valida, confirma la sección y genera los compromisos de pago.',
    permiso: 'MATRICULA_EVALUAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/auditoria' }),
    titulo: 'Auditoría',
    descripcion: 'Registro inmutable de las operaciones críticas.',
    permiso: 'AUDITORIA_CONSULTAR',
  },
] as const

export type OpcionMenu = (typeof OPCIONES_MENU)[number]
