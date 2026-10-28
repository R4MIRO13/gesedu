import { useMutation } from '@tanstack/react-query'
import { api } from '../../compartido/api/cliente'
import type { SesionIniciada } from '../../compartido/auth/sesion'

export function useIniciarSesion() {
  return useMutation({
    mutationFn: (datos: { correo: string; contrasena: string }) =>
      api<SesionIniciada>('/auth/inicio-sesion', { method: 'POST', cuerpo: datos }),
  })
}

/** Pide el enlace de recuperación. La respuesta es la misma exista o no la cuenta (RF-52). */
export function useSolicitarRecuperacion() {
  return useMutation({
    mutationFn: (datos: { correo: string }) =>
      api<{ mensaje: string }>('/auth/recuperacion', { method: 'POST', cuerpo: datos }),
  })
}

/** Define la contraseña nueva con el token del enlace del correo (un solo uso, 30 minutos). */
export function useRestablecerContrasena() {
  return useMutation({
    mutationFn: (datos: { token: string; nuevaContrasena: string }) =>
      api<void>('/auth/recuperacion/confirmacion', { method: 'POST', cuerpo: datos }),
  })
}
