import { Link } from '@tanstack/react-router'
import type { OPCIONES_MENU } from '../menu'

type Opcion = (typeof OPCIONES_MENU)[number]

/** Menú de la intranet en escritorio (≥ 768 px). Las opciones llegan ya filtradas por los permisos (RF-50). */
export function MenuLateral({ opciones }: { opciones: readonly Opcion[] }) {
  return (
    <nav className="menu" aria-label="Principal">
      <Link to="/intranet/inicio" activeProps={{ className: 'activo' }}>
        Inicio
      </Link>
      {opciones.map((o) => (
        <Link key={o.titulo} {...o.enlace} activeProps={{ className: 'activo' }}>
          {o.titulo}
        </Link>
      ))}
    </nav>
  )
}
