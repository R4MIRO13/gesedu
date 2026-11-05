import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { Asignacion, BimestreInfo, FilaAlumno, NivelLogro, Rejilla } from '../../../modulos/evaluacion/api'
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
  alumnos: 3,
  competencias: 3,
  bimestreVigente: 1,
}

const BIMESTRES: BimestreInfo[] = [
  { numero: 1, fechaInicio: '2027-02-15', fechaFin: '2027-04-30', vigente: true },
  { numero: 2, fechaInicio: '2027-05-01', fechaFin: '2027-07-15', vigente: false },
  { numero: 3, fechaInicio: '2027-07-16', fechaFin: '2027-09-30', vigente: false },
  { numero: 4, fechaInicio: '2027-10-01', fechaFin: '2027-12-20', vigente: false },
]

const COMPETENCIAS = [
  { id: 'c1', codigo: 'C1', nombre: 'Resuelve problemas de cantidad', nivelEsperado: 'A' },
  { id: 'c2', codigo: 'C2', nombre: 'Resuelve problemas de regularidad', nivelEsperado: 'A' },
  { id: 'c3', codigo: 'C3', nombre: 'Resuelve problemas de forma', nivelEsperado: 'A' },
]

function fila(id: string, apellidos: string, nombres: string, logros: Record<string, NivelLogro> = {}): FilaAlumno {
  return { alumnoId: id, codigo: `2027-00${id}`, apellidos, nombres, logros }
}

function rejilla(alumnos: FilaAlumno[], extra: Partial<Rejilla> = {}): Rejilla {
  const completas = alumnos.reduce((n, a) => n + Object.keys(a.logros).length, 0)
  return {
    asignacionId: 'asig-1',
    curso: 'Matemática',
    seccion: '3° A',
    anio: 2027,
    bimestre: BIMESTRES[0]!,
    bimestres: BIMESTRES,
    editable: true,
    mensaje: null,
    consolidacion: null,
    avisoConsolidacion: null,
    competencias: COMPETENCIAS,
    alumnos,
    celdasTotales: alumnos.length * COMPETENCIAS.length,
    celdasCompletas: completas,
    incompletos: [],
    aviso: null,
    cambios: 0,
    ...extra,
  }
}

const ALUMNOS = [fila('1', 'Alva', 'Lucía'), fila('2', 'Bravo', 'Tomás'), fila('3', 'Castro', 'Mateo')]

function celda(nombre: string, competencia: string) {
  return screen.getByRole('combobox', { name: `${nombre} · ${competencia}` })
}

function simular(inicial: Rejilla, puts: unknown[] = []) {
  let actual = inicial
  servidor.use(
    http.get('*/api/v1/asignaciones-docentes/mias', () => HttpResponse.json([ASIGNACION])),
    http.get('*/api/v1/asignaciones-docentes/asig-1/registros', () => HttpResponse.json(actual)),
    http.put('*/api/v1/asignaciones-docentes/asig-1/registros', async ({ request }) => {
      const cuerpo = (await request.json()) as { logros: { alumnoId: string; competenciaId: string; nivel: NivelLogro }[] }
      puts.push(cuerpo)
      const alumnos = actual.alumnos.map((a) => ({
        ...a,
        logros: { ...a.logros, ...Object.fromEntries(cuerpo.logros.filter((l) => l.alumnoId === a.alumnoId).map((l) => [l.competenciaId, l.nivel])) },
      }))
      actual = rejilla(alumnos, { cambios: cuerpo.logros.length })
      return HttpResponse.json(actual)
    }),
  )
}

describe('Registro de notas (UC-15, RF-22, RF-31)', () => {
  beforeEach(() => guardarSesion(sesionDe('DOCENTE', ['NOTA_REGISTRAR', 'HORARIO_CONSULTAR'], { nombres: 'Rosa Quispe' })))

  it('lista mis cursos y secciones y lleva al registro del bimestre vigente', async () => {
    simular(rejilla(ALUMNOS))
    const { router } = await renderizarRuta('/intranet/notas')

    expect(await screen.findByRole('heading', { name: 'Registro de notas' })).toBeInTheDocument()
    const filas = within(await screen.findByRole('table', { name: 'Mis cursos y secciones' })).getAllByRole('row').slice(1)
    expect(filas).toHaveLength(1)
    expect(filas[0]).toHaveTextContent('Matemática')
    expect(filas[0]).toHaveTextContent('3° A')
    await userEvent.click(screen.getByRole('link', { name: 'Registrar notas de Matemática en 3° A' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/intranet/notas/matematica/3A/bimestre-1'))
    expect(await screen.findByRole('heading', { name: /Registro de notas · Matemática · 3° A · Bimestre I/ })).toBeInTheDocument()
  })

  it('muestra la rejilla con los alumnos en orden, las competencias y el contador de celdas', async () => {
    simular(rejilla(ALUMNOS))
    await renderizarRuta('/intranet/notas/matematica/3A/bimestre-1')

    expect(await screen.findByRole('table', { name: 'Rejilla de logros' })).toBeInTheDocument()
    const encabezados = within(screen.getByRole('table', { name: 'Rejilla de logros' })).getAllByRole('columnheader').map((h) => h.textContent)
    expect(encabezados).toEqual(['Estudiante', 'C1', 'C2', 'C3'])
    expect(screen.getByText('C1')).toHaveAttribute('title', 'Resuelve problemas de cantidad')
    const nombres = within(screen.getByRole('table', { name: 'Rejilla de logros' })).getAllByRole('rowheader').map((h) => h.textContent)
    expect(nombres).toEqual(['Alva, Lucía', 'Bravo, Tomás', 'Castro, Mateo'])
    expect(screen.getByText(/3 alumnos · 3 competencias · 0\/9 celdas completas/)).toBeInTheDocument()
    expect(screen.getByText('Escala de calificación: AD Logro destacado · A Logro esperado · B En proceso · C En inicio')).toBeInTheDocument()
    expect(within(celda('Alva, Lucía', 'C1')).getAllByRole('option').map((o) => o.textContent)).toEqual(['—', 'AD', 'A', 'B', 'C'])
  })

  it('avisa qué alumnos tienen competencias sin calificar (FA-2) y el aviso baja al completar la rejilla', async () => {
    simular(rejilla([fila('1', 'Alva', 'Lucía', { c1: 'A', c2: 'A', c3: 'B' }), fila('2', 'Bravo', 'Tomás', { c1: 'AD', c2: 'B' })]))
    await renderizarRuta('/intranet/notas/matematica/3A/bimestre-1')

    expect(await screen.findByText('Hay 1 alumno con competencias sin calificar: Bravo Tomás (C3)')).toBeInTheDocument()
    expect(screen.getByText(/5\/6 celdas completas/)).toBeInTheDocument()

    await userEvent.selectOptions(celda('Bravo, Tomás', 'C3'), 'A')

    expect(screen.queryByText(/con competencias sin calificar/)).not.toBeInTheDocument()
    expect(screen.getByText(/6\/6 celdas completas/)).toBeInTheDocument()
  })

  it('guarda solo las celdas que cambiaron y confirma el registro', async () => {
    const puts: unknown[] = []
    simular(rejilla([fila('1', 'Alva', 'Lucía', { c1: 'B' }), fila('2', 'Bravo', 'Tomás')]), puts)
    await renderizarRuta('/intranet/notas/matematica/3A/bimestre-1')
    const guardar = await screen.findByRole('button', { name: 'Guardar registro' })
    expect(guardar).toBeDisabled()

    await userEvent.selectOptions(celda('Alva, Lucía', 'C1'), 'AD')
    await userEvent.selectOptions(celda('Bravo, Tomás', 'C2'), 'C')
    expect(guardar).toBeEnabled()
    await userEvent.click(guardar)

    expect(await screen.findByText('Se guardó el registro: 2 celdas.')).toBeInTheDocument()
    expect(puts).toEqual([
      {
        logros: [
          { alumnoId: '1', competenciaId: 'c1', nivel: 'AD' },
          { alumnoId: '2', competenciaId: 'c2', nivel: 'C' },
        ],
      },
    ])
    expect(celda('Alva, Lucía', 'C1')).toHaveValue('AD')
    expect(screen.getByRole('button', { name: 'Guardar registro' })).toBeDisabled()
  })

  it('un logro que ya estaba guardado y no se toca no se vuelve a enviar', async () => {
    const puts: { logros: unknown[] }[] = []
    simular(rejilla([fila('1', 'Alva', 'Lucía', { c1: 'B', c2: 'A' })]), puts)
    await renderizarRuta('/intranet/notas/matematica/3A/bimestre-1')
    await screen.findByRole('table', { name: 'Rejilla de logros' })

    await userEvent.selectOptions(celda('Alva, Lucía', 'C3'), 'A')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar registro' }))

    await screen.findByText('Se guardó el registro: 1 celda.')
    expect(puts[0]?.logros).toEqual([{ alumnoId: '1', competenciaId: 'c3', nivel: 'A' }])
  })

  it('muestra el error del servidor y conserva lo que el docente marcó', async () => {
    simular(rejilla(ALUMNOS))
    servidor.use(
      http.put('*/api/v1/asignaciones-docentes/asig-1/registros', () =>
        problema(422, 'NIVEL_LOGRO_INVALIDO', 'Hay 1 logro que no pertenece a la escala AD, A, B o C: no se guardó nada', {
          errores: [{ campo: 'logros.1.c1', mensaje: 'El logro no es válido: usa AD, A, B o C' }],
        }),
      ),
    )
    await renderizarRuta('/intranet/notas/matematica/3A/bimestre-1')
    await screen.findByRole('table', { name: 'Rejilla de logros' })

    await userEvent.selectOptions(celda('Alva, Lucía', 'C1'), 'A')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar registro' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('no pertenece a la escala AD, A, B o C')
    expect(celda('Alva, Lucía', 'C1')).toHaveValue('A')
    expect(celda('Alva, Lucía', 'C1')).toHaveAttribute('aria-invalid', 'true')
  })

  it('un bimestre que ya terminó se consulta sin edición', async () => {
    simular(
      rejilla([fila('1', 'Alva', 'Lucía', { c1: 'AD' })], {
        editable: false,
        mensaje: 'El Bimestre I ya terminó: solo puedes consultar sus logros.',
        bimestres: BIMESTRES.map((b) => ({ ...b, vigente: b.numero === 2 })),
        bimestre: { ...BIMESTRES[0]!, vigente: false },
      }),
    )
    await renderizarRuta('/intranet/notas/matematica/3A/bimestre-1')

    expect(await screen.findByText('El Bimestre I ya terminó: solo puedes consultar sus logros.')).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Alva, Lucía · C1')).toHaveTextContent('AD')
    expect(screen.getByRole('button', { name: 'Guardar registro' })).toBeDisabled()
    expect(screen.getByRole('link', { name: 'Bimestre II (vigente)' })).toBeInTheDocument()
  })

  it('avisa que hay que reconsolidar cuando se editó un bimestre ya consolidado (S-68)', async () => {
    simular(
      rejilla([fila('1', 'Alva', 'Lucía', { c1: 'AD', c2: 'A', c3: 'A' })], {
        consolidacion: 'DESACTUALIZADA',
        avisoConsolidacion: 'Se modificaron logros después de consolidar el Bimestre I: hay que volver a consolidarlo.',
      }),
    )
    await renderizarRuta('/intranet/notas/matematica/3A/bimestre-1')

    expect(await screen.findByText(/hay que volver a consolidarlo/)).toBeInTheDocument()
  })

  it('un curso que el docente no dicta, o un bimestre inválido, se explica sin romper', async () => {
    simular(rejilla(ALUMNOS))
    const { unmount } = await renderizarRuta('/intranet/notas/lenguaje/3A/bimestre-1')
    expect(await screen.findByText(/No dictas este curso en esa sección/)).toBeInTheDocument()
    unmount()

    await renderizarRuta('/intranet/notas/matematica/3A/bimestre-9')
    expect(await screen.findByText(/El bimestre «bimestre-9» no existe/)).toBeInTheDocument()
  })

  it('sin el permiso NOTA_REGISTRAR no se entra a la pantalla', async () => {
    guardarSesion(sesionDe('ALUMNO', ['HORARIO_CONSULTAR'], { nombres: 'Lucía García' }))
    simular(rejilla(ALUMNOS))
    const { router } = await renderizarRuta('/intranet/notas')

    await waitFor(() => expect(router.state.location.pathname).not.toBe('/intranet/notas'))
  })
})
