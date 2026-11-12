import { useQueryClient } from '@tanstack/react-query'
import { Navigate, useNavigate, useRouterState } from '@tanstack/react-router'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { cerrarSesionRemota } from '../auth/servicioSesion'
import { ROLES, tienePermiso, useSesion } from '../auth/sesion'
import { OPCIONES_MENU } from '../menu'
import { MenuLateral } from './MenuLateral'

/**
 * Marco de la intranet (RNF-02): menú lateral fijo en escritorio (≥ 768 px) y, en móvil, el mismo menú plegable que se
 * abre con el botón ☰ de la cabecera compacta. Las opciones salen de los permisos de la sesión; la SPA solo oculta, el
 * acceso real lo decide el backend.
 */
export function Layout({ children }: { children: ReactNode }) {
  const sesion = useSesion()
  const navegar = useNavigate()
  const cliente = useQueryClient()
  const ruta = useRouterState({ select: (s) => s.location.pathname })
  // El menú queda abierto solo en la ruta donde se abrió: navegar lo cierra sin un efecto adicional
  const [abiertoEn, setAbiertoEn] = useState<string | null>(null)
  const menuAbierto = abiertoEn === ruta
  const boton = useRef<HTMLButtonElement>(null)
  const panel = useRef<HTMLElement>(null)
  const estabaAbierto = useRef(false)

  function cerrar() {
    setAbiertoEn(null)
  }

  // Al abrir, el foco pasa al primer enlace; al cerrar, vuelve al botón ☰. Escape cierra.
  useEffect(() => {
    if (menuAbierto) {
      panel.current?.querySelector<HTMLElement>('a')?.focus()
      const alPulsar = (e: KeyboardEvent) => {
        if (e.key === 'Escape') setAbiertoEn(null)
      }
      document.addEventListener('keydown', alPulsar)
      estabaAbierto.current = true
      return () => document.removeEventListener('keydown', alPulsar)
    }
    if (estabaAbierto.current) {
      estabaAbierto.current = false
      boton.current?.focus()
    }
  }, [menuAbierto])

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
      <aside
        id="menu-principal"
        ref={panel}
        className="lateral"
        data-abierto={menuAbierto ? 'true' : 'false'}
      >
        <span className="marca">GESEDU</span>
        <MenuLateral opciones={opciones} alNavegar={cerrar} />
      </aside>
      {menuAbierto && (
        <div className="menu-fondo" data-testid="menu-fondo" aria-hidden="true" onClick={cerrar} />
      )}
      <header className="cabecera">
        <button
          ref={boton}
          type="button"
          className="btn btn-menu"
          aria-label="Menú"
          aria-expanded={menuAbierto}
          aria-controls="menu-principal"
          onClick={() => setAbiertoEn(menuAbierto ? null : ruta)}
        >
          <span aria-hidden="true">☰</span>
        </button>
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
    </div>
  )
}
