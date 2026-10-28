import { Link } from '@tanstack/react-router'
import type { OPCIONES_MENU } from '../menu'

type Opcion = (typeof OPCIONES_MENU)[number]

/** Barra de navegación inferior de móvil (< 768 px); el CSS la oculta en escritorio. */
export function BarraInferiorMovil({ opciones }: { opciones: readonly Opcion[] }) {
  return (
    <nav className="barra-inferior" aria-label="Navegación móvil">
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
