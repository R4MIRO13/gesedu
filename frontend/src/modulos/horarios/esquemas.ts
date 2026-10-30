import { z } from 'zod'

/** Periodo de registro de disponibilidad (RF-46): solo formato; el backend decide si está abierto. */
export const esquemaPeriodo = z
  .object({
    inicio: z.string().min(1, 'Indica cuándo empieza el periodo.'),
    fin: z.string().min(1, 'Indica cuándo termina el periodo.'),
    habilitado: z.boolean(),
  })
  .refine((v) => !v.inicio || !v.fin || v.fin > v.inicio, { path: ['fin'], message: 'El periodo debe terminar después de empezar.' })

export type DatosPeriodo = z.infer<typeof esquemaPeriodo>

/** Vinculación del docente con el año (RF-47): carga de referencia y cursos que puede dictar. */
export const esquemaVinculacion = z.object({
  habilitado: z.boolean(),
  cargaSemanalReferencia: z
    .string()
    .trim()
    .regex(/^\d{1,2}$/, 'Ingresa la carga semanal (un número entero).')
    .refine((v) => Number(v) <= 60, 'La carga semanal no puede pasar de 60 bloques.'),
  cursoIds: z.array(z.string()),
})

export type DatosVinculacion = z.infer<typeof esquemaVinculacion>
