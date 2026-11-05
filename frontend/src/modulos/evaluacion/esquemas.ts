import { z } from 'zod'

/** Un peso: número mayor que cero, hasta 100 y con hasta dos decimales (RF-38). */
const peso = z
  .string()
  .trim()
  .min(1, 'Indica el peso')
  .regex(/^\d{1,3}([.,]\d{1,2})?$/, 'Usa un número con hasta dos decimales')
  .refine((v) => Number(v.replace(',', '.')) > 0, 'El peso debe ser mayor que cero')
  .refine((v) => Number(v.replace(',', '.')) <= 100, 'El peso no puede ser mayor que 100')

/** Regla de consolidación y los pesos de los bimestres I a IV (UC-16). */
export const esquemaConfiguracion = z.object({
  regla: z.enum(['PROMEDIO_PONDERADO', 'MODA', 'ULTIMO_REGISTRO']),
  pesos: z.array(z.object({ bimestre: z.number(), peso })),
})

export type DatosConfiguracion = z.infer<typeof esquemaConfiguracion>

/** «2,5» → «2.5»: la API recibe el punto decimal. */
export function pesoParaApi(texto: string): string {
  return texto.trim().replace(',', '.')
}
