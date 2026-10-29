import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, createRouter, RouterProvider } from '@tanstack/react-router'
import { render } from '@testing-library/react'
import { routeTree } from '../routeTree.gen'

/**
 * Dibuja la SPA completa (rutas, guardias y layout reales) empezando en `ruta`. La sesión se prepara antes con
 * `guardarSesion`; la API se simula con `servidor.use(...)`.
 */
export async function renderizarRuta(ruta: string) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [ruta] }),
    defaultPreload: false,
  })
  await router.load()
  const resultado = render(
    <QueryClientProvider client={cliente}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return { ...resultado, router, cliente }
}
