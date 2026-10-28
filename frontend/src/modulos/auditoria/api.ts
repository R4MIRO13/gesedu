import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../../compartido/api/cliente'

export interface RegistroAuditoria {
  id: string
  fechaHora: string
  usuarioId: string | null
  usuario: string | null
  accion: string
  modulo: string
  tipoEntidad: string | null
  idEntidadAfectada: string | null
  descripcion: string | null
}

export interface Pagina<T> {
  contenido: T[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}

export interface FiltrosAuditoria {
  modulo: string
  accion: string
  desde: string
  hasta: string
  pagina: number
}

export function useRegistrosAuditoria(filtros: FiltrosAuditoria) {
  const parametros = new URLSearchParams({ page: String(filtros.pagina), size: '15' })
  if (filtros.modulo) parametros.set('modulo', filtros.modulo)
  if (filtros.accion) parametros.set('accion', filtros.accion)
  if (filtros.desde) parametros.set('desde', filtros.desde)
  if (filtros.hasta) parametros.set('hasta', filtros.hasta)
  return useQuery({
    queryKey: ['auditoria', filtros],
    queryFn: () => api<Pagina<RegistroAuditoria>>(`/auditoria/registros?${parametros}`),
    placeholderData: keepPreviousData,
  })
}
