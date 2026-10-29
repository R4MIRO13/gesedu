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

/** Solicitud de matrícula del apoderado (UC-01, pasos 2 a 4). Los documentos adjuntos se validan aparte. */
export const esquemaSolicitud = z.object({
  nombres: z.string().trim().min(1, 'Ingresa los nombres del estudiante.').max(100, 'Admite hasta 100 caracteres.'),
  apellidos: z.string().trim().min(1, 'Ingresa los apellidos del estudiante.').max(100, 'Admite hasta 100 caracteres.'),
  documento: z.string().trim().regex(/^\d{8}$/, 'El DNI debe tener 8 dígitos.'),
  fechaNacimiento: z
    .string()
    .min(1, 'Indica la fecha de nacimiento.')
    .refine((v) => v < new Date().toISOString().slice(0, 10), 'La fecha de nacimiento debe ser pasada.'),
  parentesco: z.string().min(1, 'Elige el parentesco.'),
  telefono: z.string().trim().regex(/^\d[\d ]{6,18}\d$/, 'Ingresa un celular válido, por ejemplo 987 654 321.'),
  tipoEstudiante: z.enum(['NUEVO_INGRESO', 'RECURRENTE']),
  gradoId: z.string().min(1, 'Elige el grado al que postula.'),
  declaracion: z.boolean().refine((v) => v, 'Debes declarar que la información es verídica.'),
})

export type DatosSolicitud = z.infer<typeof esquemaSolicitud>
