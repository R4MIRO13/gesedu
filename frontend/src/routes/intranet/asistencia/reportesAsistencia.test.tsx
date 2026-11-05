import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { FilaReporte, ReporteAsistencia } from '../../../modulos/asistencia/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

function fila(id: string, nombre: string, extra: Partial<FilaReporte> = {}): FilaReporte {
  return {
    id,
    codigo: `2027-00${id}`,
    nombre,
    seccion: '3° A',
    puntual: 18,
    tardanza: 2,
    falta: 1,
    registrados: 21,
    diasEsperados: 22,
    noRegistrados: 1,
    porcentaje: '95.24',
    ...extra,
  }
}

function reporte(extra: Partial<ReporteAsistencia> = {}): ReporteAsistencia {
  return {
    ambito: 'ALUMNOS',
    anio: 2027,
    mes: 3,
    alcance: '3° A',
    generadoEn: '2027-04-10T10:00:00-05:00',
    fechaCorte: '2027-03-31',
    parcial: false,
    diasLectivos: 22,
    totalNoRegistrados: 1,
    filas: [fila('1', 'Alva, Lucía'), fila('2', 'Bravo, Tomás', { puntual: 0, tardanza: 0, falta: 0, registrados: 0, noRegistrados: 22, porcentaje: null })],
    ...extra,
  }
}

const GRADOS = {
  anio: 2027,
  estado: 'ACTIVO',
  docentes: [],
  grados: [
    {
      id: 'g3',
      numeroNivel: 3,
      secciones: [
        { id: 's3a', gradoId: 'g3', letra: 'A', nombre: '3° A', capacidad: 30, vacantes: 4, matriculados: 26, tutorDocenteId: 'd1', tutor: 'Rosa Quispe', version: 1 },
        { id: 's3b', gradoId: 'g3', letra: 'B', nombre: '3° B', capacidad: 30, vacantes: 30, matriculados: 0, tutorDocenteId: null, tutor: null, version: 1 },
      ],
    },
  ],
}

describe('Reportes de asistencia (UC-10, UC-11, RF-16, RF-17, RF-21)', () => {
  afterEach(() => vi.unstubAllGlobals())

  describe('como docente tutor', () => {
    beforeEach(() => guardarSesion(sesionDe('DOCENTE', ['ASISTENCIA_REPORTE_SECCION', 'HORARIO_CONSULTAR'], { nombres: 'Rosa Quispe' })))

    it('no ofrece ámbito ni sección: el reporte es el de su propia sección', async () => {
      await renderizarRuta('/intranet/asistencia/reportes')

      expect(await screen.findByRole('heading', { name: 'Reportes de asistencia' })).toBeInTheDocument()
      expect(screen.queryByLabelText('Ámbito')).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Sección')).not.toBeInTheDocument()
      expect(screen.getByLabelText('Mes')).toBeInTheDocument()
    })

    it('muestra la vista previa con el porcentaje, «No disponible» y los días no registrados', async () => {
      const consultas: string[] = []
      servidor.use(
        http.get('*/api/v1/reportes/asistencia', ({ request }) => {
          consultas.push(new URL(request.url).search)
          return HttpResponse.json(reporte())
        }),
      )
      await renderizarRuta('/intranet/asistencia/reportes')

      await userEvent.selectOptions(await screen.findByLabelText('Mes'), 'Marzo')
      await userEvent.clear(screen.getByLabelText('Año'))
      await userEvent.type(screen.getByLabelText('Año'), '2027')
      await userEvent.click(screen.getByRole('button', { name: 'Ver reporte' }))

      const tabla = await screen.findByRole('table', { name: 'Resumen mensual de asistencia' })
      const filas = within(tabla).getAllByRole('row').slice(1)
      expect(filas[0]).toHaveTextContent('Alva, Lucía')
      expect(filas[0]).toHaveTextContent('95.24 %')
      expect(filas[1]).toHaveTextContent('Bravo, Tomás')
      expect(filas[1]).toHaveTextContent('No disponible')
      expect(filas[1]).toHaveTextContent('22')
      expect(screen.getByText(/Asistencia de alumnos · 3° A · Marzo 2027/)).toBeInTheDocument()
      expect(screen.getByText(/Información incompleta: 1 día esperado sin registro/)).toBeInTheDocument()
      expect(screen.getByText(/22 días lectivos considerados/)).toBeInTheDocument()
      const parametros = new URLSearchParams(consultas[0])
      expect(parametros.get('ambito')).toBe('ALUMNOS')
      expect(parametros.get('mes')).toBe('3')
      expect(parametros.get('anio')).toBe('2027')
      expect(parametros.get('formato')).toBe('json')
      expect(parametros.has('seccion')).toBe(false)
    })

    it('FA-1: un periodo en curso se marca como parcial con su fecha de corte', async () => {
      servidor.use(http.get('*/api/v1/reportes/asistencia', () => HttpResponse.json(reporte({ parcial: true, fechaCorte: '2027-03-14' }))))
      await renderizarRuta('/intranet/asistencia/reportes')

      await userEvent.click(await screen.findByRole('button', { name: 'Ver reporte' }))

      expect(await screen.findByText(/Reporte parcial: el mes sigue en curso y se calcula hasta el 14\/03\/2027/)).toBeInTheDocument()
    })

    it.each([
      ['SIN_REGISTROS_ASISTENCIA', 'No hay asistencias registradas para 3° A en mayo 2027.'],
      ['PERIODO_INVALIDO', 'No se puede pedir el reporte de un periodo futuro: corrige el mes o el año.'],
      ['CALENDARIO_NO_DISPONIBLE', 'No hay un año escolar activo: no puede determinarse el universo de días esperados.'],
    ])('muestra el motivo del servidor con %s y no genera tabla', async (codigo, mensaje) => {
      servidor.use(http.get('*/api/v1/reportes/asistencia', () => problema(422, codigo, mensaje)))
      await renderizarRuta('/intranet/asistencia/reportes')

      await userEvent.click(await screen.findByRole('button', { name: 'Ver reporte' }))

      expect(await screen.findByText(mensaje)).toBeInTheDocument()
      expect(screen.queryByRole('table', { name: 'Resumen mensual de asistencia' })).not.toBeInTheDocument()
    })

    it('FA-3: una sección ajena (403) se informa como error', async () => {
      servidor.use(http.get('*/api/v1/reportes/asistencia', () => problema(403, 'FUERA_DE_ALCANCE', 'Solo puedes exportar el reporte de tu sección 3° A')))
      await renderizarRuta('/intranet/asistencia/reportes')

      await userEvent.click(await screen.findByRole('button', { name: 'Ver reporte' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('Solo puedes exportar el reporte de tu sección 3° A')
    })

    it('descarga Excel y PDF con los filtros y el nombre que propone el servidor', async () => {
      const pedidos: string[] = []
      servidor.use(
        http.get('*/api/v1/reportes/asistencia', ({ request }) => {
          const formato = new URL(request.url).searchParams.get('formato')
          pedidos.push(String(formato))
          return new HttpResponse(formato === 'pdf' ? '%PDF' : 'xlsx', {
            headers: {
              'Content-Type': formato === 'pdf' ? 'application/pdf' : 'application/octet-stream',
              'Content-Disposition': `attachment; filename="asistencia-alumnos-3A-2027-03.${formato}"`,
            },
          })
        }),
      )
      const descargas: string[] = []
      vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:prueba', revokeObjectURL: () => undefined }))
      vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
        descargas.push(this.download)
      })
      await renderizarRuta('/intranet/asistencia/reportes')

      await userEvent.click(await screen.findByRole('button', { name: 'Descargar Excel' }))
      await waitFor(() => expect(descargas).toEqual(['asistencia-alumnos-3A-2027-03.xlsx']))
      await userEvent.click(screen.getByRole('button', { name: 'Descargar PDF' }))
      await waitFor(() => expect(descargas).toEqual(['asistencia-alumnos-3A-2027-03.xlsx', 'asistencia-alumnos-3A-2027-03.pdf']))

      expect(pedidos).toEqual(['xlsx', 'pdf'])
    })

    it('si la descarga falla (FA-2) muestra el motivo y no guarda nada', async () => {
      servidor.use(http.get('*/api/v1/reportes/asistencia', () => problema(422, 'SIN_REGISTROS_ASISTENCIA', 'No hay asistencias registradas para 3° A en mayo 2027.')))
      const descargas: string[] = []
      vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
        descargas.push(this.download)
      })
      await renderizarRuta('/intranet/asistencia/reportes')

      await userEvent.click(await screen.findByRole('button', { name: 'Descargar Excel' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('No hay asistencias registradas para 3° A en mayo 2027.')
      expect(descargas).toHaveLength(0)
    })

    it('el menú ofrece «Reporte de asistencia de mi sección»', async () => {
      await renderizarRuta('/intranet/inicio')

      const enlaces = await screen.findAllByRole('link', { name: /Reporte de asistencia de mi sección/ })
      for (const enlace of enlaces) expect(enlace).toHaveAttribute('href', '/intranet/asistencia/reportes')
    })
  })

  describe('como administrador', () => {
    beforeEach(() => {
      guardarSesion(sesionDe('ADMINISTRADOR', ['ASISTENCIA_REPORTE_GENERAL', 'ACADEMICO_CONSULTAR'], { cargo: 'Dirección' }))
      servidor.use(http.get('*/api/v1/anios-escolares/2027/grados', () => HttpResponse.json(GRADOS)))
    })

    it('elige el ámbito y la sección, y el código de la sección viaja en la consulta', async () => {
      const consultas: string[] = []
      servidor.use(
        http.get('*/api/v1/reportes/asistencia', ({ request }) => {
          consultas.push(new URL(request.url).search)
          return HttpResponse.json(reporte({ alcance: '3° B' }))
        }),
      )
      await renderizarRuta('/intranet/asistencia/reportes')

      await screen.findByRole('option', { name: '3° B' })
      await userEvent.selectOptions(screen.getByLabelText('Sección'), '3° B')
      await userEvent.click(screen.getByRole('button', { name: 'Ver reporte' }))

      await screen.findByRole('table', { name: 'Resumen mensual de asistencia' })
      expect(new URLSearchParams(consultas[0]).get('seccion')).toBe('3B')
    })

    it('el ámbito de docentes no muestra sección ni código y avisa que el cierre pudo no ejecutarse', async () => {
      servidor.use(
        http.get('*/api/v1/reportes/asistencia', () =>
          HttpResponse.json(
            reporte({
              ambito: 'DOCENTES',
              alcance: 'Personal docente',
              filas: [fila('d1', 'Quispe Mendoza, Rosa', { codigo: null, seccion: null, porcentaje: '75.00' })],
            }),
          ),
        ),
      )
      await renderizarRuta('/intranet/asistencia/reportes')

      await userEvent.selectOptions(await screen.findByLabelText('Ámbito'), 'Docentes')
      expect(screen.queryByLabelText('Sección')).not.toBeInTheDocument()
      await userEvent.click(screen.getByRole('button', { name: 'Ver reporte' }))

      const tabla = await screen.findByRole('table', { name: 'Resumen mensual de asistencia' })
      expect(within(tabla).queryByRole('columnheader', { name: 'Sección' })).not.toBeInTheDocument()
      expect(within(tabla).getByText('75.00 %')).toBeInTheDocument()
      expect(screen.getByText(/en docentes puede señalar que el cierre diario no se ejecutó/)).toBeInTheDocument()
    })

    it('el menú ofrece «Reportes de asistencia»', async () => {
      await renderizarRuta('/intranet/inicio')

      expect((await screen.findAllByRole('link', { name: /Reportes de asistencia/ })).length).toBeGreaterThan(0)
    })
  })

  it('un usuario sin permisos de reporte no entra', async () => {
    guardarSesion(sesionDe('ALUMNO', ['HORARIO_CONSULTAR']))
    await renderizarRuta('/intranet/asistencia/reportes')

    expect(screen.queryByRole('button', { name: 'Ver reporte' })).not.toBeInTheDocument()
  })
})
