import { Link } from '@tanstack/react-router'
import type { OpcionMenu } from '../menu'

/** Barra de navegación inferior de móvil (< 768 px); el CSS la oculta en escritorio. */
export function BarraInferiorMovil({ opciones }: { opciones: readonly OpcionMenu[] }) {
  return (
    <nav className="barra-inferior" aria-label="Navegación móvil">
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
