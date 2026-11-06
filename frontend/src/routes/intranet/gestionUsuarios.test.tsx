import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../compartido/auth/sesion'
import type { DetalleUsuario, UsuarioFila } from '../../modulos/seguridad/api'
import { problema, sesionDe } from '../../test/msw/handlers'
import { servidor } from '../../test/msw/servidor'
import { renderizarRuta } from '../../test/utilidades'

const ADMIN = sesionDe('ADMINISTRADOR', ['USUARIO_CONSULTAR', 'USUARIO_GESTIONAR'], { cargo: 'Secretaría académica' })

function fila(extra: Partial<UsuarioFila>): UsuarioFila {
  return {
    id: 'u-1',
    nombre: 'Luis Paredes Soto',
    correo: 'luis@colegio.pe',
    rol: 'DOCENTE',
    activo: true,
    estado: 'ACTIVO',
    bloqueadoHasta: null,
    ultimoAcceso: null,
    ...extra,
  }
}

const detalle = (extra: Partial<DetalleUsuario> = {}): DetalleUsuario => ({
  id: 'u-1',
  nombres: 'Luis',
  apellidos: 'Paredes Soto',
  documento: '45678901',
  correo: 'luis@colegio.pe',
  rol: 'DOCENTE',
  estado: 'PENDIENTE',
  cargo: null,
  codigoDocente: 'DOC-011',
  especialidad: 'Matemática',
  telefono: null,
  codigoAlumno: null,
  invitacionVenceEn: null,
  version: 0,
  ...extra,
})

function listar(filas: UsuarioFila[]) {
  servidor.use(
    http.get('*/api/v1/usuarios', () =>
      HttpResponse.json({ contenido: filas, pagina: 0, tamano: 15, totalElementos: filas.length, totalPaginas: 1 }),
    ),
  )
}

describe('Gestión de cuentas (UC-24, RF-01)', () => {
  beforeEach(() => guardarSesion(ADMIN))

  it('crea una cuenta sin pedir contraseña y avisa que se envió la invitación', async () => {
    let cuerpo: Record<string, unknown> | null = null
    listar([])
    servidor.use(
      http.post('*/api/v1/usuarios', async ({ request }) => {
        cuerpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(detalle(), { status: 201 })
      }),
    )
    await renderizarRuta('/intranet/usuarios')
    const usuario = userEvent.setup()

    await usuario.click(await screen.findByRole('button', { name: 'Nuevo usuario' }))
    const dialogo = screen.getByRole('dialog', { name: 'Nuevo usuario' })
    expect(within(dialogo).queryByLabelText(/contraseña/i)).not.toBeInTheDocument()
    expect(dialogo).toHaveTextContent('72 horas')
    await usuario.type(within(dialogo).getByLabelText('Nombres'), 'Luis')
    await usuario.type(within(dialogo).getByLabelText('Apellidos'), 'Paredes Soto')
    await usuario.type(within(dialogo).getByLabelText('Documento de identidad'), '45678901')
    await usuario.type(within(dialogo).getByLabelText('Correo'), 'luis@colegio.pe')
    await usuario.click(within(dialogo).getByRole('button', { name: 'Crear y enviar invitación' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Enviamos la invitación a luis@colegio.pe')
    expect(cuerpo).toMatchObject({ rol: 'DOCENTE', documento: '45678901', correo: 'luis@colegio.pe', especialidad: null })
    expect(cuerpo).not.toHaveProperty('contrasena')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('valida los datos antes de enviar y exige el cargo del administrador', async () => {
    let llamadas = 0
    listar([])
    servidor.use(http.post('*/api/v1/usuarios', () => (llamadas++, HttpResponse.json(detalle(), { status: 201 }))))
    await renderizarRuta('/intranet/usuarios')
    const usuario = userEvent.setup()

    await usuario.click(await screen.findByRole('button', { name: 'Nuevo usuario' }))
    const dialogo = screen.getByRole('dialog', { name: 'Nuevo usuario' })
    await usuario.selectOptions(within(dialogo).getByLabelText('Rol'), 'ADMINISTRADOR')
    await usuario.click(within(dialogo).getByRole('button', { name: 'Crear y enviar invitación' }))

    expect(await within(dialogo).findByText('Ingresa los nombres.')).toBeInTheDocument()
    expect(within(dialogo).getByText('Ingresa el cargo.')).toBeInTheDocument()
    expect(within(dialogo).getByText('Ingresa el correo.')).toBeInTheDocument()
    expect(llamadas).toBe(0)
  })

  it('muestra el conflicto cuando el documento o el correo ya existen', async () => {
    listar([])
    servidor.use(
      http.post('*/api/v1/usuarios', () => problema(409, 'CORREO_DUPLICADO', 'Ya existe una cuenta registrada con el correo luis@colegio.pe')),
    )
    await renderizarRuta('/intranet/usuarios')
    const usuario = userEvent.setup()

    await usuario.click(await screen.findByRole('button', { name: 'Nuevo usuario' }))
    const dialogo = screen.getByRole('dialog', { name: 'Nuevo usuario' })
    await usuario.type(within(dialogo).getByLabelText('Nombres'), 'Luis')
    await usuario.type(within(dialogo).getByLabelText('Apellidos'), 'Paredes')
    await usuario.type(within(dialogo).getByLabelText('Documento de identidad'), '45678901')
    await usuario.type(within(dialogo).getByLabelText('Correo'), 'luis@colegio.pe')
    await usuario.click(within(dialogo).getByRole('button', { name: 'Crear y enviar invitación' }))

    expect(await within(dialogo).findByRole('alert')).toHaveTextContent('Ya existe una cuenta registrada con el correo')
    expect(screen.getByRole('dialog', { name: 'Nuevo usuario' })).toBeInTheDocument()
  })

  it('edita una cuenta con la versión de la ficha (If-Match) y no deja cambiar el rol', async () => {
    let ifMatch: string | null = null
    let cuerpo: Record<string, unknown> | null = null
    listar([fila({})])
    servidor.use(
      http.get('*/api/v1/usuarios/u-1', () => HttpResponse.json(detalle({ estado: 'ACTIVO', version: 3 }))),
      http.put('*/api/v1/usuarios/u-1', async ({ request }) => {
        ifMatch = request.headers.get('If-Match')
        cuerpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(detalle({ apellidos: 'Paredes Rojas', version: 4 }))
      }),
    )
    await renderizarRuta('/intranet/usuarios')
    const usuario = userEvent.setup()

    await usuario.click(await screen.findByRole('button', { name: 'Editar a Luis Paredes Soto' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Editar usuario' })
    expect(within(dialogo).getByLabelText('Rol')).toBeDisabled()
    const apellidos = within(dialogo).getByLabelText('Apellidos')
    await usuario.clear(apellidos)
    await usuario.type(apellidos, 'Paredes Rojas')
    await usuario.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Se guardaron los datos de Luis Paredes Rojas')
    expect(ifMatch).toBe('"3"')
    expect(cuerpo).toMatchObject({ apellidos: 'Paredes Rojas', especialidad: 'Matemática' })
    expect(cuerpo).not.toHaveProperty('rol')
  })

  it('una cuenta pendiente se reenvía la invitación y se ve su estado', async () => {
    let reenvios = 0
    listar([fila({ estado: 'PENDIENTE', activo: false })])
    servidor.use(
      http.post('*/api/v1/usuarios/u-1/invitacion', () => {
        reenvios++
        return HttpResponse.json(detalle(), { status: 202 })
      }),
    )
    await renderizarRuta('/intranet/usuarios')
    const usuario = userEvent.setup()

    const tabla = await screen.findByRole('table', { name: 'Usuarios' })
    expect(within(tabla).getByText('Pendiente de invitación')).toBeInTheDocument()
    expect(within(tabla).queryByRole('button', { name: /^Activar/ })).not.toBeInTheDocument()
    await usuario.click(within(tabla).getByRole('button', { name: 'Reenviar invitación a Luis Paredes Soto' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Reenviamos la invitación a luis@colegio.pe')
    expect(reenvios).toBe(1)
  })

  it('desactivar pide confirmación y avisa que se cerraron las sesiones', async () => {
    let desactivada = false
    listar([fila({})])
    servidor.use(
      http.patch('*/api/v1/usuarios/u-1/desactivacion', () => {
        desactivada = true
        return HttpResponse.json(detalle({ estado: 'INACTIVO' }))
      }),
    )
    await renderizarRuta('/intranet/usuarios')
    const usuario = userEvent.setup()

    await usuario.click(await screen.findByRole('button', { name: 'Desactivar a Luis Paredes Soto' }))
    expect(desactivada).toBe(false)
    await usuario.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Sus sesiones se cerraron')
    expect(desactivada).toBe(true)
  })

  it('FA-2: habilitar un alumno sin matrícula activa muestra el mensaje del servidor', async () => {
    listar([fila({ id: 'u-ana', nombre: 'Ana Prueba', rol: 'ALUMNO', correo: 'ana@colegio.pe', estado: 'INACTIVO', activo: false })])
    servidor.use(
      http.patch('*/api/v1/usuarios/u-ana/activacion', () =>
        problema(422, 'VINCULO_ACADEMICO_INEXISTENTE', 'El estudiante no tiene una matrícula activa; no se puede habilitar su cuenta'),
      ),
    )
    await renderizarRuta('/intranet/usuarios')

    await userEvent.click(await screen.findByRole('button', { name: 'Activar a Ana Prueba' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('no tiene una matrícula activa')
  })

  it('un alumno sin correo pide el correo para enviar la invitación', async () => {
    let cuerpo: Record<string, unknown> | null = null
    listar([fila({ id: 'u-ana', nombre: 'Ana Prueba', rol: 'ALUMNO', correo: null, estado: 'INACTIVO', activo: false })])
    servidor.use(
      http.patch('*/api/v1/usuarios/u-ana/activacion', async ({ request }) => {
        cuerpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(detalle({ id: 'u-ana', rol: 'ALUMNO', correo: 'ana@colegio.pe' }))
      }),
    )
    await renderizarRuta('/intranet/usuarios')
    const usuario = userEvent.setup()

    await usuario.click(await screen.findByRole('button', { name: 'Activar a Ana Prueba' }))
    const dialogo = screen.getByRole('dialog', { name: 'Habilitar cuenta' })
    await usuario.type(within(dialogo).getByLabelText('Correo'), 'ana@colegio.pe')
    await usuario.click(within(dialogo).getByRole('button', { name: 'Enviar invitación' }))

    expect(await screen.findByText(/Enviamos la invitación a ana@colegio.pe/)).toBeInTheDocument()
    expect(cuerpo).toEqual({ correo: 'ana@colegio.pe' })
  })

  it('un apoderado inactivo se habilita sin pedir hijos (S-82)', async () => {
    let llamadas = 0
    listar([fila({ id: 'u-ap', nombre: 'María Gil', rol: 'APODERADO', estado: 'INACTIVO', activo: false })])
    servidor.use(
      http.patch('*/api/v1/usuarios/u-ap/activacion', () => {
        llamadas++
        return HttpResponse.json(detalle({ id: 'u-ap', rol: 'APODERADO', estado: 'ACTIVO' }))
      }),
    )
    await renderizarRuta('/intranet/usuarios')

    await userEvent.click(await screen.findByRole('button', { name: 'Activar a María Gil' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Se habilitó la cuenta de María Gil')
    expect(llamadas).toBe(1)
  })

  it('quien solo consulta no ve el alta ni las acciones', async () => {
    guardarSesion(sesionDe('ADMINISTRADOR', ['USUARIO_CONSULTAR']))
    listar([fila({})])
    await renderizarRuta('/intranet/usuarios')

    await screen.findByRole('table', { name: 'Usuarios' })
    expect(screen.queryByRole('button', { name: 'Nuevo usuario' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Desactivar/ })).not.toBeInTheDocument()
  })
})

describe('Definir la contraseña con la invitación (S-77)', () => {
  it('define la contraseña con el token del enlace y ofrece iniciar sesión', async () => {
    let cuerpo: Record<string, unknown> | null = null
    servidor.use(
      http.post('*/api/v1/auth/recuperacion/confirmacion', async ({ request }) => {
        cuerpo = (await request.json()) as Record<string, unknown>
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await renderizarRuta('/definir-contrasena?token=abc123')
    const usuario = userEvent.setup()

    expect(await screen.findByRole('heading', { name: 'Define tu contraseña' })).toBeInTheDocument()
    await usuario.type(screen.getByLabelText('Contraseña'), 'ClaveNueva2027')
    await usuario.type(screen.getByLabelText('Repite la contraseña'), 'ClaveNueva2027')
    await usuario.click(screen.getByRole('button', { name: 'Guardar contraseña' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Tu cuenta está activa')
    expect(cuerpo).toEqual({ token: 'abc123', nuevaContrasena: 'ClaveNueva2027' })
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute('href', '/login')
  })

  it('un enlace vencido indica pedir una invitación nueva a la administración', async () => {
    servidor.use(
      http.post('*/api/v1/auth/recuperacion/confirmacion', () =>
        problema(400, 'TOKEN_RECUPERACION_INVALIDO', 'El enlace de recuperación no es válido o venció'),
      ),
    )
    await renderizarRuta('/definir-contrasena?token=vencido')
    const usuario = userEvent.setup()

    await usuario.type(await screen.findByLabelText('Contraseña'), 'ClaveNueva2027')
    await usuario.type(screen.getByLabelText('Repite la contraseña'), 'ClaveNueva2027')
    await usuario.click(screen.getByRole('button', { name: 'Guardar contraseña' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('invitación nueva'))
  })
})
