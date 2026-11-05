import { useEffect, useState } from 'react'

/** Espera antes de validar en vivo: evita una petición por cada tecla o cada segmento de una fecha. */
export const RETRASO_VALIDACION_MS = 400

/**
 * Devuelve `valor` solo cuando deja de cambiar durante `retraso` ms. `pendiente` es verdadero mientras el valor de
 * entrada va por delante del devuelto, para que la pantalla no use una validación que ya quedó vieja.
 * El valor debe poder serializarse como JSON (cadenas, números, objetos y listas de datos): se compara por contenido,
 * así que un objeto nuevo con los mismos datos no reinicia la espera.
 */
export function useValorConRetraso<T>(valor: T, retraso: number = RETRASO_VALIDACION_MS): { valor: T; pendiente: boolean } {
  const clave = JSON.stringify(valor)
  const [estable, setEstable] = useState({ valor, clave })

  useEffect(() => {
    if (clave === estable.clave) return
    const temporizador = setTimeout(() => setEstable({ valor: JSON.parse(clave) as T, clave }), retraso)
    return () => clearTimeout(temporizador)
  }, [clave, estable.clave, retraso])

  return { valor: estable.valor, pendiente: clave !== estable.clave }
}
