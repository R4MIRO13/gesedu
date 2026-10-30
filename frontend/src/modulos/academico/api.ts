import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../compartido/api/cliente'

// ---- UC-13: año escolar y bimestres ----

export interface Bimestre {
  numero: number
  fechaInicio: string
  fechaFin: string
}

export type EstadoAnio = 'PLANIFICADO' | 'ACTIVO' | 'CERRADO'

export interface AnioResumen {
  id: string
  anio: number
  fechaInicio: string
  fechaFin: string
  estado: EstadoAnio
}

export interface AnioDetalle extends AnioResumen {
  bimestres: Bimestre[]
}

export type TipoProblemaAnio =
  | 'ANIO_FUERA_DE_DURACION'
  | 'BIMESTRE_FECHAS_INVALIDAS'
  | 'BIMESTRE_FUERA_DEL_ANIO'
  | 'BIMESTRES_SUPERPUESTOS'
  | 'BIMESTRES_DESORDENADOS'

/** Un problema de las fechas; `desde` y `hasta` marcan el tramo en conflicto en la línea de tiempo. */
export interface ProblemaAnio {
  tipo: TipoProblemaAnio
  bimestre: number | null
  otroBimestre: number | null
  desde: string | null
  hasta: string | null
  mensaje: string
}

export interface ValidacionAnio {
  valido: boolean
  duracion: { meses: number; dias: number }
  problemas: ProblemaAnio[]
}

export interface DatosAnioNuevo {
  fechaInicio: string
  fechaFin: string
  bimestres: Bimestre[]
}

export const ESTADO_ANIO: Record<EstadoAnio, string> = {
  PLANIFICADO: 'Planificado',
  ACTIVO: 'Activo',
  CERRADO: 'Cerrado',
}

export function useAnios() {
  return useQuery({ queryKey: ['anios'], queryFn: () => api<AnioResumen[]>('/anios-escolares'), staleTime: 0 })
}

export function useAnio(anio: number) {
  return useQuery({ queryKey: ['anio', anio], queryFn: () => api<AnioDetalle>(`/anios-escolares/${anio}`) })
}

/**
 * Validación en vivo (pasos 4 y 7): el backend es quien decide si las fechas sirven. Sin bimestres solo juzga el
 * año; se consulta cuando las fechas ya tienen forma de fecha.
 */
export function useValidacionAnio(fechaInicio: string, fechaFin: string, bimestres: Bimestre[] | null) {
  const completas = /^\d{4}-\d{2}-\d{2}$/.test(fechaInicio) && /^\d{4}-\d{2}-\d{2}$/.test(fechaFin)
  const conFechas = bimestres?.filter((b) => /^\d{4}-\d{2}-\d{2}$/.test(b.fechaInicio) && /^\d{4}-\d{2}-\d{2}$/.test(b.fechaFin))
  return useQuery({
    queryKey: ['validacion-anio', fechaInicio, fechaFin, conFechas ?? null],
    queryFn: () =>
      api<ValidacionAnio>('/anios-escolares/validaciones', {
        method: 'POST',
        cuerpo: { fechaInicio, fechaFin, ...(conFechas && conFechas.length > 0 ? { bimestres: conFechas } : {}) },
      }),
    enabled: completas,
    placeholderData: keepPreviousData,
    staleTime: 0,
  })
}

export function useCrearAnio() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (cuerpo: DatosAnioNuevo) => api<AnioDetalle>('/anios-escolares', { method: 'POST', cuerpo }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['anios'] }),
  })
}

// ---- UC-14: cursos y competencias ----

export interface Competencia {
  id: string
  codigo: string
  nombre: string
  nivelEsperado: string | null
  orden: number
}

export interface Curso {
  id: string
  nombre: string
  slug: string
  horasLectivas: number
  habilitado: boolean
  competencias: Competencia[]
}

export interface CursosAnio {
  anio: number
  estado: EstadoAnio
  totalHoras: number
  cursos: Curso[]
}

/** Competencia que se envía: sin `id` es nueva, con `id` edita la existente y las que faltan se quitan. */
export interface CompetenciaSolicitud {
  id?: string
  nombre: string
  nivelEsperado?: string | null
}

export interface CursoSolicitud {
  nombre: string
  horasLectivas: number
  competencias: CompetenciaSolicitud[]
}

export interface CursoCopiable {
  id: string
  nombre: string
  horasLectivas: number
  competencias: Competencia[]
  yaExiste: boolean
}

export interface CopiaAnterior {
  anioOrigen: number | null
  cursos: CursoCopiable[]
}

export interface ResultadoCopia {
  anioOrigen: number
  copiados: Curso[]
  omitidos: string[]
}

export function useCursos(anio: number) {
  return useQuery({ queryKey: ['cursos', anio], queryFn: () => api<CursosAnio>(`/anios-escolares/${anio}/cursos`), staleTime: 0 })
}

export function useCopiaAnterior(anio: number, habilitada: boolean) {
  return useQuery({
    queryKey: ['cursos-copia', anio],
    queryFn: () => api<CopiaAnterior>(`/anios-escolares/${anio}/cursos/copia-anterior`),
    enabled: habilitada,
    staleTime: 0,
  })
}

function invalidarCursos(cliente: ReturnType<typeof useQueryClient>, anio: number) {
  return Promise.all([
    cliente.invalidateQueries({ queryKey: ['cursos', anio] }),
    cliente.invalidateQueries({ queryKey: ['cursos-copia', anio] }),
  ])
}

export function useCrearCurso(anio: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (cuerpo: CursoSolicitud) => api<Curso>(`/anios-escolares/${anio}/cursos`, { method: 'POST', cuerpo }),
    onSuccess: () => invalidarCursos(cliente, anio),
  })
}

export function useModificarCurso(anio: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...cuerpo }: CursoSolicitud & { id: string }) => api<Curso>(`/cursos/${id}`, { method: 'PUT', cuerpo }),
    onSuccess: () => invalidarCursos(cliente, anio),
  })
}

export function useEliminarCurso(anio: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/cursos/${id}`, { method: 'DELETE' }),
    onSuccess: () => invalidarCursos(cliente, anio),
  })
}

export function useCopiarCursos(anio: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (cursoIds: string[]) =>
      api<ResultadoCopia>(`/anios-escolares/${anio}/cursos/copia-anterior`, { method: 'POST', cuerpo: { cursoIds } }),
    onSuccess: () => invalidarCursos(cliente, anio),
  })
}
