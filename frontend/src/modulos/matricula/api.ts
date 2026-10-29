import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, solicitar } from '../../compartido/api/cliente'
import { ApiError, SIN_CONEXION } from '../../compartido/api/errores'

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

// ---- UC-01: solicitud del apoderado ----

export type TipoDocumento = 'CERTIFICADO_ESTUDIOS' | 'FICHA_MATRICULA_ANTERIOR' | 'OTRO'

export const NOMBRE_DOCUMENTO: Record<TipoDocumento, string> = {
  CERTIFICADO_ESTUDIOS: 'Certificado de estudios',
  FICHA_MATRICULA_ANTERIOR: 'Ficha de matrícula anterior',
  OTRO: 'Otro documento',
}

export interface DatosApoderado {
  nombre: string
  documento: string
  correo: string | null
  telefono: string | null
}

export interface OpcionesSolicitud {
  anio: number
  apoderado: DatosApoderado
  grados: { id: string; nombre: string }[]
  documentosRequeridos: Record<TipoEstudiante, TipoDocumento[]>
  costosConfigurados: boolean
}

/** Documento ya subido al Gestor de Archivos, listo para declararse en la solicitud. */
export interface DocumentoSubido {
  tipo: TipoDocumento
  claveObjeto: string
  nombreArchivo: string
  contentType: string
  tamanoBytes: number
}

export interface SolicitudNueva {
  anio: number
  tipoEstudiante: TipoEstudiante
  gradoId: string
  alumno: { nombres: string; apellidos: string; documento: string; fechaNacimiento: string; parentesco: string }
  telefonoApoderado: string
  documentos: DocumentoSubido[]
}

export interface SolicitudRegistrada {
  codigo: string
  estado: EstadoSolicitud
  estadoVisible: string
  fechaSolicitud: string
  anio: number
  alumno: string
  grado: string
  mensaje: string
}

export interface SolicitudPropia {
  codigo: string
  alumno: string
  grado: string
  anio: number
  fechaSolicitud: string
  tipoEstudiante: TipoEstudiante
  estado: EstadoSolicitud
  estadoVisible: string
  observacion: string | null
}

export const TIPOS_ARCHIVO_PERMITIDOS = ['application/pdf', 'image/jpeg', 'image/png']
export const TAMANO_MAXIMO_ARCHIVO = 5 * 1024 * 1024

export function useOpcionesSolicitud(anio: number) {
  return useQuery({
    queryKey: ['opciones-solicitud', anio],
    queryFn: () => api<OpcionesSolicitud>(`/solicitudes-matricula/opciones?anio=${anio}`),
    staleTime: 0,
  })
}

/**
 * Sube un documento como lo hace el navegador (DA-07): pide la URL prefirmada a la API y envía el archivo directo
 * al Gestor de Archivos con la cabecera que esa URL exige.
 */
export async function subirDocumento(tipo: TipoDocumento, archivo: File): Promise<DocumentoSubido> {
  const { datos } = await solicitar<{
    claveObjeto: string
    subida: { url: string; metodo: string; cabeceras: Record<string, string> }
  }>('/solicitudes-matricula/archivos', {
    method: 'POST',
    cuerpo: { nombreArchivo: archivo.name, contentType: archivo.type, tamanoBytes: archivo.size },
  })
  let respuesta: Response
  try {
    respuesta = await fetch(datos.subida.url, { method: datos.subida.metodo, headers: datos.subida.cabeceras, body: archivo })
  } catch {
    throw new ApiError(0, 'No se pudo subir el archivo. Revisa tu conexión.', SIN_CONEXION)
  }
  if (!respuesta.ok) throw new ApiError(respuesta.status, 'No se pudo subir el archivo. Inténtalo de nuevo.', 'ARCHIVO_INVALIDO')
  return { tipo, claveObjeto: datos.claveObjeto, nombreArchivo: archivo.name, contentType: archivo.type, tamanoBytes: archivo.size }
}

export function useRegistrarSolicitud() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (cuerpo: SolicitudNueva) => api<SolicitudRegistrada>('/solicitudes-matricula', { method: 'POST', cuerpo }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['mis-solicitudes'] }),
  })
}

export function useMisSolicitudes() {
  return useQuery({
    queryKey: ['mis-solicitudes'],
    queryFn: () => api<SolicitudPropia[]>('/solicitudes-matricula/mias'),
    staleTime: 0,
  })
}

// ---- UC-03: matrícula de los hijos ----

export interface MatriculaVigente {
  matriculaId: string
  anio: number
  grado: string
  seccion: string
  estado: string
  fechaMatricula: string
}

/** Un estudiante del alcance; `matricula` es nulo si no tiene matrícula vigente (UC-03 FA-2). */
export interface HijoMatricula {
  alumnoId: string
  codigoAlumno: string
  alumno: string
  matricula: MatriculaVigente | null
}

export function useMisHijos() {
  return useQuery({
    queryKey: ['matriculas-mias'],
    queryFn: () => api<HijoMatricula[]>('/matriculas/mias'),
    staleTime: 0,
  })
}

// ---- RF-05: cambio de grado o sección ----

/** La matrícula vigente de un estudiante con las secciones de su año y su cupo. */
export interface MatriculaGestionable {
  matriculaId: string
  alumnoId: string
  alumno: string
  anio: number
  grado: string
  seccionId: string
  seccion: string
  estado: string
  version: number
  secciones: SeccionConVacantes[]
}

export interface ResultadoCambioSeccion {
  matriculaId: string
  alumno: string
  seccionAnterior: string
  seccionNueva: string
  vacantesOrigen: number
  vacantesDestino: number
  sinCambios: boolean
}

export function useMatriculaGestionable(alumnoId: string) {
  return useQuery({
    queryKey: ['matricula-gestionable', alumnoId],
    queryFn: () => api<MatriculaGestionable>(`/matriculas/alumno/${alumnoId}`),
    staleTime: 0,
    retry: false,
  })
}

export function useCambiarSeccion(alumnoId: string, matriculaId: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (seccionId: string) =>
      api<ResultadoCambioSeccion>(`/matriculas/${matriculaId}/seccion`, { method: 'PATCH', cuerpo: { seccionId } }),
    // Con éxito o con 409 (la sección se llenó) las vacantes ya cambiaron: se recarga la lista
    onSettled: () => cliente.invalidateQueries({ queryKey: ['matricula-gestionable', alumnoId] }),
  })
}
