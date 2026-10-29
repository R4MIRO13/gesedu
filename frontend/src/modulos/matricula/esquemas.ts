import { z } from 'zod'

/** Confirmación de UC-02 (paso 9): la sección elegida y la aceptación de los costos por el apoderado. */
export const esquemaConfirmacion = z.object({
  seccionId: z.string().min(1, 'Elige la sección en la que se matricula.'),
  costosAceptados: z.boolean().refine((aceptados) => aceptados, 'Marca que el apoderado aceptó los costos.'),
})

export type DatosConfirmacion = z.infer<typeof esquemaConfirmacion>

export const esquemaRechazo = z.object({
  motivo: z.enum(['NO_ADMITIDO', 'DUPLICIDAD', 'DEUDAS', 'SIN_VACANTE', 'VOLUNTAD_APODERADO'], {
    error: 'Elige el motivo del rechazo.',
  }),
  observacion: z.string().trim().max(500, 'La observación admite hasta 500 caracteres.'),
})

export type DatosRechazo = z.infer<typeof esquemaRechazo>

export const esquemaEvaluacion = z.object({
  tipoEstudiante: z.enum(['NUEVO_INGRESO', 'RECURRENTE']),
  estadoAdmision: z.enum(['ADMITIDO', 'NO_ADMITIDO', 'NO_APLICA']),
})

export type DatosEvaluacion = z.infer<typeof esquemaEvaluacion>
