import { describe, expectTypeOf, it } from 'vitest'
import type { DatosUsuario, SesionIniciada } from '../auth/sesion'
import type { components } from './tipos.gen'

type Esquemas = components['schemas']

/**
 * Comprueba con `tsc` (npm run typecheck) que los tipos escritos a mano coinciden con el contrato OpenAPI del
 * backend (`scripts/generar-tipos-api.*`). Si el backend agrega, quita o renombra una propiedad, esto deja de
 * compilar y hay que actualizar el tipo y regenerar `tipos.gen.ts`.
 */
describe('contrato de la API', () => {
  it('la sesión y el usuario tienen las mismas propiedades que el backend', () => {
    expectTypeOf<keyof SesionIniciada>().toEqualTypeOf<keyof Esquemas['SesionIniciada']>()
    expectTypeOf<keyof DatosUsuario>().toEqualTypeOf<keyof Esquemas['DatosUsuario']>()
  })

  it('los tipos de las propiedades son compatibles', () => {
    expectTypeOf<SesionIniciada['tokenAcceso']>().toEqualTypeOf<NonNullable<Esquemas['SesionIniciada']['tokenAcceso']>>()
    expectTypeOf<DatosUsuario['permisos']>().toEqualTypeOf<NonNullable<Esquemas['DatosUsuario']['permisos']>>()
  })
})
