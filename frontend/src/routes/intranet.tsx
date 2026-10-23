import { useQueryClient } from '@tanstack/react-query'
import { createFileRoute, Link, Navigate, Outlet, redirect, useNavigate } from '@tanstack/react-router'
import { OPCIONES_MENU } from '../compartido/menu'
import { cerrarSesion, obtenerSesion, ROLES, tienePermiso, useSesion } from '../compartido/sesion'

export const Route = createFileRoute('/intranet')({
  beforeLoad: () => {
    if (!obtenerSesion()) throw redirect({ to: '/login' })
  },
  component: LayoutIntranet,
})

function LayoutIntranet() {
  const sesion = useSesion()
  const navegar = useNavigate()
  const cliente = useQueryClient()

  // Token vencido o 401: la sesión desapareció
  if (!sesion) return <Navigate to="/login" />

  function salir() {
    cerrarSesion()
    cliente.clear()
    void navegar({ to: '/login' })
  }

  const { usuario } = sesion

  return (
    <div className="app">
      <header className="barra">
        <span className="marca">GESEDU</span>
        <nav className="menu" aria-label="Principal">
          <Link to="/intranet/inicio" activeProps={{ className: 'activo' }}>
            Inicio
          </Link>
          {OPCIONES_MENU.filter((o) => tienePermiso(sesion, o.permiso)).map((o) => (
            <Link key={o.to} to={o.to} activeProps={{ className: 'activo' }}>
              {o.titulo}
            </Link>
          ))}
        </nav>
        <div className="usuario">
          <div className="usuario-datos">
            <strong>{usuario.nombres}</strong>
            <span className="muted">{usuario.cargo ?? ROLES[usuario.rol] ?? usuario.rol}</span>
          </div>
          <button className="btn" onClick={salir}>
            Salir
          </button>
        </div>
      </header>
      <main className="contenido">
        <Outlet />
      </main>
    </div>
  )
}
