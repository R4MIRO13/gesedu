import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api, descargar, type ArchivoDescargado } from '../../compartido/api/cliente'

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

export interface CambioAuditoria {
  campo: string
  valorAnterior: string | null
  valorNuevo: string | null
}

export interface DetalleRegistroAuditoria {
  registro: RegistroAuditoria
  cambios: CambioAuditoria[]
}

export interface CatalogosAuditoria {
  modulos: { modulo: string; acciones: string[] }[]
  usuarios: { id: string; nombre: string }[]
}

export interface Pagina<T> {
  contenido: T[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}

export interface FiltrosAuditoria {
  usuarioId: string
  modulo: string
  accion: string
  desde: string
  hasta: string
  pagina: number
}

export const FILTROS_VACIOS: FiltrosAuditoria = { usuarioId: '', modulo: '', accion: '', desde: '', hasta: '', pagina: 0 }

function parametrosDe(filtros: FiltrosAuditoria): URLSearchParams {
  const parametros = new URLSearchParams()
  if (filtros.usuarioId) parametros.set('usuarioId', filtros.usuarioId)
  if (filtros.modulo) parametros.set('modulo', filtros.modulo)
  if (filtros.accion) parametros.set('accion', filtros.accion)
  if (filtros.desde) parametros.set('desde', filtros.desde)
  if (filtros.hasta) parametros.set('hasta', filtros.hasta)
  return parametros
}

export function useRegistrosAuditoria(filtros: FiltrosAuditoria) {
  const parametros = parametrosDe(filtros)
  parametros.set('page', String(filtros.pagina))
  parametros.set('size', '15')
  return useQuery({
    queryKey: ['auditoria', filtros],
    queryFn: () => api<Pagina<RegistroAuditoria>>(`/auditoria/registros?${parametros}`),
    placeholderData: keepPreviousData,
  })
}

/** Módulos, acciones y usuarios que existen en la auditoría, para los filtros (RF-51). */
export function useCatalogosAuditoria() {
  return useQuery({
    queryKey: ['auditoria', 'catalogos'],
    queryFn: () => api<CatalogosAuditoria>('/auditoria/catalogos'),
    staleTime: 60_000,
  })
}

/** Los cambios de campo de un registro; solo se pide cuando se abre el detalle. */
export function useDetalleRegistro(id: string | null) {
  return useQuery({
    queryKey: ['auditoria', 'detalle', id],
    enabled: id !== null,
    queryFn: () => api<DetalleRegistroAuditoria>(`/auditoria/registros/${id}`),
  })
}

export type FormatoExportacion = 'xlsx' | 'csv'

/** Descarga los registros que devuelve el filtro actual (UC-25, paso 6). */
export function exportarAuditoria(filtros: FiltrosAuditoria, formato: FormatoExportacion): Promise<ArchivoDescargado> {
  const parametros = parametrosDe(filtros)
  parametros.set('formato', formato)
  return descargar(`/auditoria/registros/exportacion?${parametros}`, `auditoria.${formato}`)
}
