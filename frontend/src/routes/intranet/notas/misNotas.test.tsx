import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { AlumnoNotas, CursoNotas, CursosAlumno, Libreta, NivelLogro } from '../../../modulos/evaluacion/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

function competencia(id: string, codigo: string, nombre: string, bimestres: Record<string, NivelLogro>, anual: NivelLogro | null = null) {
  return { competenciaId: id, codigo, nombre, nivelEsperado: `Nivel esperado de ${codigo}`, bimestres, anual }
}

const MATEMATICA: CursoNotas = {
  cursoId: 'mat',
  curso: 'Matemática',
  docente: 'Rosa Quispe Mendoza',
  competencias: [
    competencia('c1', 'C1', 'Resuelve problemas de cantidad', { '1': 'AD', '2': 'A' }),
    competencia('c2', 'C2', 'Resuelve problemas de regularidad', { '1': 'A', '2': 'B' }),
  ],
}
const COMUNICACION: CursoNotas = {
  cursoId: 'com',
  curso: 'Comunicación',
  docente: 'Jorge Mendoza Paz',
  competencias: [competencia('k1', 'C1', 'Se comunica oralmente', { '1': 'A', '2': 'A' })],
}

function cursos(extra: Partial<CursosAlumno> = {}): CursosAlumno {
  return {
    alumnoId: 'a1',
    alumno: 'Lucía García Rojas',
    codigo: '2026-0315',
    anio: 2027,
    seccion: '3° A',
    anios: [2027, 2026],
    bimestres: [1, 2, 3, 4].map((numero) => ({ numero, consolidado: numero <= 2 })),
    bimestre: 2,
    libreta: 'PARCIAL',
    cursos: [COMUNICACION, MATEMATICA],
    ...extra,
  }
}

const LUCIA: AlumnoNotas = { alumnoId: 'a1', codigo: '2026-0315', nombre: 'Lucía García Rojas', seccion: '3° A', anio: 2027 }
const TOMAS: AlumnoNotas = { alumnoId: 'a2', codigo: '2026-0409', nombre: 'Tomás Torres Gil', seccion: '1° A', anio: 2027 }

function simular(alumnos: AlumnoNotas[], respuesta: (alumnoId: string, url: URL) => CursosAlumno | Response, consultas: string[] = []) {
  servidor.use(
    http.get('*/api/v1/apoderados/yo/alumnos', () => HttpResponse.json(alumnos)),
    http.get('*/api/v1/alumnos/:id/cursos', ({ params, request }) => {
      const url = new URL(request.url)
      consultas.push(`${params.id}${url.search}`)
      const r = respuesta(String(params.id), url)
      return r instanceof Response ? r : HttpResponse.json(r)
    }),
  )
}

describe('Mis notas (UC-19, RF-30, RF-32 a RF-36)', () => {
  beforeEach(() => guardarSesion(sesionDe('ALUMNO', ['NOTA_CONSULTAR_PROPIA'], { nombres: 'Lucía García' })))
  afterEach(() => vi.restoreAllMocks())

  it('el alumno ve los cursos de su sección con las notas del último bimestre consolidado', async () => {
    simular([LUCIA], () => cursos())
    await renderizarRuta('/intranet/notas')

    expect(await screen.findByRole('heading', { name: 'Mis cursos · 3° A · Nota del Bimestre II' })).toBeInTheDocument()
    expect(screen.getByText(/Libreta parcial/)).toBeInTheDocument()
    const lista = screen.getByRole('list', { name: 'Cursos' })
    expect(within(lista).getAllByRole('listitem').map((c) => c.getAttribute('aria-label'))).toEqual(['Comunicación', 'Matemática'])
    expect(screen.getByLabelText('Notas de Matemática en el bimestre II')).toHaveTextContent('C1 A · C2 B')
    expect(screen.getByLabelText('Notas de Comunicación en el bimestre II')).toHaveTextContent('C1 A')
    expect(screen.getByText('Prof. Rosa Quispe Mendoza')).toBeInTheDocument()
    // Con un solo estudiante no hace falta elegir
    expect(screen.queryByLabelText('Estudiante')).not.toBeInTheDocument()
  })

  it('el detalle de un curso muestra las competencias con su nivel esperado y las notas de cada bimestre', async () => {
    simular([LUCIA], () => cursos())
    await renderizarRuta('/intranet/notas')
    await screen.findByRole('heading', { name: /Mis cursos/ })

    await userEvent.click(screen.getByRole('button', { name: /Ver detalle de Matemática/ }))

    const tabla = screen.getByRole('table', { name: 'Detalle de Matemática' })
    const filas = within(tabla).getAllByRole('row').slice(1)
    expect(filas[0]).toHaveTextContent('C1 · Resuelve problemas de cantidadNivel esperado de C1ADA———')
    expect(filas[1]).toHaveTextContent('C2 · Resuelve problemas de regularidadNivel esperado de C2AB———')
    await userEvent.click(screen.getByRole('button', { name: /Ocultar detalle de Matemática/ }))
    expect(screen.queryByRole('table', { name: 'Detalle de Matemática' })).not.toBeInTheDocument()
  })

  it('cambiar de bimestre consulta ese bimestre', async () => {
    const consultas: string[] = []
    simular([LUCIA], (_id, url) => cursos({ bimestre: Number(url.searchParams.get('bimestre') ?? 2) }), consultas)
    await renderizarRuta('/intranet/notas')
    await screen.findByRole('heading', { name: /Nota del Bimestre II/ })

    await userEvent.selectOptions(screen.getByLabelText('Bimestre'), '1')

    expect(await screen.findByRole('heading', { name: /Nota del Bimestre I$/ })).toBeInTheDocument()
    expect(consultas).toEqual(['a1', 'a1?bimestre=1'])
    expect(screen.getByRole('option', { name: 'Bimestre III (sin consolidar)' })).toBeInTheDocument()
  })

  it('FA-1: el apoderado con varios hijos elige a uno y ve sus cursos', async () => {
    guardarSesion(sesionDe('APODERADO', ['NOTA_CONSULTAR_PROPIA'], { nombres: 'María Rojas' }))
    const consultas: string[] = []
    simular(
      [LUCIA, TOMAS],
      (id) => (id === 'a1' ? cursos() : cursos({ alumnoId: 'a2', alumno: 'Tomás Torres Gil', seccion: '1° A', libreta: null, cursos: [COMUNICACION] })),
      consultas,
    )
    await renderizarRuta('/intranet/notas')

    const selector = await screen.findByLabelText('Estudiante')
    expect(within(selector).getAllByRole('option').map((o) => o.textContent)).toEqual(['Lucía García Rojas · 3° A', 'Tomás Torres Gil · 1° A'])
    expect(await screen.findByRole('heading', { name: /Mis cursos · 3° A/ })).toBeInTheDocument()

    await userEvent.selectOptions(selector, 'a2')

    expect(await screen.findByRole('heading', { name: /Mis cursos · 1° A/ })).toBeInTheDocument()
    expect(screen.getByText(/Tomás Torres Gil/, { selector: 'p' })).toBeInTheDocument()
    expect(consultas).toEqual(['a1', 'a2'])
  })

  it('un año anterior se consulta con su libreta final', async () => {
    simular([LUCIA], (_id, url) =>
      url.searchParams.get('anio') === '2026' ? cursos({ anio: 2026, seccion: '2° A', libreta: 'FINAL', bimestre: 4, cursos: [MATEMATICA] }) : cursos(),
    )
    await renderizarRuta('/intranet/notas')
    await screen.findByRole('heading', { name: /Mis cursos/ })

    await userEvent.selectOptions(screen.getByLabelText('Año escolar'), '2026')

    expect(await screen.findByRole('heading', { name: 'Mis cursos · 2° A · Nota del Bimestre IV' })).toBeInTheDocument()
    expect(screen.getByText(/Libreta final/)).toBeInTheDocument()
  })

  it('sin notas consolidadas lo dice y no deja descargar', async () => {
    simular([LUCIA], () => cursos({ bimestres: [1, 2, 3, 4].map((numero) => ({ numero, consolidado: false })), bimestre: 1, libreta: null, cursos: [{ ...MATEMATICA, competencias: [competencia('c1', 'C1', 'Resuelve problemas de cantidad', {})] }] }))
    await renderizarRuta('/intranet/notas')

    expect(await screen.findByText(/Todavía no hay notas consolidadas en 2027/)).toBeInTheDocument()
    expect(screen.getByLabelText('Notas de Matemática en el bimestre I')).toHaveTextContent('C1 —')
    expect(screen.getByRole('button', { name: 'Descargar informe de progreso' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Ver libreta completa' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Descargar libreta' })).toBeDisabled()
  })

  it('FA-2: la libreta completa se despliega con la nota final y se descarga en PDF', async () => {
    const libreta: Libreta = {
      alumnoId: 'a1',
      alumno: 'Lucía García Rojas',
      codigo: '2026-0315',
      seccion: '3° A',
      anio: 2027,
      estado: 'PARCIAL',
      fechaActualizacion: '2027-03-15T10:00:00-05:00',
      hastaBimestre: null,
      bimestres: [1, 2],
      cursos: [{ ...MATEMATICA, competencias: [competencia('c1', 'C1', 'Resuelve problemas de cantidad', { '1': 'AD', '2': 'A' }, 'A')] }],
    }
    const descargas: string[] = []
    servidor.use(
      http.get('*/api/v1/alumnos/a1/libreta', ({ request }) => {
        const url = new URL(request.url)
        if (url.searchParams.get('formato') === 'pdf') {
          descargas.push(url.search)
          return new HttpResponse('%PDF-1.7', { headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="libreta-2026-0315-2027.pdf"' } })
        }
        return HttpResponse.json(libreta)
      }),
    )
    simular([LUCIA], () => cursos())
    const guardados: string[] = []
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:prueba', revokeObjectURL: () => undefined }))
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      guardados.push(this.download)
    })
    await renderizarRuta('/intranet/notas')
    await screen.findByRole('heading', { name: /Mis cursos/ })

    await userEvent.click(screen.getByRole('button', { name: 'Ver libreta completa' }))

    const tabla = await screen.findByRole('table', { name: 'Libreta de notas' })
    expect(within(tabla).getAllByRole('row')[1]).toHaveTextContent('MatemáticaC1 · Resuelve problemas de cantidadADAA')
    expect(screen.getByRole('heading', { name: /Libreta parcial 2027 · actualizada el 15\/03\/2027/ })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Descargar libreta' }))
    await userEvent.click(screen.getByRole('button', { name: 'Descargar informe de progreso' }))

    await waitFor(() => expect(descargas).toEqual(['?formato=pdf&anio=2027', '?formato=pdf&anio=2027&bimestre=2']))
    expect(guardados[0]).toBe('libreta-2026-0315-2027.pdf')
  })

  it('un error de la descarga se muestra', async () => {
    servidor.use(
      http.get('*/api/v1/alumnos/a1/libreta', () => problema(422, 'SIN_NOTAS_CONSOLIDADAS', 'Aún no hay notas consolidadas en la libreta de 2027.')),
    )
    simular([LUCIA], () => cursos())
    await renderizarRuta('/intranet/notas')
    await screen.findByRole('heading', { name: /Mis cursos/ })

    await userEvent.click(screen.getByRole('button', { name: 'Descargar libreta' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Aún no hay notas consolidadas en la libreta de 2027.')
  })

  it('el inicio del alumno muestra las notas del último bimestre consolidado', async () => {
    simular([LUCIA], () => cursos())
    await renderizarRuta('/intranet/inicio')

    expect(await screen.findByRole('heading', { name: 'Mis notas del Bimestre II' })).toBeInTheDocument()
    const lista = screen.getByRole('list', { name: 'Notas por curso' })
    expect(within(lista).getAllByRole('listitem').map((li) => li.textContent)).toEqual(['Comunicación: C1 A', 'Matemática: C1 A · C2 B'])
    expect(screen.getByRole('link', { name: 'Ver mis notas y mi libreta' })).toBeInTheDocument()
  })

  it('el inicio sin notas consolidadas avisa que aparecerán', async () => {
    simular([LUCIA], () => cursos({ bimestres: [1, 2, 3, 4].map((numero) => ({ numero, consolidado: false })), libreta: null }))
    await renderizarRuta('/intranet/inicio')

    expect(await screen.findByText(/Aún no hay notas consolidadas/)).toBeInTheDocument()
  })

  it('un administrador no entra a las notas', async () => {
    guardarSesion(sesionDe('ADMINISTRADOR', ['NOTA_CONSOLIDAR'], { nombres: 'Carmen Salas' }))
    const { router } = await renderizarRuta('/intranet/notas')

    await waitFor(() => expect(router.state.location.pathname).not.toBe('/intranet/notas'))
  })
})
