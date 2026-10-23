import { createRootRoute, Link, Outlet } from '@tanstack/react-router'

export const Route = createRootRoute({
  component: () => <Outlet />,
  notFoundComponent: () => (
    <main className="centrado">
      <div className="tarjeta">
        <h1>Página no encontrada</h1>
        <p className="muted">La dirección no existe.</p>
        <Link to="/intranet/inicio" className="btn">
          Ir al inicio
        </Link>
      </div>
    </main>
  ),
})
