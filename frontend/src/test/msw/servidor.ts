import { setupServer } from 'msw/node'
import { handlersBase } from './handlers'

/** Servidor MSW de las pruebas: simula la API. Cada prueba agrega los suyos con `servidor.use(...)`. */
export const servidor = setupServer(...handlersBase)
