import type { ReactNode } from 'react'
import { useAnioActivo } from '../../modulos/academico/anioActivo'

/** Dibuja la pantalla con el año escolar activo que informa la API (C-47); sin él, explica qué falta. */
export function ConAnioActivo({ children }: { children: (anio: number) => ReactNode }) {
  const activo = useAnioActivo()
  if (activo.isPending) return <p className="muted">Cargando…</p>
  if (activo.isError) {
    return (
      <p className="alerta" role="status">
        No hay un año escolar activo. Un administrador debe activar el año en curso desde «Año escolar».
      </p>
    )
  }
  return <>{children(activo.data.anio)}</>
}
