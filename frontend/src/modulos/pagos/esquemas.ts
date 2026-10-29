import { z } from 'zod'

const monto = z.string().trim().regex(/^\d{1,10}(\.\d{1,2})?$/, 'Ingresa un monto válido, por ejemplo 350.00.')

export const esquemaLineaCobro = z.object({
  concepto: z.enum(['MATRICULA', 'PENSION', 'CUOTA_INGRESO']),
  periodo: z.string(),
  monto,
  fechaVencimiento: z.string().min(1, 'Indica la fecha de vencimiento.'),
})

/** Configuración de cobros del año (RF-06): penalidad entre 0 y 100 y el cronograma completo. */
export const esquemaCobros = z.object({
  porcentajePenalidad: z
    .string()
    .trim()
    .regex(/^\d{1,3}(\.\d{1,2})?$/, 'Ingresa un porcentaje válido, por ejemplo 5.')
    .refine((v) => Number(v) <= 100, 'La penalidad no puede superar el 100 %.'),
  lineas: z.array(esquemaLineaCobro).min(1, 'El cronograma necesita al menos una línea.'),
})

export type DatosCobros = z.infer<typeof esquemaCobros>
