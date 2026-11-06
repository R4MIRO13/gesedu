import { QueryClient } from '@tanstack/react-query'

/** Cliente único de TanStack Query: también lo usan los `beforeLoad` de las rutas (por ejemplo, el año activo). */
export const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
})
