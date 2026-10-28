import { z } from 'zod'

/** Formulario de inicio de sesión (RF-52): solo con correo (C-02). Valida formatos; las reglas las aplica el backend. */
export const esquemaInicioSesion = z.object({
  correo: z.string().trim().min(1, 'Ingresa tu correo.').pipe(z.email('Ingresa un correo válido.')),
  contrasena: z.string().min(1, 'Ingresa tu contraseña.'),
})

export type DatosInicioSesion = z.infer<typeof esquemaInicioSesion>
