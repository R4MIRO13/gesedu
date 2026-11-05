import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../compartido/api/cliente'

// ---- UC-07: asistencia del docente ----

export type EstadoAsistencia = 'PUNTUAL' | 'TARDANZA' | 'FALTA'

export const NOMBRE_ESTADO: Record<EstadoAsistencia, string> = {
  PUNTUAL: 'Puntual',
  TARDANZA: 'Tardanza',
  FALTA: 'Falta',
}

/** Una asistencia diaria; `horaRegistro` es nula solo en la FALTA que crea el cierre diario (UC-12). */
export interface RegistroAsistenciaDocente {
  id: string
  fecha: string
  horaRegistro: string | null
  estado: EstadoAsistencia
}

/**
 * Lo que la pantalla «Mi asistencia» muestra. La hora la manda el servidor: la SPA solo la hace avanzar entre consultas
 * para mostrarla, y nunca manda una hora al marcar. `motivo` es el código por el que no se puede marcar (nulo si se puede).
 */
export interface AsistenciaHoy {
  fecha: string
  horaServidor: string
  primerBloque: string | null
  limitePuntual: string | null
  toleranciaMinutos: number
  cierreJornada: string
  puedeMarcar: boolean
  motivo: 'DIA_NO_LECTIVO' | 'JORNADA_CERRADA' | 'SIN_CLASES_PROGRAMADAS' | 'ASISTENCIA_YA_REGISTRADA' | null
  mensaje: string | null
  registro: RegistroAsistenciaDocente | null
}

/** Cada cuánto se vuelve a preguntar al servidor, para que la pantalla note el cierre de la jornada. */
const REFRESCO_HOY_MS = 30_000

export function useAsistenciaHoy() {
  return useQuery({
    queryKey: ['asistencia-docente', 'hoy'],
    queryFn: () => api<AsistenciaHoy>('/asistencia-docente/hoy'),
    refetchInterval: REFRESCO_HOY_MS,
  })
}

export function useMiHistorialAsistencia() {
  return useQuery({
    queryKey: ['asistencia-docente', 'mias'],
    queryFn: () => api<RegistroAsistenciaDocente[]>('/asistencia-docente/mias'),
  })
}

/** Marca la llegada. No lleva cuerpo: la fecha, la hora y el estado los decide el servidor. */
export function useMarcarAsistencia() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: () => api<RegistroAsistenciaDocente>('/asistencia-docente/marcaciones', { method: 'POST' }),
    // También tras un 409: otra solicitud ya la había registrado y la pantalla debe mostrarla
    onSettled: () => cliente.invalidateQueries({ queryKey: ['asistencia-docente'] }),
  })
}

// ---- UC-08: asistencia de alumnos de la sección del tutor ----

export interface AlumnoAsistencia {
  alumnoId: string
  codigo: string
  apellidos: string
  nombres: string
  registroId: string | null
  /** Nulo = sin registro: no equivale a FALTA. */
  estado: EstadoAsistencia | null
}

export interface TotalesAsistencia {
  puntual: number
  tardanza: number
  falta: number
  pendientes: number
}

export interface AsistenciaSeccion {
  codigo: string
  nombre: string
  fecha: string
  horaServidor: string
  /** Falso con la jornada cerrada: la pantalla queda en consulta. */
  registroHabilitado: boolean
  mensaje: string | null
  limiteTardanza: string
  /** Verdadero mientras, a la hora del servidor, aún se puede cambiar una falta a tardanza (UC-09). */
  actualizacionTardanzaHabilitada: boolean
  alumnos: AlumnoAsistencia[]
  totales: TotalesAsistencia
}

export interface MiSeccion {
  codigo: string
  nombre: string
  alumnos: number
}

export interface ItemPaseLista {
  alumnoId: string
  estado: EstadoAsistencia
}

export function useMiSeccion() {
  return useQuery({
    queryKey: ['asistencia-alumnos', 'mi-seccion'],
    queryFn: () => api<MiSeccion>('/asistencia-alumnos/mi-seccion'),
    retry: false,
  })
}

export function useAsistenciaSeccion(codigo: string) {
  return useQuery({
    queryKey: ['asistencia-alumnos', 'seccion', codigo],
    queryFn: () => api<AsistenciaSeccion>(`/secciones/${encodeURIComponent(codigo)}/asistencia`),
    retry: false,
  })
}

/** Guarda el pase de lista completo en un solo lote atómico; la fecha y la hora las pone el servidor. */
export function useRegistrarAsistenciaSeccion(codigo: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (asistencias: ItemPaseLista[]) =>
      api<AsistenciaSeccion>(`/secciones/${encodeURIComponent(codigo)}/asistencia`, { method: 'POST', cuerpo: { asistencias } }),
    // También tras un 409: otra solicitud ya registró y la pantalla debe mostrar lo vigente
    onSettled: () => cliente.invalidateQueries({ queryKey: ['asistencia-alumnos'] }),
  })
}

/** Una asistencia de alumno tras cambiarla; `modificado` es falso si ya estaba en TARDANZA (UC-09 FA-2). */
export interface RegistroAlumno {
  id: string
  alumnoId: string
  fecha: string
  estado: EstadoAsistencia
  actualizadoEn: string
  modificado: boolean
}

/** UC-09: de FALTA a TARDANZA. La hora límite y la fecha las decide el servidor. */
export function useCambiarATardanza() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (registroId: string) =>
      api<RegistroAlumno>(`/asistencia-alumnos/${encodeURIComponent(registroId)}`, { method: 'PATCH', cuerpo: { estado: 'TARDANZA' } }),
    // También tras un error: el plazo pudo vencer o el registro cambiar, y la vista debe mostrar lo vigente
    onSettled: () => cliente.invalidateQueries({ queryKey: ['asistencia-alumnos'] }),
  })
}

// ---- UC-12: asistencia de los docentes por día (administrador) ----

export interface FilaAsistenciaDocente {
  docenteId: string
  nombre: string
  primerBloque: string | null
  horaRegistro: string | null
  /** Nulo = programado sin registro: no es falta. */
  estado: EstadoAsistencia | null
  generadaAlCierre: boolean
}

export interface TotalesDocentes {
  puntual: number
  tardanza: number
  falta: number
  sinRegistrar: number
}

export type EstadoCierre = 'PROCESADO' | 'FALLIDO' | 'PENDIENTE'

export interface AsistenciaDocentesDia {
  fecha: string
  diaLectivo: boolean
  cierre: EstadoCierre
  detalleCierre: string | null
  totales: TotalesDocentes
  docentes: FilaAsistenciaDocente[]
}

/** `fecha` nula = hoy según el servidor. */
export function useAsistenciaDocentesDia(fecha: string | null) {
  return useQuery({
    queryKey: ['asistencia-docentes-dia', fecha],
    queryFn: () => api<AsistenciaDocentesDia>(fecha ? `/asistencia-docente?fecha=${encodeURIComponent(fecha)}` : '/asistencia-docente'),
    retry: false,
  })
}
