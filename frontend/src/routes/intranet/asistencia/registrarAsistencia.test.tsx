import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { AlumnoAsistencia, AsistenciaSeccion, EstadoAsistencia } from '../../../modulos/asistencia/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

function alumno(id: string, apellidos: string, nombres: string, estado: EstadoAsistencia | null = null): AlumnoAsistencia {
  return { alumnoId: id, codigo: `2027-00${id}`, apellidos, nombres, registroId: estado ? `r${id}` : null, estado }
}

function totalesDe(alumnos: AlumnoAsistencia[]) {
  const cuenta = (e: EstadoAsistencia) => alumnos.filter((a) => a.estado === e).length
  return { puntual: cuenta('PUNTUAL'), tardanza: cuenta('TARDANZA'), falta: cuenta('FALTA'), pendientes: alumnos.filter((a) => a.estado === null).length }
}

function seccion(alumnos: AlumnoAsistencia[], extra: Partial<AsistenciaSeccion> = {}): AsistenciaSeccion {
  return {
    codigo: '3A',
    nombre: '3° A',
    fecha: '2027-03-15',
    horaServidor: '2027-03-15T07:52:00-05:00',
    registroHabilitado: true,
    mensaje: null,
    limiteTardanza: '08:30:00',
    alumnos,
    totales: totalesDe(alumnos),
    ...extra,
  }
}

const ALUMNOS = [alumno('1', 'Alva', 'Lucía'), alumno('2', 'Bravo', 'Tomás'), alumno('3', 'Castro', 'Mateo')]

function simular(inicial: AsistenciaSeccion, lotes: unknown[] = []) {
  let actual = inicial
  servidor.use(
    http.get('*/api/v1/secciones/3A/asistencia', () => HttpResponse.json(actual)),
    http.get('*/api/v1/asistencia-alumnos/mi-seccion', () => HttpResponse.json({ codigo: '3A', nombre: '3° A', alumnos: actual.alumnos.length })),
    http.post('*/api/v1/secciones/3A/asistencia', async ({ request }) => {
      const cuerpo = (await request.json()) as { asistencias: { alumnoId: string; estado: EstadoAsistencia }[] }
      lotes.push(cuerpo)
      const alumnos = actual.alumnos.map((a) => {
        const item = cuerpo.asistencias.find((i) => i.alumnoId === a.alumnoId)
        return item ? { ...a, estado: item.estado, registroId: `r${a.alumnoId}` } : a
      })
      actual = seccion(alumnos)
      return HttpResponse.json(actual, { status: 201 })
    }),
  )
}

function radio(nombre: string, estado: string) {
  return within(screen.getByRole('group', { name: `Asistencia de ${nombre}` })).getByLabelText(estado)
}

describe('Registrar asistencia de alumnos (UC-08, RF-14)', () => {
  beforeEach(() => guardarSesion(sesionDe('DOCENTE', ['ASISTENCIA_ALUMNO_REGISTRAR', 'HORARIO_CONSULTAR'], { nombres: 'Rosa Quispe' })))

  it('muestra la sección, el día y los alumnos en orden, con Puntual, Tardanza y Falta por alumno', async () => {
    simular(seccion(ALUMNOS))
    await renderizarRuta('/intranet/asistencia/3A/registrar')

    expect(await screen.findByRole('heading', { name: 'Registrar asistencia · 3° A' })).toBeInTheDocument()
    expect(screen.getByText('Lunes 15/03/2027')).toBeInTheDocument()
    const filas = within(screen.getByRole('table', { name: 'Alumnos de la sección' })).getAllByRole('row').slice(1)
    expect(filas.map((f) => f.textContent)).toEqual([
      expect.stringContaining('Alva, Lucía'),
      expect.stringContaining('Bravo, Tomás'),
      expect.stringContaining('Castro, Mateo'),
    ])
    for (const estado of ['Puntual', 'Tardanza', 'Falta']) expect(radio('Alva, Lucía', estado)).not.toBeChecked()
    expect(screen.getByText('3 por registrar')).toBeInTheDocument()
  })

  it('guarda el lote con los tres estados en una sola petición', async () => {
    const lotes: unknown[] = []
    simular(seccion(ALUMNOS), lotes)
    await renderizarRuta('/intranet/asistencia/3A/registrar')
    await screen.findByRole('table', { name: 'Alumnos de la sección' })

    await userEvent.click(radio('Alva, Lucía', 'Puntual'))
    await userEvent.click(radio('Bravo, Tomás', 'Tardanza'))
    await userEvent.click(radio('Castro, Mateo', 'Falta'))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar asistencia' }))

    expect(await screen.findByText(/Asistencia registrada: 1 puntuales, 1 tardanza y 1 falta\./)).toBeInTheDocument()
    expect(lotes).toEqual([
      {
        asistencias: [
          { alumnoId: '1', estado: 'PUNTUAL' },
          { alumnoId: '2', estado: 'TARDANZA' },
          { alumnoId: '3', estado: 'FALTA' },
        ],
      },
    ])
  })

  it('«Marcar todos como Puntual» elige a todos y permite guardar', async () => {
    const lotes: unknown[] = []
    simular(seccion(ALUMNOS), lotes)
    await renderizarRuta('/intranet/asistencia/3A/registrar')
    await screen.findByRole('table', { name: 'Alumnos de la sección' })

    await userEvent.click(screen.getByRole('button', { name: 'Marcar todos como Puntual' }))
    expect(screen.getByText('Todos tienen estado')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Guardar asistencia' }))

    await waitFor(() => expect(lotes).toHaveLength(1))
    expect((lotes[0] as { asistencias: { estado: string }[] }).asistencias.every((a) => a.estado === 'PUNTUAL')).toBe(true)
  })

  it('FA-1: con alumnos sin estado señala cuántos faltan, los marca y no envía nada', async () => {
    const lotes: unknown[] = []
    simular(seccion(ALUMNOS), lotes)
    await renderizarRuta('/intranet/asistencia/3A/registrar')
    await screen.findByRole('table', { name: 'Alumnos de la sección' })

    await userEvent.click(radio('Alva, Lucía', 'Puntual'))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar asistencia' }))

    expect(await screen.findByText(/Faltan 2 alumnos por registrar/)).toBeInTheDocument()
    expect(screen.getAllByText('Indica el estado')).toHaveLength(2)
    expect(lotes).toHaveLength(0)
  })

  it('FA-2: los alumnos ya registrados se muestran sin controles y solo se envían los pendientes', async () => {
    const lotes: unknown[] = []
    simular(seccion([alumno('1', 'Alva', 'Lucía', 'FALTA'), alumno('2', 'Bravo', 'Tomás'), alumno('3', 'Castro', 'Mateo')]), lotes)
    await renderizarRuta('/intranet/asistencia/3A/registrar')
    await screen.findByRole('table', { name: 'Alumnos de la sección' })

    expect(screen.queryByRole('group', { name: 'Asistencia de Alva, Lucía' })).not.toBeInTheDocument()
    const filaDeAlva = screen.getByText('Alva, Lucía').closest('tr') as HTMLElement
    expect(within(filaDeAlva).getByText('Falta')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Marcar todos como Puntual' }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar asistencia' }))

    await waitFor(() => expect(lotes).toHaveLength(1))
    expect((lotes[0] as { asistencias: { alumnoId: string }[] }).asistencias.map((a) => a.alumnoId)).toEqual(['2', '3'])
  })

  it('FA-2: con todos ya registrados la pantalla queda en consulta y no deja guardar', async () => {
    simular(seccion([alumno('1', 'Alva', 'Lucía', 'PUNTUAL'), alumno('2', 'Bravo', 'Tomás', 'TARDANZA'), alumno('3', 'Castro', 'Mateo', 'FALTA')]))
    await renderizarRuta('/intranet/asistencia/3A/registrar')

    expect(await screen.findByText(/Todos los alumnos ya tienen su asistencia registrada hoy: puntuales 1, tardanzas 1, faltas 1/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar asistencia' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Marcar todos como Puntual' })).not.toBeInTheDocument()
  })

  it('FA-4: con la jornada cerrada queda en consulta con el aviso y sin guardar', async () => {
    simular(seccion(ALUMNOS, { registroHabilitado: false, mensaje: 'La jornada cerró a las 15:30: el registro de asistencia ya no está habilitado.' }))
    await renderizarRuta('/intranet/asistencia/3A/registrar')

    expect(await screen.findByText(/La jornada cerró a las 15:30/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar asistencia' })).toBeDisabled()
    expect(screen.queryByRole('group', { name: /Asistencia de/ })).not.toBeInTheDocument()
  })

  it('FA-4: en un día no lectivo muestra el motivo del servidor', async () => {
    servidor.use(
      http.get('*/api/v1/secciones/3A/asistencia', () =>
        problema(422, 'DIA_NO_LECTIVO', 'Hoy no es un día lectivo: el registro de asistencia no está habilitado para la fecha.'),
      ),
    )
    await renderizarRuta('/intranet/asistencia/3A/registrar')

    expect(await screen.findByRole('alert')).toHaveTextContent('Hoy no es un día lectivo')
  })

  it('FA-5: si otra solicitud ganó la carrera (409) muestra el aviso y se actualiza con lo vigente', async () => {
    let vigente = seccion(ALUMNOS)
    servidor.use(
      http.get('*/api/v1/secciones/3A/asistencia', () => HttpResponse.json(vigente)),
      http.post('*/api/v1/secciones/3A/asistencia', () => {
        vigente = seccion(ALUMNOS.map((a) => ({ ...a, estado: 'PUNTUAL' as const, registroId: `r${a.alumnoId}` })))
        return problema(409, 'ASISTENCIA_YA_REGISTRADA', 'Otra solicitud ya registró la asistencia de algunos alumnos. No se guardó nada: revisa los registros vigentes.')
      }),
    )
    await renderizarRuta('/intranet/asistencia/3A/registrar')
    await screen.findByRole('table', { name: 'Alumnos de la sección' })

    await userEvent.click(screen.getByRole('button', { name: 'Marcar todos como Puntual' }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar asistencia' }))

    expect(await screen.findByText(/Otra solicitud ya registró la asistencia/)).toBeInTheDocument()
    expect(await screen.findByText(/Todos los alumnos ya tienen su asistencia registrada hoy/)).toBeInTheDocument()
  })

  it('FA-3: un usuario que no es tutor de la sección ve el motivo', async () => {
    servidor.use(
      http.get('*/api/v1/secciones/3A/asistencia', () =>
        problema(403, 'FUERA_DE_ALCANCE', 'Solo el tutor de la sección 3° A registra su asistencia'),
      ),
    )
    await renderizarRuta('/intranet/asistencia/3A/registrar')

    expect(await screen.findByRole('alert')).toHaveTextContent('Solo el tutor de la sección 3° A registra su asistencia')
  })

  it('la opción del menú lleva al tutor directamente a su sección', async () => {
    simular(seccion(ALUMNOS))
    const { router } = await renderizarRuta('/intranet/asistencia')

    expect(await screen.findByRole('heading', { name: 'Registrar asistencia · 3° A' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/intranet/asistencia/3A/registrar')
  })

  it('un docente que no es tutor ve el aviso en lugar de una pantalla', async () => {
    servidor.use(
      http.get('*/api/v1/asistencia-alumnos/mi-seccion', () => problema(403, 'FUERA_DE_ALCANCE', 'No tienes una sección a cargo como tutor este año.')),
    )
    await renderizarRuta('/intranet/asistencia')

    expect(await screen.findByText('No tienes una sección a cargo como tutor este año.')).toBeInTheDocument()
  })

  it('un usuario sin ASISTENCIA_ALUMNO_REGISTRAR no entra', async () => {
    guardarSesion(sesionDe('ALUMNO', ['HORARIO_CONSULTAR']))
    simular(seccion(ALUMNOS))
    await renderizarRuta('/intranet/asistencia/3A/registrar')

    expect(screen.queryByRole('button', { name: 'Guardar asistencia' })).not.toBeInTheDocument()
  })
})
