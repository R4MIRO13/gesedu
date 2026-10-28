import { z } from 'zod'

/** Formulario de inicio de sesión (RF-52): solo con correo (C-02). Valida formatos; las reglas las aplica el backend. */
export const esquemaInicioSesion = z.object({
  correo: z.string().trim().min(1, 'Ingresa tu correo.').pipe(z.email('Ingresa un correo válido.')),
  contrasena: z.string().min(1, 'Ingresa tu contraseña.'),
})

export type DatosInicioSesion = z.infer<typeof esquemaInicioSesion>

/** Política de contraseñas (S-01), la misma que valida el backend: 8 a 72 caracteres, con una letra y un número. */
export const MENSAJE_POLITICA_CONTRASENA = 'Usa entre 8 y 72 caracteres, con al menos una letra y un número.'

const contrasenaNueva = z
  .string()
  .min(8, MENSAJE_POLITICA_CONTRASENA)
  .max(72, MENSAJE_POLITICA_CONTRASENA)
  .regex(/[A-Za-z]/, MENSAJE_POLITICA_CONTRASENA)
  .regex(/\d/, MENSAJE_POLITICA_CONTRASENA)

export const esquemaSolicitudRecuperacion = z.object({
  correo: z.string().trim().min(1, 'Ingresa tu correo.').pipe(z.email('Ingresa un correo válido.')),
})

export type DatosSolicitudRecuperacion = z.infer<typeof esquemaSolicitudRecuperacion>

export const esquemaNuevaContrasena = z
  .object({
    nuevaContrasena: contrasenaNueva,
    confirmacion: z.string().min(1, 'Repite la contraseña.'),
  })
  .refine((datos) => datos.nuevaContrasena === datos.confirmacion, {
    path: ['confirmacion'],
    message: 'Las contraseñas no coinciden.',
  })

export type DatosNuevaContrasena = z.infer<typeof esquemaNuevaContrasena>
