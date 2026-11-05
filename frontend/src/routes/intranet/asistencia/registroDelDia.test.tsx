import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { AlumnoAsistencia, AsistenciaSeccion, EstadoAsistencia } from '../../../modulos/asistencia/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

function alumno(id: string, apellidos: string, nombres: string, estado: EstadoAsistencia | null): AlumnoAsistencia {
  return { alumnoId: id, codigo: `2027-00${id}`, apellidos, nombres, registroId: estado ? `r${id}` : null, estado }
}

function seccion(alumnos: AlumnoAsistencia[], extra: Partial<AsistenciaSeccion> = {}): AsistenciaSeccion {
  const cuenta = (e: EstadoAsistencia) => alumnos.filter((a) => a.estado === e).length
  return {
    codigo: '3A',
    nombre: '3° A',
    fecha: '2027-03-15',
    horaServidor: '2027-03-15T08:10:00-05:00',
    registroHabilitado: true,
    mensaje: null,
    limiteTardanza: '08:30:00',
    actualizacionTardanzaHabilitada: true,
    alumnos,
    totales: { puntual: cuenta('PUNTUAL'), tardanza: cuenta('TARDANZA'), falta: cuenta('FALTA'), pendientes: alumnos.filter((a) => a.estado === null).length },
    ...extra,
  }
}

const ALUMNOS = [alumno('1', 'Alva', 'Lucía', 'PUNTUAL'), alumno('2', 'Bravo', 'Tomás', 'FALTA'), alumno('3', 'Castro', 'Mateo', 'TARDANZA')]

/** El backend simulado: el PATCH cambia la falta a tardanza en lo que devuelve la consulta siguiente. */
function simular(inicial: AsistenciaSeccion, cambios: string[] = []) {
  let actual = inicial
  servidor.use(
    http.get('*/api/v1/secciones/3A/asistencia', () => HttpResponse.json(actual)),
    http.patch('*/api/v1/asistencia-alumnos/:id', async ({ params, request }) => {
      cambios.push(`${String(params.id)} ${JSON.stringify(await request.json())}`)
      actual = seccion(actual.alumnos.map((a) => (a.registroId === params.id ? { ...a, estado: 'TARDANZA' as const } : a)))
      return HttpResponse.json({ id: params.id, alumnoId: '2', fecha: '2027-03-15', estado: 'TARDANZA', actualizadoEn: '2027-03-15T08:10:00-05:00', modificado: true })
    }),
  )
}

describe('Registro del día (UC-09, RF-15)', () => {
  beforeEach(() => guardarSesion(sesionDe('DOCENTE', ['ASISTENCIA_ALUMNO_REGISTRAR', 'HORARIO_CONSULTAR'], { nombres: 'Rosa Quispe' })))

  it('muestra la jornada, los totales y el estado de cada alumno', async () => {
    simular(seccion(ALUMNOS))
    await renderizarRuta('/intranet/asistencia/3A/registro-del-dia')

    expect(await screen.findByRole('heading', { name: 'Registro del día · 3° A' })).toBeInTheDocument()
    expect(screen.getByText('Jornada abierta')).toBeInTheDocument()
    const totales = screen.getByLabelText('Totales')
    expect(totales).toHaveTextContent('Puntual 1')
    expect(totales).toHaveTextContent('Tardanza 1')
    expect(totales).toHaveTextContent('Falta 1')
    const filas = within(screen.getByRole('table', { name: 'Asistencia de los alumnos' })).getAllByRole('row').slice(1)
    expect(filas[0]).toHaveTextContent('Alva, Lucía')
    expect(filas[0]).toHaveTextContent('Puntual')
    expect(filas[2]).toHaveTextContent('Tardanza')
  })

  it('solo ofrece «Cambiar a Tardanza» a quien está en falta', async () => {
    simular(seccion(ALUMNOS))
    await renderizarRuta('/intranet/asistencia/3A/registro-del-dia')
    await screen.findByRole('table', { name: 'Asistencia de los alumnos' })

    expect(screen.getAllByRole('button', { name: 'Cambiar a Tardanza' })).toHaveLength(1)
    const filaBravo = screen.getByText('Bravo, Tomás').closest('tr') as HTMLElement
    expect(within(filaBravo).getByRole('button', { name: 'Cambiar a Tardanza' })).toBeInTheDocument()
  })

  it('pide confirmar con la hora límite, cambia el estado y avisa', async () => {
    const cambios: string[] = []
    simular(seccion(ALUMNOS), cambios)
    await renderizarRuta('/intranet/asistencia/3A/registro-del-dia')

    await userEvent.click(await screen.findByRole('button', { name: 'Cambiar a Tardanza' }))
    const dialogo = screen.getByRole('dialog', { name: 'Cambiar a Tardanza' })
    expect(dialogo).toHaveTextContent('Bravo, Tomás')
    expect(dialogo).toHaveTextContent('hasta las 8:30 a. m.')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar cambio' }))

    expect(await screen.findByText('La asistencia de Bravo, Tomás ahora es Tardanza.')).toBeInTheDocument()
    expect(cambios).toEqual(['r2 {"estado":"TARDANZA"}'])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(await screen.findByLabelText('Totales')).toHaveTextContent('Tardanza 2')
    expect(screen.queryByRole('button', { name: 'Cambiar a Tardanza' })).not.toBeInTheDocument()
  })

  it('cancelar el diálogo no envía nada', async () => {
    const cambios: string[] = []
    simular(seccion(ALUMNOS), cambios)
    await renderizarRuta('/intranet/asistencia/3A/registro-del-dia')

    await userEvent.click(await screen.findByRole('button', { name: 'Cambiar a Tardanza' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(cambios).toHaveLength(0)
  })

  it('FA-1: si el plazo venció en el servidor muestra el motivo y el alumno sigue en falta', async () => {
    servidor.use(
      http.get('*/api/v1/secciones/3A/asistencia', () => HttpResponse.json(seccion(ALUMNOS))),
      http.patch('*/api/v1/asistencia-alumnos/:id', () =>
        problema(422, 'PLAZO_TARDANZA_EXCEDIDO', 'Ya no se admite la actualización: el plazo para cambiar a Tardanza venció a las 08:30 y el alumno permanece en Falta.'),
      ),
    )
    await renderizarRuta('/intranet/asistencia/3A/registro-del-dia')

    await userEvent.click(await screen.findByRole('button', { name: 'Cambiar a Tardanza' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar cambio' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('el plazo para cambiar a Tardanza venció a las 08:30')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Totales')).toHaveTextContent('Falta 1')
  })

  it('con el plazo vencido no ofrece el cambio y lo explica', async () => {
    simular(seccion(ALUMNOS, { actualizacionTardanzaHabilitada: false, horaServidor: '2027-03-15T09:00:00-05:00' }))
    await renderizarRuta('/intranet/asistencia/3A/registro-del-dia')

    expect(await screen.findByText(/El plazo para cambiar una falta a tardanza venció a las 8:30 a\. m\./)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cambiar a Tardanza' })).not.toBeInTheDocument()
  })

  it('con la jornada cerrada queda en consulta', async () => {
    simular(seccion(ALUMNOS, { registroHabilitado: false, actualizacionTardanzaHabilitada: false, mensaje: 'La jornada cerró a las 15:30: el registro de asistencia ya no está habilitado.' }))
    await renderizarRuta('/intranet/asistencia/3A/registro-del-dia')

    expect(await screen.findByText('Jornada cerrada')).toBeInTheDocument()
    expect(screen.getByText(/La jornada cerró a las 15:30/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cambiar a Tardanza' })).not.toBeInTheDocument()
  })

  it('si faltan alumnos por registrar ofrece pasar lista', async () => {
    simular(seccion([alumno('1', 'Alva', 'Lucía', 'PUNTUAL'), alumno('2', 'Bravo', 'Tomás', null)]))
    await renderizarRuta('/intranet/asistencia/3A/registro-del-dia')

    expect(await screen.findByText(/1 alumno aún sin registro/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Pasar lista' })).toHaveAttribute('href', '/intranet/asistencia/3A/registrar')
  })

  it('FA-3: quien no es tutor de la sección ve el motivo', async () => {
    servidor.use(
      http.get('*/api/v1/secciones/3A/asistencia', () => problema(403, 'FUERA_DE_ALCANCE', 'Solo el tutor de la sección 3° A registra su asistencia')),
    )
    await renderizarRuta('/intranet/asistencia/3A/registro-del-dia')

    expect(await screen.findByRole('alert')).toHaveTextContent('Solo el tutor de la sección 3° A registra su asistencia')
  })

  it('un usuario sin ASISTENCIA_ALUMNO_REGISTRAR no entra', async () => {
    guardarSesion(sesionDe('ALUMNO', ['HORARIO_CONSULTAR']))
    simular(seccion(ALUMNOS))
    await renderizarRuta('/intranet/asistencia/3A/registro-del-dia')

    expect(screen.queryByRole('table', { name: 'Asistencia de los alumnos' })).not.toBeInTheDocument()
  })
})
