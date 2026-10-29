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
