import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../compartido/auth/sesion'
import type { CatalogoRoles } from '../../modulos/seguridad/api'
import { problema, sesionDe } from '../../test/msw/handlers'
import { servidor } from '../../test/msw/servidor'
import { renderizarRuta } from '../../test/utilidades'

const CATALOGO: CatalogoRoles = {
  roles: [
    {
      id: 'r-adm',
      codigo: 'ADMINISTRADOR',
      nombre: 'Administrador',
      descripcion: 'Personal administrativo',
      permisos: ['ROL_GESTIONAR', 'USUARIO_CONSULTAR', 'USUARIO_GESTIONAR'],
    },
    { id: 'r-doc', codigo: 'DOCENTE', nombre: 'Docente', descripcion: null, permisos: ['NOTA_REGISTRAR'] },
  ],
  permisos: [
    { codigo: 'NOTA_REGISTRAR', modulo: 'EVALUACION', accion: 'REGISTRAR', descripcion: 'Registrar logros por competencia' },
    { codigo: 'REGLA_DEFINIR', modulo: 'EVALUACION', accion: 'DEFINIR', descripcion: 'Definir la regla de consolidación' },
    { codigo: 'ROL_GESTIONAR', modulo: 'SEGURIDAD', accion: 'GESTIONAR', descripcion: 'Gestionar roles y permisos' },
    { codigo: 'USUARIO_CONSULTAR', modulo: 'SEGURIDAD', accion: 'CONSULTAR', descripcion: 'Consultar cuentas' },
    { codigo: 'USUARIO_GESTIONAR', modulo: 'SEGURIDAD', accion: 'GESTIONAR', descripcion: 'Gestionar cuentas' },
  ],
}

describe('Roles y permisos (RF-48)', () => {
  beforeEach(() => {
    guardarSesion(sesionDe('ADMINISTRADOR', ['ROL_GESTIONAR', 'USUARIO_CONSULTAR']))
    servidor.use(http.get('*/api/v1/roles', () => HttpResponse.json(CATALOGO)))
  })

  it('muestra los permisos del rol agrupados por módulo y el menú lleva a la pantalla', async () => {
    await renderizarRuta('/intranet/roles')

    const formulario = await screen.findByRole('form', { name: 'Permisos del rol Administrador' })
    expect(within(formulario).getByText('EVALUACION')).toBeInTheDocument()
    expect(within(formulario).getByText('SEGURIDAD')).toBeInTheDocument()
    expect(within(formulario).getByRole('checkbox', { name: /ROL_GESTIONAR/ })).toBeChecked()
    expect(within(formulario).getByRole('checkbox', { name: /NOTA_REGISTRAR/ })).not.toBeChecked()
    expect(screen.getAllByRole('link', { name: /Roles y permisos/ })[0]).toHaveAttribute('href', '/intranet/roles')
  })

  it('al Administrador no se le pueden quitar los permisos de gestión', async () => {
    await renderizarRuta('/intranet/roles')

    const formulario = await screen.findByRole('form', { name: 'Permisos del rol Administrador' })
    for (const codigo of [/ROL_GESTIONAR/, /USUARIO_CONSULTAR/, /USUARIO_GESTIONAR/]) {
      expect(within(formulario).getByRole('checkbox', { name: codigo })).toBeDisabled()
    }
  })

  it('cambia los permisos del docente y guarda el conjunto completo', async () => {
    let cuerpo: { permisos: string[] } | null = null
    servidor.use(
      http.put('*/api/v1/roles/r-doc/permisos', async ({ request }) => {
        cuerpo = (await request.json()) as { permisos: string[] }
        return HttpResponse.json({ ...CATALOGO.roles[1]!, permisos: cuerpo.permisos })
      }),
    )
    await renderizarRuta('/intranet/roles')
    const usuario = userEvent.setup()

    await usuario.click(await screen.findByRole('button', { name: 'Docente' }))
    const formulario = await screen.findByRole('form', { name: 'Permisos del rol Docente' })
    const guardar = within(formulario).getByRole('button', { name: 'Guardar permisos' })
    expect(guardar).toBeDisabled()
    await usuario.click(within(formulario).getByRole('checkbox', { name: /REGLA_DEFINIR/ }))
    await usuario.click(within(formulario).getByRole('checkbox', { name: /NOTA_REGISTRAR/ }))
    expect(guardar).toBeEnabled()
    await usuario.click(guardar)

    expect(await screen.findByRole('status')).toHaveTextContent('Se guardaron los permisos del rol Docente')
    expect(cuerpo).toEqual({ permisos: ['REGLA_DEFINIR'] })
  })

  it('muestra el error del servidor y conserva lo marcado', async () => {
    servidor.use(
      http.put('*/api/v1/roles/r-doc/permisos', () => problema(400, 'VALIDACION_INVALIDA', 'Permisos que no existen: X')),
    )
    await renderizarRuta('/intranet/roles')
    const usuario = userEvent.setup()

    await usuario.click(await screen.findByRole('button', { name: 'Docente' }))
    await usuario.click(await screen.findByRole('checkbox', { name: /REGLA_DEFINIR/ }))
    await usuario.click(screen.getByRole('button', { name: 'Guardar permisos' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Permisos que no existen: X')
    expect(screen.getByRole('checkbox', { name: /REGLA_DEFINIR/ })).toBeChecked()
  })

  it('sin el permiso ROL_GESTIONAR no se entra', async () => {
    guardarSesion(sesionDe('DOCENTE', ['NOTA_REGISTRAR']))
    const { router } = await renderizarRuta('/intranet/roles')

    expect(router.state.location.pathname).not.toBe('/intranet/roles')
  })
})
