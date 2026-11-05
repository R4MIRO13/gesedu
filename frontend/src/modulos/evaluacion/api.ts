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

// ---- UC-16: regla de consolidación y pesos por bimestre ----

export type TipoRegla = 'PROMEDIO_PONDERADO' | 'MODA' | 'ULTIMO_REGISTRO'

export const NOMBRE_REGLA: Record<TipoRegla, string> = {
  PROMEDIO_PONDERADO: 'Promedio ponderado',
  MODA: 'Moda',
  ULTIMO_REGISTRO: 'Último registro',
}

export interface ReglaConsolidacion {
  tipo: TipoRegla
  nombre: string
  descripcion: string
  porDefecto: boolean
  /** Lo calcula el servidor con el motor real: bimestres I a IV con estos niveles dan este resultado. */
  ejemplo: { niveles: NivelLogro[]; resultado: NivelLogro }
}

export interface PesoBimestre {
  bimestre: number
  /** Texto decimal, por ejemplo «1.00». */
  peso: string
  personalizado: boolean
}

export interface ConfiguracionCurso {
  asignacionId: string
  curso: string
  seccion: string
  anio: number
  regla: TipoRegla
  reglas: ReglaConsolidacion[]
  pesos: PesoBimestre[]
  competencias: CompetenciaCurso[]
  /** Falso con el año cerrado. */
  editable: boolean
  cambios: number
}

export interface GuardarConfiguracion {
  regla: TipoRegla
  /** El conjunto completo de pesos; los bimestres omitidos pesan 1. Sin él no se tocan. */
  pesos?: { bimestre: number; peso: string }[]
}

export function useConfiguracionCurso(asignacionId: string | undefined) {
  return useQuery({
    queryKey: ['asignaciones-docentes', asignacionId, 'configuracion'],
    queryFn: () => api<ConfiguracionCurso>(`/asignaciones-docentes/${asignacionId}/configuracion`),
    enabled: asignacionId !== undefined,
    retry: false,
  })
}

export function useGuardarConfiguracion(asignacionId: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (cuerpo: GuardarConfiguracion) =>
      api<ConfiguracionCurso>(`/asignaciones-docentes/${asignacionId}/configuracion`, { method: 'PUT', cuerpo }),
    onSuccess: (configuracion) => {
      cliente.setQueryData(['asignaciones-docentes', asignacionId, 'configuracion'], configuracion)
      // La lista de «mis cursos» muestra la regla de cada curso
      return cliente.invalidateQueries({ queryKey: ['asignaciones-docentes', 'mias'] })
    },
  })
}

// ---- UC-17: notas finales del curso ----

/** Las notas de un alumno en una competencia: el logro de cada bimestre (por número) y la nota anual si ya se calculó. */
export interface CeldaNota {
  bimestres: Record<string, NivelLogro>
  anual: NivelLogro | null
}

export interface FilaNotas {
  alumnoId: string
  codigo: string
  apellidos: string
  nombres: string
  /** Por id de competencia. */
  competencias: Record<string, CeldaNota>
}

export interface ResultadoCalculo {
  creadas: number
  actualizadas: number
  sinCambios: number
  sinNota: number
}

export interface NotasFinales {
  asignacionId: string
  curso: string
  seccion: string
  anio: number
  regla: TipoRegla
  bimestres: number[]
  competencias: CompetenciaCurso[]
  alumnos: FilaNotas[]
  calculadas: boolean
  /** Lo registrado después del último cálculo (logros, regla o pesos) daría otras notas. */
  desactualizada: boolean
  /** Falso con el año cerrado. */
  editable: boolean
  celdasSinNota: number
  /** Solo viene en la respuesta del cálculo. */
  calculo: ResultadoCalculo | null
}

export function useNotasFinales(asignacionId: string | undefined) {
  return useQuery({
    queryKey: ['asignaciones-docentes', asignacionId, 'notas-finales'],
    queryFn: () => api<NotasFinales>(`/asignaciones-docentes/${asignacionId}/notas-finales`),
    enabled: asignacionId !== undefined,
    retry: false,
  })
}

/** Calcula la nota anual de cada competencia con la regla del curso; repetirlo no cambia nada si los datos son iguales. */
export function useCalcularNotasFinales(asignacionId: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: () => api<NotasFinales>(`/asignaciones-docentes/${asignacionId}/notas-finales/calculo`, { method: 'POST' }),
    onSuccess: (notas) => cliente.setQueryData(['asignaciones-docentes', asignacionId, 'notas-finales'], notas),
  })
}

// ---- UC-18: consolidar las notas de un bimestre (administrador) ----

export type EstadoConsolidacionBimestre = 'SIN_CONSOLIDAR' | 'COMPLETADA' | 'DESACTUALIZADA'

export interface CursoAvance {
  asignacionId: string
  curso: string
  seccion: string
  docente: string
  celdasTotales: number
  celdasCompletas: number
  porcentaje: number
  completo: boolean
}

export interface ResultadoConsolidacion {
  creadas: number
  actualizadas: number
  sinCambios: number
  libretas: number
  finalizada: boolean
  notasAnuales: ResultadoCalculo | null
}

export interface AvanceBimestre {
  anio: number
  bimestre: { numero: number; fechaInicio: string; fechaFin: string; ultimo: boolean }
  bimestres: { numero: number; estado: EstadoConsolidacionBimestre }[]
  estado: EstadoConsolidacionBimestre
  fechaConsolidacion: string | null
  totales: { cursos: number; completos: number; incompletos: number }
  cursos: CursoAvance[]
  puedeConsolidar: boolean
  motivo: string | null
  docentesPendientes: string[]
  /** Falso con el año cerrado. */
  editable: boolean
  /** Solo viene en la respuesta de consolidar. */
  resultado: ResultadoConsolidacion | null
}

export function useAvanceBimestre(anio: number, bimestre: number) {
  return useQuery({
    queryKey: ['consolidacion', anio, bimestre],
    queryFn: () => api<AvanceBimestre>(`/anios-escolares/${anio}/bimestres/${bimestre}/avance`),
    retry: false,
  })
}

/** Consolida el bimestre; repetirlo reemplaza las notas del bimestre, no las duplica. */
export function useConsolidarBimestre(anio: number, bimestre: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: () => api<AvanceBimestre>(`/anios-escolares/${anio}/bimestres/${bimestre}/consolidacion`, { method: 'POST' }),
    onSuccess: (avance) => cliente.setQueryData(['consolidacion', anio, bimestre], avance),
    // También tras un error: el avance pudo cambiar (un docente completó o editó notas) y la pantalla debe mostrarlo
    onSettled: () => cliente.invalidateQueries({ queryKey: ['consolidacion', anio] }),
  })
}
