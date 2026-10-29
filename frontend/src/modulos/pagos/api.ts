import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../compartido/api/cliente'

export type ConceptoPago = 'MATRICULA' | 'PENSION' | 'CUOTA_INGRESO'

export const CONCEPTOS: Record<ConceptoPago, string> = {
  MATRICULA: 'Matrícula',
  PENSION: 'Pensión',
  CUOTA_INGRESO: 'Cuota de ingreso',
}

/** Una línea del cronograma de cobros; los montos viajan como texto decimal. */
export interface LineaCobro {
  concepto: ConceptoPago
  periodo: string
  monto: string
  fechaVencimiento: string
}

export interface ConfiguracionCobros {
  anio: number
  porcentajePenalidad: number | null
  lineas: LineaCobro[]
  configurado: boolean
}

export function useConfiguracionCobros(anio: number) {
  return useQuery({
    queryKey: ['cobros', anio],
    queryFn: () => api<ConfiguracionCobros>(`/anios-escolares/${anio}/cobros`),
    staleTime: 0,
  })
}

export function useGuardarCobros(anio: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (cuerpo: { porcentajePenalidad: number; lineas: LineaCobro[] }) =>
      api<ConfiguracionCobros>(`/anios-escolares/${anio}/cobros`, { method: 'PUT', cuerpo }),
    onSuccess: (datos) => cliente.setQueryData(['cobros', anio], datos),
  })
}

// ---- UC-03: obligaciones del apoderado ----

export type EstadoObligacion = 'PENDIENTE' | 'PAGADA' | 'VENCIDA'
export type EstadoRevision = 'PENDIENTE' | 'APROBADO' | 'OBSERVADO' | 'RECHAZADO'

export interface Boleta {
  numero: string
  fechaRegistro: string
  monto: string
}

export interface ComprobanteResumen {
  id: string
  estado: EstadoRevision
  observacion: string | null
  numeroOperacion: string
  fechaPago: string
  montoPresentado: string
  medioPago: string
}

export interface Obligacion {
  id: string
  concepto: ConceptoPago
  periodo: string
  montoBase: string
  montoPenalidad: string
  total: string
  fechaVencimiento: string
  estado: EstadoObligacion
  penalidadAplicadaEn: string | null
  boleta: Boleta | null
  comprobante: ComprobanteResumen | null
  puedePresentarComprobante: boolean
}

export interface ResumenObligaciones {
  totalPagado: string
  totalPendiente: string
  deudaVencida: string
  obligaciones: number
  pagadas: number
  pendientes: number
  vencidas: number
  proximoVencimiento: { concepto: ConceptoPago; periodo: string; fechaVencimiento: string; total: string } | null
}

export interface ObligacionesAlumno {
  alumnoId: string
  codigoAlumno: string
  alumno: string
  obligaciones: Obligacion[]
  resumen: ResumenObligaciones
}

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']

/** «Pensión marzo» para «2027-03»; la matrícula y la cuota de ingreso no llevan mes. */
export function nombreObligacion(concepto: ConceptoPago, periodo: string): string {
  if (concepto !== 'PENSION') return CONCEPTOS[concepto]
  const mes = Number(periodo.slice(5, 7))
  return `${CONCEPTOS.PENSION} ${MESES[mes - 1] ?? periodo}`
}

export function useObligaciones(alumnoId: string | undefined) {
  return useQuery({
    queryKey: ['obligaciones', alumnoId],
    queryFn: () => api<ObligacionesAlumno>(`/alumnos/${alumnoId}/obligaciones`),
    enabled: !!alumnoId,
    staleTime: 0,
  })
}
