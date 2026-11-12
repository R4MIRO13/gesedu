import { Link } from '@tanstack/react-router'
import type { OPCIONES_MENU } from '../menu'

type Opcion = (typeof OPCIONES_MENU)[number]

/**
 * Menú de la intranet: fijo en escritorio (≥ 768 px) y plegable en móvil (botón ☰). Las opciones llegan ya filtradas por
 * los permisos (RF-50). {@code alNavegar} cierra el menú móvil también al elegir la página en la que ya se está.
 */
export function MenuLateral({
  opciones,
  alNavegar,
}: {
  opciones: readonly Opcion[]
  alNavegar?: () => void
}) {
  return (
    <nav className="menu" aria-label="Principal">
      <Link to="/intranet/inicio" activeProps={{ className: 'activo' }} onClick={alNavegar}>
        Inicio
      </Link>
      {opciones.map((o) => (
        <Link key={o.titulo} {...o.enlace} activeProps={{ className: 'activo' }} onClick={alNavegar}>
          {o.titulo}
        </Link>
      ))}
    </nav>
  )
}
