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

/** Curso con sus competencias (UC-14, pasos 6 a 11): solo formato; el backend decide duplicados y año cerrado. */
export const esquemaCurso = z.object({
  nombre: z.string().trim().min(1, 'Ingresa el nombre del curso.').max(80, 'Admite hasta 80 caracteres.'),
  horasLectivas: z
    .string()
    .trim()
    .regex(/^\d{1,2}$/, 'Ingresa las horas semanales (un número entero).')
    .refine((v) => Number(v) >= 1 && Number(v) <= 40, 'Las horas deben estar entre 1 y 40.'),
  competencias: z
    .array(
      z.object({
        id: z.string().optional(),
        codigo: z.string().optional(),
        nombre: z.string().trim().min(1, 'Escribe el nombre de la competencia.').max(160, 'Admite hasta 160 caracteres.'),
        nivelEsperado: z.string().optional(),
      }),
    )
    .min(1, 'El curso necesita al menos una competencia.')
    .max(12, 'Un curso admite hasta 12 competencias.'),
})

export type DatosCurso = z.infer<typeof esquemaCurso>
