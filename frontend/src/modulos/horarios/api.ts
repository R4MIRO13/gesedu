import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../compartido/api'

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
