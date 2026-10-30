import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../compartido/api/cliente'

export const DIAS = ['LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO'] as const
export type DiaSemana = (typeof DIAS)[number]

export const NOMBRE_DIA: Record<DiaSemana, string> = {
  LUNES: 'Lunes',
  MARTES: 'Martes',
  MIERCOLES: 'Miércoles',
  JUEVES: 'Jueves',
  VIERNES: 'Viernes',
  SABADO: 'Sábado',
  DOMINGO: 'Domingo',
}

export interface BloqueHorario {
  id: string
  diaSemana: DiaSemana
  horaInicio: string
  horaFin: string
}

export interface NuevoBloque {
  diaSemana: DiaSemana
  horaInicio: string
  horaFin: string
}

export function useBloques(anio: number) {
  return useQuery({
    queryKey: ['bloques', anio],
    queryFn: () => api<BloqueHorario[]>(`/anios-escolares/${anio}/bloques`),
  })
}

export function useCrearBloque(anio: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (bloque: NuevoBloque) =>
      api<BloqueHorario>(`/anios-escolares/${anio}/bloques`, { method: 'POST', cuerpo: bloque }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['bloques', anio] }),
  })
}

// --- UC-21, Elaborar horario ---------------------------------------------------------------------------------

export type Perspectiva = 'SECCION' | 'DOCENTE'

export interface SeccionHorario {
  id: string
  nombre: string
}

export interface DocenteHorario {
  id: string
  nombre: string
}

export interface Asignacion {
  id: string
  bloqueId: string
  seccionId: string
  seccion: string
  cursoId: string
  curso: string
  docenteId: string
  docente: string
}

export interface CargaCurso {
  cursoId: string
  curso: string
  horasAsignadas: number
  horasRequeridas: number
}

export interface CargaSeccion {
  cursos: CargaCurso[]
  totalAsignadas: number
  totalRequeridas: number
}

/** El borrador del año para una perspectiva; `version` va en `If-Match` al editarlo. */
export interface Borrador {
  id: string
  anio: number
  version: number
  perspectiva: Perspectiva
  seccionId: string | null
  docenteId: string | null
  bloques: BloqueHorario[]
  secciones: SeccionHorario[]
  docentes: DocenteHorario[]
  asignaciones: Asignacion[]
  carga: CargaSeccion | null
}

export interface OpcionDocente {
  docenteId: string
  nombre: string
  disponible: boolean
  ocupadoEn: string | null
  detalleOcupado: string | null
}

export interface Opciones {
  cursos: CargaCurso[]
  docentes: OpcionDocente[]
}

export interface ConflictoHorario {
  tipo: 'DOCENTE_NO_ASOCIADO' | 'DOCENTE_NO_DISPONIBLE' | 'CRUCE_DOCENTE' | 'CRUCE_SECCION'
  mensaje: string
  asignacionId: string | null
  seccionId: string | null
  docenteId: string | null
}

export interface Validacion {
  valido: boolean
  conflictos: ConflictoHorario[]
}

export interface SolicitudAsignacion {
  bloqueId: string
  seccionId: string
  cursoId: string
  docenteId: string
  asignacionId?: string
}

export function useBorrador(anio: number, perspectiva: Perspectiva, seccionId?: string, docenteId?: string) {
  return useQuery({
    queryKey: ['borrador', anio, perspectiva, perspectiva === 'SECCION' ? seccionId : docenteId],
    queryFn: () => {
      const parametros = new URLSearchParams({ perspectiva })
      if (perspectiva === 'SECCION' && seccionId) parametros.set('seccionId', seccionId)
      if (perspectiva === 'DOCENTE' && docenteId) parametros.set('docenteId', docenteId)
      return api<Borrador>(`/horarios/${anio}/borrador?${parametros}`)
    },
    // Siempre se quiere la versión más reciente: es la que se manda en If-Match
    staleTime: 0,
  })
}

export function useOpciones(
  anio: number,
  consulta: { bloqueId: string; seccionId: string; cursoId?: string; asignacionId?: string } | null,
) {
  return useQuery({
    queryKey: ['opciones', anio, consulta],
    enabled: consulta !== null,
    queryFn: () => {
      const parametros = new URLSearchParams({ bloqueId: consulta!.bloqueId, seccionId: consulta!.seccionId })
      if (consulta!.cursoId) parametros.set('cursoId', consulta!.cursoId)
      if (consulta!.asignacionId) parametros.set('asignacionId', consulta!.asignacionId)
      return api<Opciones>(`/horarios/${anio}/borrador/opciones?${parametros}`)
    },
  })
}

/**
 * Validación en vivo (RF-42): se consulta al elegir curso y docente en un bloque. No guarda nada. Es una consulta
 * con la solicitud en la clave, así una respuesta tardía de una selección anterior nunca pisa a la actual.
 */
export function useValidacion(anio: number, solicitud: SolicitudAsignacion | null) {
  return useQuery({
    queryKey: ['validacion', anio, solicitud],
    enabled: solicitud !== null,
    staleTime: 0,
    queryFn: () =>
      api<Validacion>(`/horarios/${anio}/borrador/validaciones`, { method: 'POST', cuerpo: solicitud }),
  })
}

const etag = (version: number) => `"${version}"`

export function useGuardarAsignacion(anio: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ solicitud, version, asignacionId }: {
      solicitud: SolicitudAsignacion
      version: number
      asignacionId?: string
    }) =>
      api<Asignacion>(
        asignacionId
          ? `/horarios/${anio}/borrador/asignaciones/${asignacionId}`
          : `/horarios/${anio}/borrador/asignaciones`,
        {
          method: asignacionId ? 'PUT' : 'POST',
          cuerpo: solicitud,
          cabeceras: { 'If-Match': etag(version) },
        },
      ),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['borrador', anio] }),
  })
}

export function useEliminarAsignacion(anio: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ asignacionId, version }: { asignacionId: string; version: number }) =>
      api<void>(`/horarios/${anio}/borrador/asignaciones/${asignacionId}`, {
        method: 'DELETE',
        cabeceras: { 'If-Match': etag(version) },
      }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['borrador', anio] }),
  })
}

// --- Configuración de horarios del año: periodo, docentes y cargas (RF-46, RF-47, RF-43) ---------------------

export interface PeriodoDisponibilidad {
  anio: number
  configurado: boolean
  fechaHoraInicio: string | null
  fechaHoraFin: string | null
  estado: 'HABILITADO' | 'CERRADO' | null
  /** Lo decide el servidor: habilitado y dentro de las fechas. */
  abierto: boolean
}

export interface CursoOpcion {
  id: string
  nombre: string
}

export interface Vinculacion {
  docenteId: string
  nombre: string
  vinculacionId: string | null
  vinculado: boolean
  habilitado: boolean
  cargaSemanalReferencia: number
  cursoIds: string[]
  disponibilidadConfirmada: boolean
  bloquesDisponibles: number
  ultimaConfirmacion: string | null
}

export interface VinculacionesAnio {
  anio: number
  estado: 'PLANIFICADO' | 'ACTIVO' | 'CERRADO'
  docentes: Vinculacion[]
  cursos: CursoOpcion[]
}

export interface CeldaCarga {
  gradoId: string
  bloques: number
  personalizada: boolean
}

export interface FilaCarga {
  cursoId: string
  curso: string
  horasLectivas: number
  grados: CeldaCarga[]
}

export interface CargasHorarias {
  anio: number
  estado: 'PLANIFICADO' | 'ACTIVO' | 'CERRADO'
  bloquesPorSemana: number
  grados: { id: string; numeroNivel: number }[]
  cursos: FilaCarga[]
  totales: { gradoId: string; total: number; excedeLaSemana: boolean }[]
}

export function usePeriodoDisponibilidad(anio: number) {
  return useQuery({
    queryKey: ['periodo-disponibilidad', anio],
    queryFn: () => api<PeriodoDisponibilidad>(`/anios-escolares/${anio}/periodo-disponibilidad`),
    staleTime: 0,
  })
}

export function useGuardarPeriodo(anio: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (cuerpo: { fechaHoraInicio: string; fechaHoraFin: string; habilitado: boolean }) =>
      api<PeriodoDisponibilidad>(`/anios-escolares/${anio}/periodo-disponibilidad`, { method: 'PUT', cuerpo }),
    onSuccess: (datos) => cliente.setQueryData(['periodo-disponibilidad', anio], datos),
  })
}

export function useVinculaciones(anio: number) {
  return useQuery({
    queryKey: ['vinculaciones', anio],
    queryFn: () => api<VinculacionesAnio>(`/anios-escolares/${anio}/vinculaciones-docentes`),
    staleTime: 0,
  })
}

export function useGuardarVinculacion(anio: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ docenteId, ...cuerpo }: { docenteId: string; habilitado: boolean; cargaSemanalReferencia: number; cursoIds: string[] }) =>
      api<Vinculacion>(`/anios-escolares/${anio}/vinculaciones-docentes/${docenteId}`, { method: 'PUT', cuerpo }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['vinculaciones', anio] }),
  })
}

export function useCargasHorarias(anio: number) {
  return useQuery({
    queryKey: ['cargas-horarias', anio],
    queryFn: () => api<CargasHorarias>(`/anios-escolares/${anio}/cargas-horarias`),
    staleTime: 0,
  })
}

export function useGuardarCargas(anio: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (cargas: { gradoId: string; cursoId: string; bloques: number }[]) =>
      api<CargasHorarias>(`/anios-escolares/${anio}/cargas-horarias`, { method: 'PUT', cuerpo: { cargas } }),
    onSuccess: (datos) => cliente.setQueryData(['cargas-horarias', anio], datos),
  })
}

export function useEliminarBloque(anio: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/anios-escolares/${anio}/bloques/${id}`, { method: 'DELETE' }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['bloques', anio] }),
  })
}
