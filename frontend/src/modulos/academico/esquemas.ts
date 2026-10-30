import { z } from 'zod'

const fecha = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Indica la fecha.')

/** Fechas del año escolar (paso 1 de UC-13): solo formato; la duración la juzga el backend. */
export const esquemaFechasAnio = z
  .object({ fechaInicio: fecha, fechaFin: fecha })
  .refine((v) => v.fechaFin > v.fechaInicio, { path: ['fechaFin'], message: 'La fecha de fin debe ser posterior a la de inicio.' })

export type DatosFechasAnio = z.infer<typeof esquemaFechasAnio>

const bimestre = z
  .object({ numero: z.number(), fechaInicio: fecha, fechaFin: fecha })
  .refine((v) => v.fechaFin > v.fechaInicio, { path: ['fechaFin'], message: 'El fin debe ser posterior al inicio.' })

/** Año con sus cuatro bimestres (paso 2 de UC-13). */
export const esquemaAnioNuevo = z.object({
  fechaInicio: fecha,
  fechaFin: fecha,
  bimestres: z.array(bimestre).length(4),
})

export type DatosAnioNuevoForm = z.infer<typeof esquemaAnioNuevo>
