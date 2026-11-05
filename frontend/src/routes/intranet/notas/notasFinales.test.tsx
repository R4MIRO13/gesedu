import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { Asignacion, CeldaNota, NotasFinales, NivelLogro } from '../../../modulos/evaluacion/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

const ASIGNACION: Asignacion = {
  id: 'asig-1',
  cursoId: 'curso-1',
  curso: 'Matemática',
  cursoSlug: 'matematica',
  seccionId: 'sec-1',
  seccion: '3° A',
  seccionCodigo: '3A',
  anio: 2027,
  regla: 'PROMEDIO_PONDERADO',
  alumnos: 2,
  competencias: 2,
  bimestreVigente: 4,
}

function celda(niveles: (NivelLogro | null)[], anual: NivelLogro | null = null): CeldaNota {
  const bimestres: Record<string, NivelLogro> = {}
  niveles.forEach((n, i) => {
    if (n) bimestres[String(i + 1)] = n
  })
  return { bimestres, anual }
}

function notas(extra: Partial<NotasFinales> = {}): NotasFinales {
  return {
    asignacionId: 'asig-1',
    curso: 'Matemática',
    seccion: '3° A',
    anio: 2027,
    regla: 'PROMEDIO_PONDERADO',
    bimestres: [1, 2, 3, 4],
    competencias: [
      { id: 'c1', codigo: 'C1', nombre: 'Resuelve problemas de cantidad', nivelEsperado: 'A' },
      { id: 'c2', codigo: 'C2', nombre: 'Resuelve problemas de regularidad', nivelEsperado: 'A' },
    ],
    alumnos: [
      { alumnoId: '1', codigo: '2027-001', apellidos: 'Alva', nombres: 'Lucía', competencias: { c1: celda(['AD', 'A', 'A', 'B']), c2: celda([null, null, null, null]) } },
      { alumnoId: '2', codigo: '2027-002', apellidos: 'Bravo', nombres: 'Tomás', competencias: { c1: celda(['C', 'B', null, null]), c2: celda([null, null, null, null]) } },
    ],
    calculadas: false,
    desactualizada: false,
    editable: true,
    celdasSinNota: 2,
    calculo: null,
    ...extra,
  }
}

function simular(inicial: NotasFinales, posts: number[] = []) {
  let actual = inicial
  servidor.use(
    http.get('*/api/v1/asignaciones-docentes/mias', () => HttpResponse.json([ASIGNACION])),
    http.get('*/api/v1/asignaciones-docentes/asig-1/notas-finales', () => HttpResponse.json(actual)),
    http.post('*/api/v1/asignaciones-docentes/asig-1/notas-finales/calculo', () => {
      posts.push(1)
      const nuevas = posts.length === 1
      actual = notas({
        calculadas: true,
        alumnos: actual.alumnos.map((a, i) => ({
          ...a,
          competencias: { ...a.competencias, c1: { ...a.competencias.c1!, anual: i === 0 ? 'A' : 'B' } },
        })),
        calculo: nuevas ? { creadas: 2, actualizadas: 0, sinCambios: 0, sinNota: 2 } : { creadas: 0, actualizadas: 0, sinCambios: 2, sinNota: 2 },
      })
      return HttpResponse.json(actual)
    }),
  )
}

describe('Notas finales del curso (UC-17, RF-28, RF-29)', () => {
  beforeEach(() => guardarSesion(sesionDe('DOCENTE', ['NOTA_REGISTRAR', 'REGLA_DEFINIR', 'NOTA_CALCULAR_FINAL'], { nombres: 'Rosa Quispe' })))

  it('muestra los alumnos con el logro de cada bimestre por competencia y la regla vigente', async () => {
    simular(notas())
    await renderizarRuta('/intranet/notas/matematica/3A/finales')

    expect(await screen.findByRole('heading', { name: 'Notas finales · Matemática · 3° A' })).toBeInTheDocument()
    expect(screen.getByText(/Promedio ponderado/)).toBeInTheDocument()
    const tabla = screen.getByRole('table', { name: 'Notas finales por competencia' })
    const filas = within(tabla).getAllByRole('row')
    // Encabezado de competencias, encabezado de bimestres y dos alumnos
    expect(filas).toHaveLength(4)
    expect(within(filas[1]!).getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['I', 'II', 'III', 'IV', 'Final', 'I', 'II', 'III', 'IV', 'Final'])
    expect(within(filas[2]!).getByRole('rowheader')).toHaveTextContent('Alva, Lucía')
    expect(within(filas[2]!).getAllByRole('cell').map((c) => c.textContent)).toEqual(['AD', 'A', 'A', 'B', '—', '—', '—', '—', '—', '—'])
    expect(within(filas[3]!).getAllByRole('cell').slice(0, 5).map((c) => c.textContent)).toEqual(['C', 'B', '—', '—', '—'])
  })

  it('avisa cuántas competencias no tienen ningún logro', async () => {
    simular(notas({ celdasSinNota: 3 }))
    await renderizarRuta('/intranet/notas/matematica/3A/finales')

    expect(await screen.findByText(/3 competencias de los alumnos no tienen ningún logro registrado/)).toBeInTheDocument()
  })

  it('calcula las notas finales y las muestra con el resumen del cálculo', async () => {
    const posts: number[] = []
    simular(notas(), posts)
    await renderizarRuta('/intranet/notas/matematica/3A/finales')
    await screen.findByRole('table', { name: 'Notas finales por competencia' })

    await userEvent.click(screen.getByRole('button', { name: 'Calcular notas finales' }))

    expect(await screen.findByText('Se calcularon las notas finales: 2 nuevas, 0 actualizadas y 0 sin cambios.')).toBeInTheDocument()
    expect(screen.getByLabelText('Alva, Lucía · C1 · nota final')).toHaveTextContent('A')
    expect(screen.getByLabelText('Bravo, Tomás · C1 · nota final')).toHaveTextContent('B')
    expect(posts).toHaveLength(1)
  })

  it('calcular otra vez sin cambios lo dice y no cambia lo mostrado', async () => {
    simular(notas())
    await renderizarRuta('/intranet/notas/matematica/3A/finales')
    await screen.findByRole('table', { name: 'Notas finales por competencia' })

    await userEvent.click(screen.getByRole('button', { name: 'Calcular notas finales' }))
    await screen.findByText(/Se calcularon las notas finales/)
    await userEvent.click(screen.getByRole('button', { name: 'Calcular notas finales' }))

    expect(await screen.findByText('Las notas finales ya estaban al día (2 sin cambios).')).toBeInTheDocument()
    expect(screen.getByLabelText('Alva, Lucía · C1 · nota final')).toHaveTextContent('A')
  })

  it('avisa que las notas están desactualizadas cuando cambiaron los logros, la regla o los pesos', async () => {
    simular(notas({ calculadas: true, desactualizada: true }))
    await renderizarRuta('/intranet/notas/matematica/3A/finales')

    expect(await screen.findByText(/Las notas finales están desactualizadas/)).toBeInTheDocument()
  })

  it('sin logros registrados muestra el error del servidor', async () => {
    simular(notas())
    servidor.use(
      http.post('*/api/v1/asignaciones-docentes/asig-1/notas-finales/calculo', () =>
        problema(422, 'SIN_LOGROS_REGISTRADOS', 'Aún no hay logros registrados en este curso: registra las notas de al menos un bimestre.'),
      ),
    )
    await renderizarRuta('/intranet/notas/matematica/3A/finales')
    await screen.findByRole('table', { name: 'Notas finales por competencia' })

    await userEvent.click(screen.getByRole('button', { name: 'Calcular notas finales' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Aún no hay logros registrados en este curso')
  })

  it('con el año cerrado se consulta pero no se calcula', async () => {
    simular(notas({ editable: false, calculadas: true }))
    await renderizarRuta('/intranet/notas/matematica/3A/finales')

    expect(await screen.findByText(/El año escolar está cerrado: las notas finales ya no se calculan/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Calcular notas finales' })).toBeDisabled()
  })

  it('un curso que no dicta se explica, y sin NOTA_CALCULAR_FINAL no se entra', async () => {
    simular(notas())
    const { unmount } = await renderizarRuta('/intranet/notas/lenguaje/3A/finales')
    expect(await screen.findByText(/No dictas este curso en esa sección/)).toBeInTheDocument()
    unmount()

    guardarSesion(sesionDe('ALUMNO', ['HORARIO_CONSULTAR'], { nombres: 'Lucía García' }))
    const { router } = await renderizarRuta('/intranet/notas/matematica/3A/finales')
    await waitFor(() => expect(router.state.location.pathname).not.toBe('/intranet/notas/matematica/3A/finales'))
  })

  it('la lista de mis cursos lleva a las notas finales de cada uno', async () => {
    simular(notas())
    const { router } = await renderizarRuta('/intranet/notas')

    await userEvent.click(await screen.findByRole('link', { name: 'Notas finales de Matemática en 3° A' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/intranet/notas/matematica/3A/finales'))
    expect(await screen.findByRole('heading', { name: 'Notas finales · Matemática · 3° A' })).toBeInTheDocument()
  })
})
