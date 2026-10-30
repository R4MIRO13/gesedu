import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
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

// Con el backend compilando al lado, el primer render de una ruta puede pasar del segundo que espera findBy por defecto
configure({ asyncUtilTimeout: 5000 })

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
