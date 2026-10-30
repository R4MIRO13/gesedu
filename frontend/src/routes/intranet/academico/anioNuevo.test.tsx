import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { Bimestre, ProblemaAnio, ValidacionAnio } from '../../../modulos/academico/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const SESION = sesionDe('ADMINISTRADOR', ['ACADEMICO_CONFIGURAR', 'ACADEMICO_CONSULTAR'], { cargo: 'Coordinación académica' })

const CRUCE: ProblemaAnio = {
  tipo: 'BIMESTRES_SUPERPUESTOS',
  bimestre: 3,
  otroBimestre: 2,
  desde: '2027-06-25',
  hasta: '2027-07-02',
  mensaje: 'El Bimestre III se cruza con el Bimestre II. Corrige las fechas. (del 25/06/2027 al 02/07/2027)',
}

const BIMESTRES_OK: Bimestre[] = [
  { numero: 1, fechaInicio: '2027-02-15', fechaFin: '2027-04-30' },
  { numero: 2, fechaInicio: '2027-05-03', fechaFin: '2027-07-09' },
  { numero: 3, fechaInicio: '2027-07-26', fechaFin: '2027-10-01' },
  { numero: 4, fechaInicio: '2027-10-04', fechaFin: '2027-12-20' },
]

/** El backend simulado: año de 10 meses y 5 días válido; 9 meses inválido; el bimestre III de 2027-06-25 se cruza. */
function simular(cuerpos: unknown[] = []) {
  servidor.use(
    http.post('*/api/v1/anios-escolares/validaciones', async ({ request }) => {
      const c = (await request.json()) as { fechaInicio: string; fechaFin: string; bimestres?: Bimestre[] }
      const corto = c.fechaFin < '2027-12-01'
      const cruzado = c.bimestres?.some((b) => b.numero === 3 && b.fechaInicio === '2027-06-25')
      const problemas: ProblemaAnio[] = corto
        ? [{ tipo: 'ANIO_FUERA_DE_DURACION', bimestre: null, otroBimestre: null, desde: null, hasta: null, mensaje: 'El año escolar debe durar entre 10 y 12 meses' }]
        : cruzado
          ? [CRUCE]
          : []
      const cuerpo: ValidacionAnio = { valido: problemas.length === 0, duracion: corto ? { meses: 9, dias: 0 } : { meses: 10, dias: 5 }, problemas }
      return HttpResponse.json(cuerpo)
    }),
    http.post('*/api/v1/anios-escolares', async ({ request }) => {
      const c = (await request.json()) as { fechaInicio: string; fechaFin: string; bimestres: Bimestre[] }
      cuerpos.push(c)
      return HttpResponse.json(
        { id: 'a-2027', anio: 2027, fechaInicio: c.fechaInicio, fechaFin: c.fechaFin, estado: 'PLANIFICADO', bimestres: c.bimestres },
        { status: 201 },
      )
    }),
  )
}

function poner(etiqueta: string, valor: string) {
  fireEvent.change(screen.getByLabelText(etiqueta), { target: { value: valor } })
}

async function pasarAlPaso2() {
  await renderizarRuta('/intranet/academico/anio-escolar/nuevo')
  poner('Fecha de inicio', '2027-02-15')
  poner('Fecha de fin', '2027-12-20')
  expect(await screen.findByText('Duración: 10 meses y 5 días')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  expect(await screen.findByLabelText('Inicio del Bimestre I')).toHaveValue('2027-02-15')
}

describe('Nuevo año escolar (UC-13, RF-03, RF-25)', () => {
  beforeEach(() => guardarSesion(SESION))

  it('muestra la duración del año y pasa al paso de los bimestres con el primero y el último ya sugeridos', async () => {
    simular()
    await pasarAlPaso2()

    expect(screen.getByLabelText('Fin del Bimestre IV')).toHaveValue('2027-12-20')
    expect(screen.getByRole('region', { name: 'Línea de tiempo del año escolar 2027' })).toBeInTheDocument()
  })

  it('FA-1: un año que no dura entre 10 y 12 meses muestra el mensaje y no deja avanzar', async () => {
    simular()
    await renderizarRuta('/intranet/academico/anio-escolar/nuevo')
    poner('Fecha de inicio', '2027-02-15')
    poner('Fecha de fin', '2027-11-14')

    expect(await screen.findByText('El año escolar debe durar entre 10 y 12 meses')).toBeInTheDocument()
    expect(screen.getByText('Duración: 9 meses y 0 días')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(screen.queryByLabelText('Inicio del Bimestre I')).not.toBeInTheDocument()
  })

  it('exige las dos fechas antes de avanzar', async () => {
    simular()
    await renderizarRuta('/intranet/academico/anio-escolar/nuevo')

    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))

    expect(await screen.findAllByText('Indica la fecha.')).toHaveLength(2)
  })

  it('FA-2: un bimestre que se cruza muestra el aviso del wireframe y bloquea la creación', async () => {
    simular()
    await pasarAlPaso2()
    poner('Inicio del Bimestre II', '2027-04-26')
    poner('Fin del Bimestre II', '2027-07-02')
    poner('Inicio del Bimestre III', '2027-06-25')
    poner('Fin del Bimestre III', '2027-09-10')
    poner('Inicio del Bimestre IV', '2027-09-20')

    expect(await screen.findByText(CRUCE.mensaje)).toBeInTheDocument()
    expect(screen.getByText('Se superpone')).toBeInTheDocument()
    expect(screen.getByText('1 error')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear año escolar' })).toBeDisabled()
  })

  it('crea el año con los cuatro bimestres y confirma el registro', async () => {
    const cuerpos: unknown[] = []
    simular(cuerpos)
    await pasarAlPaso2()
    for (const b of BIMESTRES_OK) {
      const romano = ['I', 'II', 'III', 'IV'][b.numero - 1]
      poner(`Inicio del Bimestre ${romano}`, b.fechaInicio)
      poner(`Fin del Bimestre ${romano}`, b.fechaFin)
    }
    await waitFor(() => expect(screen.getByRole('button', { name: 'Crear año escolar' })).toBeEnabled())

    await userEvent.click(screen.getByRole('button', { name: 'Crear año escolar' }))

    expect(await screen.findByText(/El año escolar 2027 quedó registrado \(15\/02\/2027 – 20\/12\/2027\)/)).toBeInTheDocument()
    expect(cuerpos).toEqual([{ fechaInicio: '2027-02-15', fechaFin: '2027-12-20', bimestres: BIMESTRES_OK }])
  })

  it('si el backend rechaza la creación muestra su mensaje', async () => {
    simular()
    servidor.use(
      http.post('*/api/v1/anios-escolares', () => problema(409, 'ANIO_ESCOLAR_DUPLICADO', 'Ya existe el año escolar 2027')),
    )
    await pasarAlPaso2()
    for (const b of BIMESTRES_OK) {
      const romano = ['I', 'II', 'III', 'IV'][b.numero - 1]
      poner(`Inicio del Bimestre ${romano}`, b.fechaInicio)
      poner(`Fin del Bimestre ${romano}`, b.fechaFin)
    }
    await waitFor(() => expect(screen.getByRole('button', { name: 'Crear año escolar' })).toBeEnabled())
    await userEvent.click(screen.getByRole('button', { name: 'Crear año escolar' }))

    expect(await screen.findByText('Ya existe el año escolar 2027')).toBeInTheDocument()
  })

  it('un usuario sin ACADEMICO_CONFIGURAR no entra a la pantalla', async () => {
    guardarSesion(sesionDe('DOCENTE', ['ACADEMICO_CONSULTAR']))
    simular()
    await renderizarRuta('/intranet/academico/anio-escolar/nuevo')

    expect(screen.queryByRole('button', { name: 'Siguiente' })).not.toBeInTheDocument()
  })
})
