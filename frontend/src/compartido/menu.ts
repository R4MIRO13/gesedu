export interface OpcionMenu {
  to: '/intranet/horarios/bloques' | '/intranet/auditoria'
  titulo: string
  descripcion: string
  permiso: string
}

/** Opciones de la intranet; la SPA solo oculta, la autorización real la hace el backend. */
export const OPCIONES_MENU: readonly OpcionMenu[] = [
  {
    to: '/intranet/horarios/bloques',
    titulo: 'Bloques horarios',
    descripcion: 'Franjas de clase del año escolar.',
    permiso: 'HORARIO_CONSULTAR',
  },
  {
    to: '/intranet/auditoria',
    titulo: 'Auditoría',
    descripcion: 'Registro inmutable de las operaciones críticas.',
    permiso: 'AUDITORIA_CONSULTAR',
  },
]
