import { z } from 'zod'
import { hoyLima } from '../../compartido/formato'

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

/** Comprobante de pago del apoderado (UC-04, paso 4); el voucher se valida aparte. */
export const esquemaComprobante = z.object({
  numeroOperacion: z
    .string()
    .trim()
    .min(1, 'Ingresa el número de operación.')
    .max(40, 'Admite hasta 40 caracteres.')
    .regex(/^[A-Za-z0-9-]+$/, 'Usa solo letras, números y guiones.'),
  fechaPago: z
    .string()
    .min(1, 'Indica la fecha de pago.')
    .refine((v) => v <= hoyLima(), 'La fecha de pago no puede ser futura.'),
  montoPagado: z
    .string()
    .trim()
    .regex(/^\d{1,10}(\.\d{1,2})?$/, 'Ingresa un monto válido, por ejemplo 350.00.')
    .refine((v) => Number(v) > 0, 'El monto debe ser mayor que cero.'),
  medioPago: z.string().trim().min(1, 'Indica el banco o medio de pago.').max(30, 'Admite hasta 30 caracteres.'),
})

export type DatosComprobante = z.infer<typeof esquemaComprobante>
