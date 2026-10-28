import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { limpiarSesion } from '../compartido/auth/sesion'
import { reiniciarRestauracion } from '../compartido/auth/servicioSesion'
import { servidor } from './msw/servidor'

// El fetch de Node no acepta rutas relativas (`/api/v1/...`); el navegador las resuelve contra el origen de la página
const fetchOriginal = globalThis.fetch
globalThis.fetch = (entrada: RequestInfo | URL, opciones?: RequestInit) =>
  fetchOriginal(
    typeof entrada === 'string' && entrada.startsWith('/') ? new URL(entrada, window.location.origin) : entrada,
    opciones,
  )

// jsdom no implementa scrollTo (el router lo llama al navegar)
window.scrollTo = () => undefined

beforeAll(() => servidor.listen({ onUnhandledFrame: 'error' }))

afterEach(() => {
  cleanup()
  servidor.resetHandlers()
  limpiarSesion()
  reiniciarRestauracion()
})

afterAll(() => servidor.close())
