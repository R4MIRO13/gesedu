import { useMutation } from '@tanstack/react-query'
import { api } from '../../compartido/api'
import type { SesionIniciada } from '../../compartido/sesion'

export function useIniciarSesion() {
  return useMutation({
    mutationFn: (datos: { correo: string; contrasena: string }) =>
      api<SesionIniciada>('/auth/inicio-sesion', { method: 'POST', cuerpo: datos }),
  })
}
