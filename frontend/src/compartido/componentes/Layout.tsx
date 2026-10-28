import { useQueryClient } from '@tanstack/react-query'
import { Navigate, useNavigate } from '@tanstack/react-router'
import type { ReactNode } from 'react'
import { cerrarSesionRemota } from '../auth/servicioSesion'
import { ROLES, tienePermiso, useSesion } from '../auth/sesion'
import { OPCIONES_MENU } from '../menu'
import { BarraInferiorMovil } from './BarraInferiorMovil'
import { MenuLateral } from './MenuLateral'

/**
 * Marco de la intranet: menú lateral (escritorio), barra inferior (móvil) y cabecera con el usuario. Las
 * opciones salen de los permisos de la sesión; la SPA solo oculta, el acceso real lo decide el backend.
 */
export function Layout({ children }: { children: ReactNode }) {
  const sesion = useSesion()
  const navegar = useNavigate()
  const cliente = useQueryClient()

  // La sesión se cerró (token no renovable): de vuelta al login
  if (!sesion) return <Navigate to="/login" />

  const opciones = OPCIONES_MENU.filter((o) => tienePermiso(sesion, o.permiso))
  const { usuario } = sesion

  async function salir() {
    await cerrarSesionRemota()
    cliente.clear()
    await navegar({ to: '/login' })
  }

  return (
    <div className="app">
      <aside className="lateral">
        <span className="marca">GESEDU</span>
        <MenuLateral opciones={opciones} />
      </aside>
      <header className="cabecera">
        <span className="marca">GESEDU</span>
        <div className="usuario">
          <div className="usuario-datos">
            <strong>{usuario.nombres}</strong>
            <span className="muted">{usuario.cargo ?? ROLES[usuario.rol] ?? usuario.rol}</span>
          </div>
          <button className="btn" onClick={() => void salir()}>
            Salir
          </button>
        </div>
      </header>
      <main className="principal">
        <div className="contenido">{children}</div>
      </main>
      <BarraInferiorMovil opciones={opciones} />
    </div>
  )
}
