import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../compartido/api/cliente'

// ---- UC-15: registro de logros por competencia ----

export type NivelLogro = 'AD' | 'A' | 'B' | 'C'

/** La escala literal oficial, de mayor a menor (RF-22). */
export const NIVELES: NivelLogro[] = ['AD', 'A', 'B', 'C']

export const NOMBRE_NIVEL: Record<NivelLogro, string> = {
  AD: 'Logro destacado',
  A: 'Logro esperado',
  B: 'En proceso',
  C: 'En inicio',
}

/** Un curso en una sección que el docente imparte (RF-31). `bimestreVigente` es nulo si hoy no hay bimestre en curso. */
export interface Asignacion {
  id: string
  cursoId: string
  curso: string
  cursoSlug: string
  seccionId: string
  seccion: string
  seccionCodigo: string
  anio: number
  regla: 'PROMEDIO_PONDERADO' | 'MODA' | 'ULTIMO_REGISTRO'
  alumnos: number
  competencias: number
  bimestreVigente: number | null
}

export interface BimestreInfo {
  numero: number
  fechaInicio: string
  fechaFin: string
  vigente: boolean
}

export interface CompetenciaCurso {
  id: string
  codigo: string
  nombre: string
  nivelEsperado: string | null
}

/** Una fila de la rejilla; `logros` va por id de competencia y solo trae las celdas ya calificadas. */
export interface FilaAlumno {
  alumnoId: string
  codigo: string
  apellidos: string
  nombres: string
  logros: Record<string, NivelLogro>
}

export interface AlumnoIncompleto {
  alumnoId: string
  alumno: string
  competencias: string[]
}

export type EstadoConsolidacion = 'COMPLETADA' | 'DESACTUALIZADA'

export interface Rejilla {
  asignacionId: string
  curso: string
  seccion: string
  anio: number
  bimestre: BimestreInfo
  bimestres: BimestreInfo[]
  /** Verdadero solo con el bimestre vigente. */
  editable: boolean
  mensaje: string | null
  consolidacion: EstadoConsolidacion | null
  avisoConsolidacion: string | null
  competencias: CompetenciaCurso[]
  alumnos: FilaAlumno[]
  celdasTotales: number
  celdasCompletas: number
  incompletos: AlumnoIncompleto[]
  aviso: string | null
  /** Celdas que modificó el guardado que produjo esta respuesta. */
  cambios: number
}

export interface CeldaLogro {
  alumnoId: string
  competenciaId: string
  nivel: NivelLogro
}

export function useMisAsignaciones() {
  return useQuery({
    queryKey: ['asignaciones-docentes', 'mias'],
    queryFn: () => api<Asignacion[]>('/asignaciones-docentes/mias'),
  })
}

/** `bimestre` nulo = el vigente (o el último que ya empezó). */
export function useRejilla(asignacionId: string | undefined, bimestre: number | null) {
  return useQuery({
    queryKey: ['asignaciones-docentes', asignacionId, 'registros', bimestre],
    queryFn: () => api<Rejilla>(`/asignaciones-docentes/${asignacionId}/registros${bimestre === null ? '' : `?bimestre=${bimestre}`}`),
    enabled: asignacionId !== undefined,
    retry: false,
  })
}

/** Guarda las celdas indicadas en un solo lote atómico; la respuesta es la rejilla ya actualizada. */
export function useGuardarLogros(asignacionId: string, bimestre: number | null) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (logros: CeldaLogro[]) =>
      api<Rejilla>(`/asignaciones-docentes/${asignacionId}/registros${bimestre === null ? '' : `?bimestre=${bimestre}`}`, {
        method: 'PUT',
        cuerpo: { logros },
      }),
    onSuccess: (rejilla) => cliente.setQueryData(['asignaciones-docentes', asignacionId, 'registros', bimestre], rejilla),
  })
}

export const NUMERO_ROMANO = ['', 'I', 'II', 'III', 'IV'] as const

export function romano(numero: number): string {
  return NUMERO_ROMANO[numero] ?? String(numero)
}
