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
