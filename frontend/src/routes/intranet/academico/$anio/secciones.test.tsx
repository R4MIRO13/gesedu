import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../../compartido/auth/sesion'
import type { GradosAnio } from '../../../../modulos/academico/api'
import { problema, sesionDe } from '../../../../test/msw/handlers'
import { servidor } from '../../../../test/msw/servidor'
import { renderizarRuta } from '../../../../test/utilidades'

const SESION = sesionDe('ADMINISTRADOR', ['ACADEMICO_CONFIGURAR', 'ACADEMICO_CONSULTAR'], { cargo: 'Coordinación académica' })

const GRADOS: GradosAnio = {
  anio: 2027,
  estado: 'ACTIVO',
  docentes: [
    { id: 'd-rosa', nombre: 'Rosa Quispe Mendoza' },
    { id: 'd-jorge', nombre: 'Jorge Mendoza Alva' },
  ],
  grados: [
    {
      id: 'g3',
      numeroNivel: 3,
      secciones: [
        { id: 's3a', gradoId: 'g3', letra: 'A', nombre: '3° A', capacidad: 30, vacantes: 4, matriculados: 26, tutorDocenteId: 'd-rosa', tutor: 'Rosa Quispe Mendoza', version: 2 },
        { id: 's3b', gradoId: 'g3', letra: 'B', nombre: '3° B', capacidad: 30, vacantes: 0, matriculados: 30, tutorDocenteId: null, tutor: null, version: 0 },
      ],
    },
    { id: 'g4', numeroNivel: 4, secciones: [] },
  ],
}

function simular(datos: GradosAnio = GRADOS) {
  servidor.use(http.get('*/api/v1/anios-escolares/2027/grados', () => HttpResponse.json(datos)))
}

describe('Secciones y vacantes (RF-03, RF-04)', () => {
  beforeEach(() => guardarSesion(SESION))

  it('muestra cada grado con la capacidad, los matriculados, las vacantes y el tutor', async () => {
    simular()
    await renderizarRuta('/intranet/academico/2027/secciones')

    const tabla = await screen.findByRole('table', { name: 'Secciones de 3°' })
    const filaA = within(tabla).getByRole('row', { name: /3° A/ })
    expect(filaA).toHaveTextContent('30')
    expect(filaA).toHaveTextContent('26')
    expect(filaA).toHaveTextContent('Rosa Quispe Mendoza')
    expect(within(tabla).getByRole('row', { name: /3° B/ })).toHaveTextContent('Sin tutor')
    expect(screen.getByText('Sin secciones todavía.')).toBeInTheDocument()
  })

  it('edita la capacidad y el tutor enviando la versión de la sección en If-Match', async () => {
    const recibido: { cuerpo: unknown; ifMatch: string | null }[] = []
    simular()
    servidor.use(
      http.put('*/api/v1/secciones/s3a', async ({ request }) => {
        recibido.push({ cuerpo: await request.json(), ifMatch: request.headers.get('If-Match') })
        return HttpResponse.json({ ...GRADOS.grados[0]!.secciones[0], capacidad: 35, vacantes: 9, version: 3, tutorDocenteId: 'd-jorge', tutor: 'Jorge Mendoza Alva' })
      }),
    )
    await renderizarRuta('/intranet/academico/2027/secciones')

    await userEvent.click(await screen.findByRole('button', { name: 'Editar 3° A' }))
    const form = screen.getByRole('form', { name: 'Editar 3° A' })
    const capacidad = within(form).getByLabelText('Capacidad')
    await userEvent.clear(capacidad)
    await userEvent.type(capacidad, '35')
    await userEvent.selectOptions(within(form).getByLabelText('Tutor'), 'd-jorge')
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText('Se guardó 3° A: capacidad 35, 9 vacantes.')).toBeInTheDocument()
    expect(recibido).toEqual([{ cuerpo: { capacidad: 35, tutorDocenteId: 'd-jorge' }, ifMatch: '"2"' }])
  })

  it('si la capacidad queda por debajo de los matriculados muestra el mensaje del backend', async () => {
    simular()
    servidor.use(
      http.put('*/api/v1/secciones/s3a', () =>
        problema(422, 'CAPACIDAD_INSUFICIENTE', 'La capacidad no puede ser menor que los 26 estudiantes ya matriculados'),
      ),
    )
    await renderizarRuta('/intranet/academico/2027/secciones')

    await userEvent.click(await screen.findByRole('button', { name: 'Editar 3° A' }))
    const form = screen.getByRole('form', { name: 'Editar 3° A' })
    const capacidad = within(form).getByLabelText('Capacidad')
    await userEvent.clear(capacidad)
    await userEvent.type(capacidad, '20')
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))

    expect(await within(form).findByText('La capacidad no puede ser menor que los 26 estudiantes ya matriculados')).toBeInTheDocument()
  })

  it('valida la capacidad antes de enviar', async () => {
    simular()
    await renderizarRuta('/intranet/academico/2027/secciones')

    await userEvent.click(await screen.findByRole('button', { name: 'Editar 3° B' }))
    const form = screen.getByRole('form', { name: 'Editar 3° B' })
    await userEvent.clear(within(form).getByLabelText('Capacidad'))
    await userEvent.type(within(form).getByLabelText('Capacidad'), '99')
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))

    expect(await within(form).findByText('La capacidad debe estar entre 1 y 60.')).toBeInTheDocument()
  })

  it('agrega una sección nueva con su letra en mayúscula', async () => {
    const cuerpos: unknown[] = []
    simular()
    servidor.use(
      http.post('*/api/v1/grados/g4/secciones', async ({ request }) => {
        cuerpos.push(await request.json())
        return HttpResponse.json(
          { id: 's4a', gradoId: 'g4', letra: 'A', nombre: '4° A', capacidad: 28, vacantes: 28, matriculados: 0, tutorDocenteId: null, tutor: null, version: 0 },
          { status: 201 },
        )
      }),
    )
    await renderizarRuta('/intranet/academico/2027/secciones')

    const form = await screen.findByRole('form', { name: 'Agregar sección a 4°' })
    await userEvent.type(within(form).getByLabelText('Letra'), 'a')
    const capacidad = within(form).getByLabelText('Capacidad')
    await userEvent.clear(capacidad)
    await userEvent.type(capacidad, '28')
    await userEvent.click(within(form).getByRole('button', { name: 'Agregar sección' }))

    expect(await screen.findByText('Se agregó la sección 4° A con 28 vacantes.')).toBeInTheDocument()
    expect(cuerpos).toEqual([{ letra: 'A', capacidad: 28, tutorDocenteId: null }])
  })

  it('exige la letra y avisa de una sección duplicada', async () => {
    simular()
    servidor.use(http.post('*/api/v1/grados/g3/secciones', () => problema(409, 'SECCION_DUPLICADA', 'Ya existe la sección 3° A')))
    await renderizarRuta('/intranet/academico/2027/secciones')

    const form = await screen.findByRole('form', { name: 'Agregar sección a 3°' })
    await userEvent.click(within(form).getByRole('button', { name: 'Agregar sección' }))
    expect(await within(form).findByText('Usa una sola letra, de la A a la Z.')).toBeInTheDocument()

    await userEvent.type(within(form).getByLabelText(/^Letra/), 'A')
    await userEvent.click(within(form).getByRole('button', { name: 'Agregar sección' }))
    expect(await within(form).findByText('Ya existe la sección 3° A')).toBeInTheDocument()
  })

  it('quien solo consulta no ve botones de edición y un año cerrado tampoco los muestra', async () => {
    guardarSesion(sesionDe('DOCENTE', ['ACADEMICO_CONSULTAR']))
    simular()
    await renderizarRuta('/intranet/academico/2027/secciones')
    await screen.findByRole('table', { name: 'Secciones de 3°' })
    expect(screen.queryByRole('button', { name: 'Editar 3° A' })).not.toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Agregar sección a 4°' })).not.toBeInTheDocument()
  })

  it('un año cerrado solo se consulta', async () => {
    simular({ ...GRADOS, estado: 'CERRADO' })
    await renderizarRuta('/intranet/academico/2027/secciones')

    expect(await screen.findByText('Año cerrado: solo consulta')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Editar 3° A' })).not.toBeInTheDocument()
  })
})
