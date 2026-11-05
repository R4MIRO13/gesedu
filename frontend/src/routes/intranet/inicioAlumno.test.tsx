import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../compartido/auth/sesion'
import type { ResumenAsistenciaAlumno } from '../../modulos/asistencia/api'
import { problema, sesionDe } from '../../test/msw/handlers'
import { servidor } from '../../test/msw/servidor'
import { renderizarRuta } from '../../test/utilidades'

function resumen(extra: Partial<ResumenAsistenciaAlumno> = {}): ResumenAsistenciaAlumno {
  return {
    alumnoId: 'a1',
    anio: 2027,
    mes: 3,
    puntual: 7,
    tardanza: 2,
    falta: 1,
    registrados: 10,
    diasEsperados: 11,
    noRegistrados: 1,
    porcentaje: '90.00',
    parcial: true,
    fechaCorte: '2027-03-14',
    ...extra,
  }
}

function simular(datos: ResumenAsistenciaAlumno | 'error', consultas: string[] = []) {
  servidor.use(
    http.get('*/api/v1/matriculas/mias', () =>
      HttpResponse.json([{ alumnoId: 'a1', codigoAlumno: '2026-0100', alumno: 'Lucía García Rojas', matricula: null }]),
    ),
    http.get('*/api/v1/alumnos/:id/asistencia/resumen', ({ params }) => {
      consultas.push(String(params.id))
      return datos === 'error' ? problema(422, 'CALENDARIO_NO_DISPONIBLE', 'No hay un año escolar activo: no puede determinarse el universo de días esperados.') : HttpResponse.json(datos)
    }),
  )
}

describe('Inicio del alumno: resumen de asistencia (RF-16)', () => {
  beforeEach(() => guardarSesion(sesionDe('ALUMNO', ['ASISTENCIA_CONSULTAR_PROPIA', 'HORARIO_CONSULTAR', 'MATRICULA_CONSULTAR_PROPIA'], { nombres: 'Lucía' })))

  it('muestra el porcentaje del mes, los conteos y los días sin registro como no faltas', async () => {
    const consultas: string[] = []
    simular(resumen(), consultas)
    await renderizarRuta('/intranet/inicio')

    expect(await screen.findByRole('heading', { name: 'Mi asistencia de marzo' })).toBeInTheDocument()
    expect(screen.getByLabelText('Porcentaje de asistencia')).toHaveTextContent('90.00 %')
    expect(screen.getByText('Puntual 7')).toBeInTheDocument()
    expect(screen.getByText('Tardanza 2')).toBeInTheDocument()
    expect(screen.getByText('Falta 1')).toBeInTheDocument()
    expect(screen.getByText(/1 día sin registro: no se cuentan como faltas/)).toBeInTheDocument()
    expect(screen.getByText(/Mes en curso: calculado hasta el 14\/03\/2027/)).toBeInTheDocument()
    expect(consultas).toEqual(['a1'])
  })

  it('sin días registrados el porcentaje no está disponible (no un 0 %)', async () => {
    simular(resumen({ puntual: 0, tardanza: 0, falta: 0, registrados: 0, noRegistrados: 11, porcentaje: null, parcial: false }))
    await renderizarRuta('/intranet/inicio')

    expect(await screen.findByText('No disponible')).toBeInTheDocument()
    expect(screen.queryByLabelText('Porcentaje de asistencia')).not.toBeInTheDocument()
    expect(screen.queryByText(/Mes en curso/)).not.toBeInTheDocument()
  })

  it('si el servidor no puede calcularlo muestra el motivo', async () => {
    simular('error')
    await renderizarRuta('/intranet/inicio')

    expect(await screen.findByText(/No hay un año escolar activo/)).toBeInTheDocument()
  })

  it('otros roles no ven la tarjeta ni piden el resumen', async () => {
    guardarSesion(sesionDe('DOCENTE', ['HORARIO_CONSULTAR'], { nombres: 'Rosa' }))
    const consultas: string[] = []
    simular(resumen(), consultas)
    await renderizarRuta('/intranet/inicio')

    expect(await screen.findByRole('heading', { name: 'Hola, Rosa' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /Mi asistencia de/ })).not.toBeInTheDocument()
    expect(consultas).toHaveLength(0)
  })
})
