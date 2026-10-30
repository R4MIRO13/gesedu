import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../../compartido/auth/sesion'
import type { CopiaAnterior, Curso, CursosAnio } from '../../../../modulos/academico/api'
import { problema, sesionDe } from '../../../../test/msw/handlers'
import { servidor } from '../../../../test/msw/servidor'
import { renderizarRuta } from '../../../../test/utilidades'

const SESION = sesionDe('ADMINISTRADOR', ['ACADEMICO_CONFIGURAR', 'ACADEMICO_CONSULTAR'], { cargo: 'Coordinación académica' })

const MATEMATICA: Curso = {
  id: 'c-mat',
  nombre: 'Matemática',
  slug: 'matematica',
  horasLectivas: 5,
  habilitado: true,
  competencias: [
    { id: 'k1', codigo: 'C1', nombre: 'Resuelve problemas de cantidad', nivelEsperado: 'Traduce cantidades', orden: 1 },
    { id: 'k2', codigo: 'C2', nombre: 'Resuelve problemas de regularidad', nivelEsperado: null, orden: 2 },
  ],
}
const INGLES: Curso = {
  id: 'c-ing',
  nombre: 'Inglés',
  slug: 'ingles',
  horasLectivas: 3,
  habilitado: true,
  competencias: [{ id: 'k3', codigo: 'C1', nombre: 'Se comunica oralmente en inglés', nivelEsperado: null, orden: 1 }],
}

const CURSOS: CursosAnio = { anio: 2027, estado: 'ACTIVO', totalHoras: 8, cursos: [INGLES, MATEMATICA] }

function simular(cursos: CursosAnio = CURSOS, copia: CopiaAnterior = { anioOrigen: null, cursos: [] }) {
  servidor.use(
    http.get('*/api/v1/anios-escolares/2027/cursos', () => HttpResponse.json(cursos)),
    http.get('*/api/v1/anios-escolares/2027/cursos/copia-anterior', () => HttpResponse.json(copia)),
  )
}

describe('Cursos y competencias (UC-14, RF-23, RF-24)', () => {
  beforeEach(() => guardarSesion(SESION))

  it('lista los cursos con sus horas y muestra las competencias del curso elegido', async () => {
    simular()
    await renderizarRuta('/intranet/academico/2027/cursos')

    expect(await screen.findByText('2 cursos · 8 h semanales')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Matemática/ }))

    const detalle = screen.getByRole('form', { name: 'Editar curso' })
    expect(within(detalle).getByLabelText('Nombre del curso *')).toHaveValue('Matemática')
    expect(within(detalle).getByLabelText('Horas lectivas semanales *')).toHaveValue('5')
    expect(within(detalle).getByLabelText('Competencia C1')).toHaveValue('Resuelve problemas de cantidad')
    expect(within(detalle).getByText('2 competencias · se evalúan con AD, A, B o C')).toBeInTheDocument()
  })

  it('crea un curso con sus competencias y avisa del resultado', async () => {
    const cuerpos: unknown[] = []
    simular()
    servidor.use(
      http.post('*/api/v1/anios-escolares/2027/cursos', async ({ request }) => {
        cuerpos.push(await request.json())
        return HttpResponse.json(
          { id: 'c-arte', nombre: 'Arte', slug: 'arte', horasLectivas: 2, habilitado: true, competencias: [{ id: 'k9', codigo: 'C1', nombre: 'Crea proyectos', nivelEsperado: null, orden: 1 }] },
          { status: 201 },
        )
      }),
    )
    await renderizarRuta('/intranet/academico/2027/cursos')

    await userEvent.click(await screen.findByRole('button', { name: '+ Nuevo curso' }))
    await userEvent.type(screen.getByLabelText('Nombre del curso *'), 'Arte')
    await userEvent.type(screen.getByLabelText('Horas lectivas semanales *'), '2')
    await userEvent.type(screen.getByLabelText('Nombre de la nueva competencia'), 'Crea proyectos')
    await userEvent.click(screen.getByRole('button', { name: 'Agregar competencia' }))
    expect(screen.getByLabelText('Competencia nueva')).toHaveValue('Crea proyectos')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar curso' }))

    expect(await screen.findByText('Se creó el curso Arte con 1 competencia.')).toBeInTheDocument()
    expect(cuerpos).toEqual([{ nombre: 'Arte', horasLectivas: 2, competencias: [{ nombre: 'Crea proyectos', nivelEsperado: null }] }])
  })

  it('exige nombre, horas y al menos una competencia antes de enviar', async () => {
    simular()
    await renderizarRuta('/intranet/academico/2027/cursos')

    await userEvent.click(await screen.findByRole('button', { name: '+ Nuevo curso' }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar curso' }))

    expect(await screen.findByText('Ingresa el nombre del curso.')).toBeInTheDocument()
    expect(screen.getByText('Ingresa las horas semanales (un número entero).')).toBeInTheDocument()
    expect(screen.getByText('El curso necesita al menos una competencia.')).toBeInTheDocument()
  })

  it('al editar quita una competencia y manda el id de las que se conservan, incluida la nueva sin id', async () => {
    const cuerpos: unknown[] = []
    simular()
    servidor.use(
      http.put('*/api/v1/cursos/c-mat', async ({ request }) => {
        cuerpos.push(await request.json())
        return HttpResponse.json(MATEMATICA)
      }),
    )
    await renderizarRuta('/intranet/academico/2027/cursos')

    await userEvent.click(await screen.findByRole('button', { name: /Matemática/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Quitar la competencia C2' }))
    await userEvent.type(screen.getByLabelText('Nombre de la nueva competencia'), 'Gestiona datos')
    await userEvent.click(screen.getByRole('button', { name: 'Agregar competencia' }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar curso' }))

    expect(await screen.findByText('Se guardó el curso Matemática.')).toBeInTheDocument()
    expect(cuerpos).toEqual([
      {
        nombre: 'Matemática',
        horasLectivas: 5,
        competencias: [
          { id: 'k1', nombre: 'Resuelve problemas de cantidad', nivelEsperado: 'Traduce cantidades' },
          { nombre: 'Gestiona datos', nivelEsperado: null },
        ],
      },
    ])
  })

  it('muestra el mensaje del backend cuando el nombre ya existe', async () => {
    simular()
    servidor.use(
      http.post('*/api/v1/anios-escolares/2027/cursos', () => problema(409, 'CURSO_DUPLICADO', 'Ya existe el curso «Arte» en el año escolar')),
    )
    await renderizarRuta('/intranet/academico/2027/cursos')

    await userEvent.click(await screen.findByRole('button', { name: '+ Nuevo curso' }))
    await userEvent.type(screen.getByLabelText('Nombre del curso *'), 'Arte')
    await userEvent.type(screen.getByLabelText('Horas lectivas semanales *'), '2')
    await userEvent.type(screen.getByLabelText('Nombre de la nueva competencia'), 'Crea')
    await userEvent.click(screen.getByRole('button', { name: 'Agregar competencia' }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar curso' }))

    expect(await screen.findByText('Ya existe el curso «Arte» en el año escolar')).toBeInTheDocument()
  })

  it('elimina un curso tras confirmar y explica por qué no se puede si está en uso', async () => {
    simular()
    let intentos = 0
    servidor.use(
      http.delete('*/api/v1/cursos/c-ing', () => {
        intentos++
        return intentos === 1
          ? problema(409, 'CURSO_EN_USO', 'El curso ya tiene docentes, cargas, clases o logros asociados y no se puede eliminar')
          : new HttpResponse(null, { status: 204 })
      }),
    )
    await renderizarRuta('/intranet/academico/2027/cursos')

    await userEvent.click(await screen.findByRole('button', { name: /Inglés/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Eliminar curso' }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar eliminación' }))
    expect(await screen.findByText(/no se puede eliminar/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar curso' }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar eliminación' }))
    expect(await screen.findByText('Se eliminó el curso Inglés.')).toBeInTheDocument()
  })

  it('FA-1: copia los cursos del año anterior que el año aún no tiene', async () => {
    const cuerpos: unknown[] = []
    simular(
      { anio: 2027, estado: 'PLANIFICADO', totalHoras: 3, cursos: [INGLES] },
      {
        anioOrigen: 2026,
        cursos: [
          { id: 'o-mat', nombre: 'Matemática', horasLectivas: 5, competencias: MATEMATICA.competencias, yaExiste: false },
          { id: 'o-ing', nombre: 'Inglés', horasLectivas: 3, competencias: INGLES.competencias, yaExiste: true },
        ],
      },
    )
    servidor.use(
      http.post('*/api/v1/anios-escolares/2027/cursos/copia-anterior', async ({ request }) => {
        cuerpos.push(await request.json())
        return HttpResponse.json({ anioOrigen: 2026, copiados: [MATEMATICA], omitidos: [] })
      }),
    )
    await renderizarRuta('/intranet/academico/2027/cursos')

    await userEvent.click(await screen.findByRole('button', { name: 'Copiar configuración de 2026' }))
    const panel = screen.getByLabelText('Copiar configuración de 2026')
    expect(within(panel).getByRole('checkbox', { name: /Inglés/ })).toBeDisabled()
    expect(within(panel).getByText('Ya existe')).toBeInTheDocument()
    expect(within(panel).getByRole('checkbox', { name: /Matemática/ })).toBeChecked()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar copia' }))

    expect(await screen.findByText('Se copiaron 1 curso de 2026 con sus competencias.')).toBeInTheDocument()
    expect(cuerpos).toEqual([{ cursoIds: ['o-mat'] }])
  })

  it('quien solo consulta ve los cursos pero no puede crear ni copiar', async () => {
    guardarSesion(sesionDe('DOCENTE', ['ACADEMICO_CONSULTAR']))
    simular()
    await renderizarRuta('/intranet/academico/2027/cursos')

    expect(await screen.findByText('2 cursos · 8 h semanales')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '+ Nuevo curso' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Matemática/ }))
    expect(screen.getByLabelText('Nombre del curso *')).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Guardar curso' })).not.toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('button', { name: /Copiar configuración/ })).not.toBeInTheDocument())
  })

  it('un año cerrado solo se consulta', async () => {
    simular({ ...CURSOS, estado: 'CERRADO' })
    await renderizarRuta('/intranet/academico/2027/cursos')

    expect(await screen.findByText('Año cerrado: solo consulta')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '+ Nuevo curso' })).not.toBeInTheDocument()
  })
})
