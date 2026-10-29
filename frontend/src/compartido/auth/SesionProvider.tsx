import { useEffect, useState, type ReactNode } from 'react'
import { restaurarSesion } from './servicioSesion'

/**
 * Recupera la sesión con el refresh token de la cookie antes de mostrar nada, para que las guardias de las
 * rutas ya sepan si hay sesión. `restaurarSesion` comparte una sola petición aunque React monte dos veces el
 * efecto (StrictMode).
 */
export function SesionProvider({ children }: { children: ReactNode }) {
  const [lista, setLista] = useState(false)

  useEffect(() => {
    let activo = true
    void restaurarSesion().finally(() => {
      if (activo) setLista(true)
    })
    return () => {
      activo = false
    }
  }, [])

  if (!lista) {
    return (
      <p className="cargando" role="status">
        Cargando…
      </p>
    )
  }
  return <>{children}</>
}
