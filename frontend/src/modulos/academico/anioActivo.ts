import { useQuery } from '@tanstack/react-query'
import { redirect } from '@tanstack/react-router'
import { api } from '../../compartido/api/cliente'
import { queryClient } from '../../compartido/api/queryClient'

/** Año escolar ACTIVO (C-47): el año de trabajo de la intranet, que decide el servidor. */
export interface AnioActivo {
  id: string
  anio: number
  fechaInicio: string
  fechaFin: string
  estado: string
}

const consulta = {
  queryKey: ['anio-activo'],
  queryFn: () => api<AnioActivo>('/anios-escolares/activo'),
  staleTime: 60_000,
}

export function useAnioActivo() {
  return useQuery(consulta)
}

/** Segmento de la URL que significa «el año activo»; el menú lo usa y la ruta lo reemplaza por el año real. */
export const ANIO_ACTIVO = 'activo'

/**
 * Para el `beforeLoad` de las rutas con `$anio`: si la URL trae `activo`, pregunta al servidor cuál es y redirige a
 * la misma dirección con el año real (se conservan los parámetros de búsqueda). Sin año activo, deja pasar la ruta
 * y la página muestra el aviso.
 */
export async function resolverAnioActivo(params: { anio: string }, href: string) {
  if (params.anio !== ANIO_ACTIVO) return
  const activo = await queryClient.fetchQuery(consulta).catch(() => null)
  if (!activo) return
  throw redirect({
    href: href.replace(`/${ANIO_ACTIVO}/`, `/${activo.anio}/`).replace(/\/activo$/, `/${activo.anio}`),
  })
}
