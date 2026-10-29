import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../compartido/auth/sesion'
import type { UsuarioFila } from '../../modulos/seguridad/api'
import { problema, sesionDe } from '../../test/msw/handlers'
import { servidor } from '../../test/msw/servidor'
import { renderizarRuta } from '../../test/utilidades'

const SESION_ADMIN_USUARIOS = sesionDe('ADMINISTRADOR', ['USUARIO_CONSULTAR', 'USUARIO_GESTIONAR'], { cargo: 'Secretaría académica' })

const HUGO: UsuarioFila = {
  id: 'u-hugo',
  nombre: 'Hugo Gonzales Paredes',
  correo: 'bloqueo@demo.gesedu.pe',
  rol: 'DOCENTE',
  activo: true,
  estado: 'BLOQUEADO',
  bloqueadoHasta: '2027-03-01T10:45:00-05:00',
  ultimoAcceso: null,
}
const ROSA: UsuarioFila = {
  id: 'u-rosa',
  nombre: 'Rosa Quispe Mendoza',
  correo: 'docente@demo.gesedu.pe',
  rol: 'DOCENTE',
  activo: true,
  estado: 'ACTIVO',
  bloqueadoHasta: null,
  ultimoAcceso: '2027-03-01T08:00:00-05:00',
}

/** Simula la lista; `bloqueado` cambia a ACTIVO cuando se desbloquea la cuenta. */
function simular(urls: string[] = [], desbloqueos: string[] = []) {
  let hugo: UsuarioFila = HUGO
  servidor.use(
    http.get('*/api/v1/usuarios', ({ request }) => {
      urls.push(request.url)
      return HttpResponse.json({ contenido: [ROSA, hugo], pagina: 0, tamano: 15, totalElementos: 2, totalPaginas: 1 })
    }),
    http.patch('*/api/v1/usuarios/u-hugo/desbloqueo', () => {
      desbloqueos.push('u-hugo')
      hugo = { ...HUGO, estado: 'ACTIVO', bloqueadoHasta: null }
      return HttpResponse.json(hugo)
    }),
  )
}

describe('Usuarios (UC-24, lista parcial)', () => {
  beforeEach(() => guardarSesion(SESION_ADMIN_USUARIOS))

  it('lista las cuentas con su estado; la bloqueada dice «Bloqueado · 5 intentos»', async () => {
    simular()
    await renderizarRuta('/intranet/usuarios')

    const tabla = await screen.findByRole('table', { name: 'Usuarios' })
    const filas = within(tabla).getAllByRole('row')
    expect(within(filas[1]!).getByText('Rosa Quispe Mendoza')).toBeInTheDocument()
    expect(within(filas[1]!).getByText('Activo')).toBeInTheDocument()
    expect(within(filas[2]!).getByText('Bloqueado · 5 intentos')).toBeInTheDocument()
    expect(within(filas[2]!).getByText('bloqueo@demo.gesedu.pe')).toBeInTheDocument()
    // Solo la cuenta bloqueada tiene el botón
    expect(within(tabla).getAllByRole('button', { name: /Desbloquear/ })).toHaveLength(1)
  })

  it('filtra por texto, rol y estado', async () => {
    const urls: string[] = []
    simular(urls)
    await renderizarRuta('/intranet/usuarios')
    await screen.findByRole('table', { name: 'Usuarios' })

    await userEvent.type(screen.getByLabelText('Buscar'), 'hugo')
    await userEvent.selectOptions(screen.getByLabelText('Rol'), 'DOCENTE')
    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'BLOQUEADO')
    await userEvent.click(screen.getByRole('button', { name: 'Filtrar' }))

    await waitFor(() => expect(urls.at(-1)).toContain('q=hugo'))
    expect(Object.fromEntries(new URL(urls.at(-1)!).searchParams)).toMatchObject({
      q: 'hugo',
      rol: 'DOCENTE',
      estado: 'BLOQUEADO',
      page: '0',
    })
  })

  it('desbloquea la cuenta, avisa y la fila deja de figurar como bloqueada', async () => {
    const desbloqueos: string[] = []
    simular([], desbloqueos)
    await renderizarRuta('/intranet/usuarios')
    await screen.findByRole('table', { name: 'Usuarios' })

    await userEvent.click(screen.getByRole('button', { name: 'Desbloquear a Hugo Gonzales Paredes' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Se desbloqueó la cuenta de Hugo Gonzales Paredes.')
    expect(desbloqueos).toEqual(['u-hugo'])
    await waitFor(() => expect(screen.queryByText('Bloqueado · 5 intentos')).not.toBeInTheDocument())
    expect(screen.queryByRole('button', { name: /Desbloquear/ })).not.toBeInTheDocument()
  })

  it('si el servidor rechaza el desbloqueo muestra el motivo', async () => {
    simular()
    servidor.use(http.patch('*/api/v1/usuarios/u-hugo/desbloqueo', () => problema(400, 'SOLICITUD_INVALIDA', 'La cuenta no está bloqueada')))
    await renderizarRuta('/intranet/usuarios')
    await screen.findByRole('table', { name: 'Usuarios' })

    await userEvent.click(screen.getByRole('button', { name: /Desbloquear/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('La cuenta no está bloqueada')
  })

  it('quien solo consulta ve la lista pero no el botón Desbloquear', async () => {
    guardarSesion(sesionDe('ADMINISTRADOR', ['USUARIO_CONSULTAR']))
    simular()
    await renderizarRuta('/intranet/usuarios')
    await screen.findByRole('table', { name: 'Usuarios' })

    expect(screen.getByText('Bloqueado · 5 intentos')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Desbloquear/ })).not.toBeInTheDocument()
  })

  it('un docente no entra a Usuarios', async () => {
    guardarSesion(sesionDe('DOCENTE', ['HORARIO_CONSULTAR']))
    const { router } = await renderizarRuta('/intranet/usuarios')

    await waitFor(() => expect(router.state.location.pathname).toBe('/intranet/inicio'))
  })
})
