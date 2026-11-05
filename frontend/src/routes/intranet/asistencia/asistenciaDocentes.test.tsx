import { fireEvent, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { AsistenciaDocentesDia, FilaAsistenciaDocente } from '../../../modulos/asistencia/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const FILAS: FilaAsistenciaDocente[] = [
  { docenteId: 'd1', nombre: 'Quispe Mendoza, Rosa', primerBloque: '07:45:00', horaRegistro: '2026-09-29T07:41:00-05:00', estado: 'PUNTUAL', generadaAlCierre: false },
  { docenteId: 'd2', nombre: 'Ríos Vega, Carlos', primerBloque: '07:45:00', horaRegistro: '2026-09-29T07:52:00-05:00', estado: 'TARDANZA', generadaAlCierre: false },
  { docenteId: 'd3', nombre: 'Villanueva Paz, Carmen', primerBloque: '08:30:00', horaRegistro: null, estado: 'FALTA', generadaAlCierre: true },
]

function dia(extra: Partial<AsistenciaDocentesDia> = {}): AsistenciaDocentesDia {
  return {
    fecha: '2026-09-29',
    diaLectivo: true,
    cierre: 'PROCESADO',
    detalleCierre: '1 falta creada de 3 docentes con clases',
    totales: { puntual: 1, tardanza: 1, falta: 1, sinRegistrar: 0 },
    docentes: FILAS,
    ...extra,
  }
}

describe('Asistencia docente del día (UC-12, RF-13, RF-19)', () => {
  beforeEach(() => guardarSesion(sesionDe('ADMINISTRADOR', ['ASISTENCIA_DOCENTE_CONSULTAR', 'HORARIO_CONSULTAR'], { cargo: 'Dirección' })))

  it('«hoy» no manda fecha: el servidor decide qué día es', async () => {
    const consultas: string[] = []
    servidor.use(
      http.get('*/api/v1/asistencia-docente', ({ request }) => {
        consultas.push(new URL(request.url).search)
        return HttpResponse.json(dia())
      }),
    )
    await renderizarRuta('/intranet/asistencia/docentes/hoy')

    expect(await screen.findByText('Martes 29/09/2026')).toBeInTheDocument()
    expect(consultas).toEqual([''])
  })

  it('muestra los totales y a cada docente con su primera clase, su marcación y su estado', async () => {
    servidor.use(http.get('*/api/v1/asistencia-docente', () => HttpResponse.json(dia())))
    await renderizarRuta('/intranet/asistencia/docentes/hoy')

    const totales = await screen.findByLabelText('Totales')
    expect(totales).toHaveTextContent('Puntual 1')
    expect(totales).toHaveTextContent('Tardanza 1')
    expect(totales).toHaveTextContent('Falta 1')
    const filas = within(screen.getByRole('table', { name: 'Docentes del día' })).getAllByRole('row').slice(1)
    expect(filas[0]).toHaveTextContent('Quispe Mendoza, Rosa')
    expect(filas[0]).toHaveTextContent('7:45 a. m.')
    expect((filas[0]?.textContent ?? '').replace(/\s/g, ' ')).toContain('7:41 a. m.')
    expect(filas[1]).toHaveTextContent('Tardanza')
  })

  it('la falta del cierre no tiene hora de marcación y lo dice', async () => {
    servidor.use(http.get('*/api/v1/asistencia-docente', () => HttpResponse.json(dia())))
    await renderizarRuta('/intranet/asistencia/docentes/hoy')

    const fila = (await screen.findByText('Villanueva Paz, Carmen')).closest('tr') as HTMLElement
    expect(within(fila).getByText('Generado al cierre de jornada')).toBeInTheDocument()
    expect(within(fila).getByText('Falta')).toBeInTheDocument()
  })

  it('un docente programado sin registro figura «Sin registrar» y el cierre pendiente se explica', async () => {
    servidor.use(
      http.get('*/api/v1/asistencia-docente', () =>
        HttpResponse.json(
          dia({
            cierre: 'PENDIENTE',
            detalleCierre: null,
            totales: { puntual: 0, tardanza: 0, falta: 0, sinRegistrar: 1 },
            docentes: [{ docenteId: 'd1', nombre: 'Quispe Mendoza, Rosa', primerBloque: '07:45:00', horaRegistro: null, estado: null, generadaAlCierre: false }],
          }),
        ),
      ),
    )
    await renderizarRuta('/intranet/asistencia/docentes/hoy')

    expect(await screen.findByText(/El cierre diario aún no procesó esta fecha/)).toBeInTheDocument()
    expect(within(screen.getByRole('table', { name: 'Docentes del día' })).getByText('Sin registrar')).toBeInTheDocument()
  })

  it('FA-4: un cierre fallido se muestra con su motivo y sin inventar faltas', async () => {
    servidor.use(
      http.get('*/api/v1/asistencia-docente', () =>
        HttpResponse.json(dia({ cierre: 'FALLIDO', detalleCierre: 'No hay un horario publicado para el año 2027: no se crean faltas basadas en supuestos', totales: { puntual: 0, tardanza: 0, falta: 0, sinRegistrar: 0 }, docentes: [] })),
      ),
    )
    await renderizarRuta('/intranet/asistencia/docentes/hoy')

    expect(await screen.findByRole('alert')).toHaveTextContent('No hay un horario publicado para el año 2027')
    expect(screen.getByText(/No hay docentes con clases ni registros en esta fecha/)).toBeInTheDocument()
  })

  it('FA-1: en un día no lectivo avisa que no hay docentes programados', async () => {
    servidor.use(http.get('*/api/v1/asistencia-docente', () => HttpResponse.json(dia({ diaLectivo: false, cierre: 'PENDIENTE', docentes: [], totales: { puntual: 0, tardanza: 0, falta: 0, sinRegistrar: 0 } }))))
    await renderizarRuta('/intranet/asistencia/docentes/2026-09-27')

    expect(await screen.findByText(/La fecha no es un día lectivo/)).toBeInTheDocument()
    expect(screen.queryByText(/El cierre diario/)).not.toBeInTheDocument()
  })

  it('una fecha en la URL se manda al servidor y cambiar la fecha navega a la nueva', async () => {
    const consultas: string[] = []
    servidor.use(
      http.get('*/api/v1/asistencia-docente', ({ request }) => {
        const fecha = new URL(request.url).searchParams.get('fecha')
        consultas.push(String(fecha))
        return HttpResponse.json(dia({ fecha: fecha ?? '2026-09-29' }))
      }),
    )
    const { router } = await renderizarRuta('/intranet/asistencia/docentes/2026-09-25')

    expect(await screen.findByText('Viernes 25/09/2026')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Fecha'), { target: { value: '2026-09-24' } })

    expect(await screen.findByText('Jueves 24/09/2026')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/intranet/asistencia/docentes/2026-09-24')
    expect(consultas).toEqual(['2026-09-25', '2026-09-24'])
  })

  it('una fecha futura rechazada por el servidor muestra el motivo', async () => {
    servidor.use(http.get('*/api/v1/asistencia-docente', () => problema(400, 'SOLICITUD_INVALIDA', 'No se consulta la asistencia de una fecha futura')))
    await renderizarRuta('/intranet/asistencia/docentes/2099-01-01')

    expect(await screen.findByRole('alert')).toHaveTextContent('No se consulta la asistencia de una fecha futura')
  })

  it('un usuario sin ASISTENCIA_DOCENTE_CONSULTAR no entra', async () => {
    guardarSesion(sesionDe('DOCENTE', ['ASISTENCIA_DOCENTE_MARCAR']))
    servidor.use(http.get('*/api/v1/asistencia-docente', () => HttpResponse.json(dia())))
    await renderizarRuta('/intranet/asistencia/docentes/hoy')

    expect(screen.queryByRole('table', { name: 'Docentes del día' })).not.toBeInTheDocument()
  })

  it('el menú ofrece «Asistencia docente» al administrador', async () => {
    servidor.use(http.get('*/api/v1/asistencia-docente', () => HttpResponse.json(dia())))
    await renderizarRuta('/intranet/inicio')

    const enlaces = await screen.findAllByRole('link', { name: /Asistencia docente/ })
    expect(enlaces.length).toBeGreaterThan(0)
    for (const enlace of enlaces) expect(enlace).toHaveAttribute('href', '/intranet/asistencia/docentes/hoy')
  })
})
