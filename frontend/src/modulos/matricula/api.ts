import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, solicitar } from '../../compartido/api/cliente'

export type EstadoSolicitud = 'PENDIENTE_REVISION' | 'APROBADA' | 'RECHAZADA'
export type TipoEstudiante = 'NUEVO_INGRESO' | 'RECURRENTE'
export type EstadoAdmision = 'ADMITIDO' | 'NO_ADMITIDO' | 'NO_APLICA'
export type EstadoDocumentacion = 'COMPLETA' | 'INCOMPLETA'
export type MotivoRechazo = 'NO_ADMITIDO' | 'DUPLICIDAD' | 'DEUDAS' | 'SIN_VACANTE' | 'VOLUNTAD_APODERADO'
export type ReglaMatricula = 'DOCUMENTACION' | 'ADMISION' | 'MATRICULA_ACTIVA' | 'DEUDAS'

export interface Pagina<T> {
  contenido: T[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}

/** Fila de la bandeja (UC-02, paso 1). */
export interface SolicitudResumen {
  codigo: string
  alumno: string
  grado: string
  fechaSolicitud: string
  tipoEstudiante: TipoEstudiante
  estado: EstadoSolicitud
  estadoDocumentacion: EstadoDocumentacion
  motivoRechazo: MotivoRechazo | null
  estadoVisible: string
}

export interface AlumnoSolicitud {
  codigo: string
  nombre: string
  documento: string
  fechaNacimiento: string
}

export interface ApoderadoSolicitud {
  nombre: string
  documento: string
  correo: string | null
  telefono: string | null
  parentesco: string
}

export interface DocumentoSolicitud {
  id: string
  tipo: string
  nombreArchivo: string
  contentType: string
  tamanoBytes: number
}

export interface ValidacionMatricula {
  regla: ReglaMatricula
  aprobada: boolean
  mensaje: string
  motivoSugerido: MotivoRechazo | null
}

export interface SeccionConVacantes {
  id: string
  gradoId: string
  nombre: string
  capacidad: number
  vacantes: number
}

export interface LineaCosto {
  concepto: string
  periodo: string
  monto: string
  fechaVencimiento: string
}

export interface ResumenCostos {
  lineas: LineaCosto[]
  total: string
  porcentajePenalidad: number
}

export interface DetalleSolicitud {
  codigo: string
  anioEscolar: string
  grado: string
  fechaSolicitud: string
  tipoEstudiante: TipoEstudiante
  estadoAdmision: EstadoAdmision
  estadoDocumentacion: EstadoDocumentacion
  estado: EstadoSolicitud
  motivoRechazo: MotivoRechazo | null
  estadoVisible: string
  observacion: string | null
  alumno: AlumnoSolicitud
  apoderado: ApoderadoSolicitud
  documentos: DocumentoSolicitud[]
  validaciones: ValidacionMatricula[]
  puedeConfirmar: boolean
  secciones: SeccionConVacantes[]
  costos: ResumenCostos
  version: number
}

export interface ResultadoConfirmacion {
  matriculaId: string
  codigoSolicitud: string
  seccionId: string
  seccion: string
  vacantesRestantes: number
  compromisosGenerados: number
  total: string
}

export interface ResultadoRechazo {
  solicitudId: string
  codigoSolicitud: string
  motivo: MotivoRechazo
  estadoVisible: string
}

export interface FiltrosBandeja {
  estado: EstadoSolicitud | ''
  q: string
  pagina: number
}

export const FILTROS_BANDEJA: FiltrosBandeja = { estado: 'PENDIENTE_REVISION', q: '', pagina: 0 }

export const MOTIVOS: Record<MotivoRechazo, string> = {
  NO_ADMITIDO: 'No Admitido',
  DUPLICIDAD: 'Duplicidad',
  DEUDAS: 'Deudas',
  SIN_VACANTE: 'Sin vacante',
  VOLUNTAD_APODERADO: 'Voluntad del apoderado',
}

export function useBandeja(filtros: FiltrosBandeja) {
  const parametros = new URLSearchParams({ page: String(filtros.pagina), size: '15' })
  if (filtros.estado) parametros.set('estado', filtros.estado)
  if (filtros.q.trim()) parametros.set('q', filtros.q.trim())
  return useQuery({
    queryKey: ['solicitudes-matricula', filtros],
    queryFn: () => api<Pagina<SolicitudResumen>>(`/solicitudes-matricula?${parametros}`),
    placeholderData: keepPreviousData,
  })
}

/** Detalle para evaluar. La versión (ETag) es la que se manda en `If-Match` al confirmar o rechazar. */
export function useDetalleSolicitud(codigo: string) {
  return useQuery({
    queryKey: ['solicitud-matricula', codigo],
    queryFn: () => api<DetalleSolicitud>(`/solicitudes-matricula/${codigo}`),
    staleTime: 0,
  })
}

const etag = (version: number) => `"${version}"`

function invalidar(cliente: ReturnType<typeof useQueryClient>, codigo: string) {
  return Promise.all([
    cliente.invalidateQueries({ queryKey: ['solicitud-matricula', codigo] }),
    cliente.invalidateQueries({ queryKey: ['solicitudes-matricula'] }),
  ])
}

export function useConfirmarMatricula(codigo: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ seccionId, version }: { seccionId: string; version: number }) =>
      api<ResultadoConfirmacion>(`/solicitudes-matricula/${codigo}/confirmacion`, {
        method: 'POST',
        cuerpo: { seccionId, costosAceptados: true },
        cabeceras: { 'If-Match': etag(version) },
      }),
    onSettled: () => invalidar(cliente, codigo),
  })
}

export function useRechazarSolicitud(codigo: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ motivo, observacion, version }: { motivo: MotivoRechazo; observacion: string; version: number }) =>
      api<ResultadoRechazo>(`/solicitudes-matricula/${codigo}/rechazo`, {
        method: 'POST',
        cuerpo: { motivo, observacion: observacion.trim() || null },
        cabeceras: { 'If-Match': etag(version) },
      }),
    onSettled: () => invalidar(cliente, codigo),
  })
}

export function useRegistrarEvaluacion(codigo: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({
      tipoEstudiante,
      estadoAdmision,
      version,
    }: {
      tipoEstudiante: TipoEstudiante
      estadoAdmision: EstadoAdmision
      version: number
    }) =>
      api<DetalleSolicitud>(`/solicitudes-matricula/${codigo}/evaluacion`, {
        method: 'PUT',
        cuerpo: { tipoEstudiante, estadoAdmision },
        cabeceras: { 'If-Match': etag(version) },
      }),
    onSettled: () => invalidar(cliente, codigo),
  })
}

/** URL prefirmada de 5 minutos para ver un documento de la solicitud. */
export async function urlDocumento(codigo: string, documentoId: string): Promise<string> {
  const { datos } = await solicitar<{ url: string }>(`/solicitudes-matricula/${codigo}/documentos/${documentoId}/url`)
  return datos.url
}
