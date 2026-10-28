import { Link } from '@tanstack/react-router'
import type { OpcionMenu } from '../menu'

/** Menú de la intranet en escritorio (≥ 768 px). Las opciones llegan ya filtradas por los permisos (RF-50). */
export function MenuLateral({ opciones }: { opciones: readonly OpcionMenu[] }) {
  return (
    <nav className="menu" aria-label="Principal">
      <Link to="/intranet/inicio" activeProps={{ className: 'activo' }}>
        Inicio
      </Link>
      {opciones.map((o) => (
        <Link key={o.to} to={o.to} activeProps={{ className: 'activo' }}>
          {o.titulo}
        </Link>
      ))}
    </nav>
  )
}
