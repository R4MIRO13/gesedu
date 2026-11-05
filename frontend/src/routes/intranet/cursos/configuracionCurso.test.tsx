import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { Asignacion, ConfiguracionCurso, TipoRegla } from '../../../modulos/evaluacion/api'
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
  alumnos: 28,
  competencias: 2,
  bimestreVigente: 1,
}

const NIVELES_EJEMPLO = ['AD', 'AD', 'A', 'B'] as const

function configuracion(regla: TipoRegla = 'PROMEDIO_PONDERADO', extra: Partial<ConfiguracionCurso> = {}): ConfiguracionCurso {
  return {
    asignacionId: 'asig-1',
    curso: 'Matemática',
    seccion: '3° A',
    anio: 2027,
    regla,
    reglas: [
      {
        tipo: 'PROMEDIO_PONDERADO',
        nombre: 'Promedio ponderado',
        descripcion: 'Convierte AD=4, A=3, B=2, C=1, promedia y redondea al nivel más cercano.',
        porDefecto: true,
        ejemplo: { niveles: [...NIVELES_EJEMPLO], resultado: 'A' },
      },
      {
        tipo: 'MODA',
        nombre: 'Moda',
        descripcion: 'Toma el nivel que más se repite entre los bimestres.',
        porDefecto: false,
        ejemplo: { niveles: [...NIVELES_EJEMPLO], resultado: 'AD' },
      },
      {
        tipo: 'ULTIMO_REGISTRO',
        nombre: 'Último registro',
        descripcion: 'Toma el nivel obtenido en el último bimestre.',
        porDefecto: false,
        ejemplo: { niveles: [...NIVELES_EJEMPLO], resultado: 'B' },
      },
    ],
    pesos: [1, 2, 3, 4].map((bimestre) => ({ bimestre, peso: '1.00', personalizado: false })),
    competencias: [
      { id: 'c1', codigo: 'C1', nombre: 'Resuelve problemas de cantidad', nivelEsperado: 'A' },
      { id: 'c2', codigo: 'C2', nombre: 'Resuelve problemas de regularidad', nivelEsperado: 'A' },
    ],
    editable: true,
    cambios: 0,
    ...extra,
  }
}

function simular(inicial: ConfiguracionCurso, puts: unknown[] = []) {
  let actual = inicial
  servidor.use(
    http.get('*/api/v1/asignaciones-docentes/mias', () => HttpResponse.json([{ ...ASIGNACION, regla: actual.regla }])),
    http.get('*/api/v1/asignaciones-docentes/asig-1/configuracion', () => HttpResponse.json(actual)),
    http.put('*/api/v1/asignaciones-docentes/asig-1/configuracion', async ({ request }) => {
      const cuerpo = (await request.json()) as { regla: TipoRegla; pesos?: { bimestre: number; peso: string }[] }
      puts.push(cuerpo)
      actual = configuracion(cuerpo.regla, {
        pesos: actual.pesos.map((p) => {
          const pedido = cuerpo.pesos?.find((x) => x.bimestre === p.bimestre)
          const peso = pedido ? Number(pedido.peso).toFixed(2) : '1.00'
          return { ...p, peso, personalizado: peso !== '1.00' }
        }),
        cambios: 1,
      })
      return HttpResponse.json(actual)
    }),
  )
}

describe('Configuración del curso: regla de consolidación (UC-16, RF-26, RF-38)', () => {
  beforeEach(() => guardarSesion(sesionDe('DOCENTE', ['NOTA_REGISTRAR', 'REGLA_DEFINIR'], { nombres: 'Rosa Quispe' })))

  it('muestra la regla actual, las tres opciones con su ejemplo y los pesos por bimestre', async () => {
    simular(configuracion())
    await renderizarRuta('/intranet/cursos/matematica-3A/configuracion')

    expect(await screen.findByRole('heading', { name: 'Configuración del curso · Matemática · 3° A' })).toBeInTheDocument()
    expect(screen.getByText('Regla actual: Promedio ponderado')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Promedio ponderado · Por defecto · Regla actual/ })).toBeChecked()
    expect(screen.getByRole('radio', { name: /Moda/ })).not.toBeChecked()
    // El motor da un resultado distinto con cada regla para los mismos bimestres
    expect(screen.getAllByText(/Ejemplo con los bimestres I a IV/).map((e) => e.textContent)).toEqual([
      'Ejemplo con los bimestres I a IV: AD, AD, A, B → A',
      'Ejemplo con los bimestres I a IV: AD, AD, A, B → AD',
      'Ejemplo con los bimestres I a IV: AD, AD, A, B → B',
    ])
    for (const n of ['I', 'II', 'III', 'IV']) expect(screen.getByLabelText(`Bimestre ${n}`)).toHaveValue('1.00')
    expect(screen.getByText('2 competencias')).toBeInTheDocument()
    expect(screen.getByText('C1 · Resuelve problemas de cantidad')).toBeInTheDocument()
  })

  it('cambia la regla (FA-1) y la pantalla muestra la nueva como actual', async () => {
    const puts: unknown[] = []
    simular(configuracion(), puts)
    await renderizarRuta('/intranet/cursos/matematica-3A/configuracion')
    await screen.findByText('Regla actual: Promedio ponderado')

    await userEvent.click(screen.getByRole('radio', { name: /Último registro/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar configuración' }))

    expect(await screen.findByText('Se guardó la configuración del curso.')).toBeInTheDocument()
    expect(puts).toEqual([{ regla: 'ULTIMO_REGISTRO', pesos: [] }])
    expect(screen.getByText('Regla actual: Último registro')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Último registro · Regla actual/ })).toBeChecked()
  })

  it('manda solo los pesos distintos de 1, con punto decimal', async () => {
    const puts: unknown[] = []
    simular(configuracion(), puts)
    await renderizarRuta('/intranet/cursos/matematica-3A/configuracion')
    await screen.findByLabelText('Bimestre III')

    await userEvent.clear(screen.getByLabelText('Bimestre III'))
    await userEvent.type(screen.getByLabelText('Bimestre III'), '2')
    await userEvent.clear(screen.getByLabelText('Bimestre IV'))
    await userEvent.type(screen.getByLabelText('Bimestre IV'), '2,5')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar configuración' }))

    await screen.findByText('Se guardó la configuración del curso.')
    expect(puts).toEqual([
      {
        regla: 'PROMEDIO_PONDERADO',
        pesos: [
          { bimestre: 3, peso: '2' },
          { bimestre: 4, peso: '2.5' },
        ],
      },
    ])
    expect(screen.getByLabelText('Bimestre IV')).toHaveValue('2.50')
  })

  it('valida los pesos antes de enviar: cero, texto, demasiados decimales, mayor que 100 o vacío', async () => {
    const puts: unknown[] = []
    simular(configuracion(), puts)
    await renderizarRuta('/intranet/cursos/matematica-3A/configuracion')
    await screen.findByLabelText('Bimestre I')

    const casos: [string, string][] = [
      ['0', 'El peso debe ser mayor que cero'],
      ['abc', 'Usa un número con hasta dos decimales'],
      ['1.234', 'Usa un número con hasta dos decimales'],
      ['150', 'El peso no puede ser mayor que 100'],
      ['', 'Indica el peso'],
    ]
    for (const [valor, mensaje] of casos) {
      await userEvent.clear(screen.getByLabelText('Bimestre I'))
      if (valor) await userEvent.type(screen.getByLabelText('Bimestre I'), valor)
      await userEvent.click(screen.getByRole('button', { name: 'Guardar configuración' }))
      expect(await screen.findByText(mensaje)).toBeInTheDocument()
    }
    expect(puts).toHaveLength(0)
  })

  it('con una regla distinta del promedio avisa que los pesos no se usan', async () => {
    simular(configuracion('MODA'))
    await renderizarRuta('/intranet/cursos/matematica-3A/configuracion')

    expect(await screen.findByText(/Los pesos solo se usan con el promedio ponderado/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('radio', { name: /Promedio ponderado/ }))
    expect(screen.getByText(/Con el promedio ponderado todos los bimestres pesan lo mismo/)).toBeInTheDocument()
  })

  it('muestra el error del servidor sin perder lo marcado', async () => {
    simular(configuracion())
    servidor.use(
      http.put('*/api/v1/asignaciones-docentes/asig-1/configuracion', () =>
        problema(400, 'VALIDACION_INVALIDA', 'La configuración no es válida: no se guardó nada'),
      ),
    )
    await renderizarRuta('/intranet/cursos/matematica-3A/configuracion')
    await screen.findByText('Regla actual: Promedio ponderado')

    await userEvent.click(screen.getByRole('radio', { name: /Moda/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar configuración' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('no se guardó nada')
    expect(screen.getByRole('radio', { name: /Moda/ })).toBeChecked()
  })

  it('con el año cerrado se consulta pero no se edita', async () => {
    simular(configuracion('MODA', { editable: false }))
    await renderizarRuta('/intranet/cursos/matematica-3A/configuracion')

    expect(await screen.findByText(/El año escolar está cerrado/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar configuración' })).toBeDisabled()
    expect(screen.getByLabelText('Bimestre I')).toBeDisabled()
  })

  it('un curso que no dicta o una sesión sin REGLA_DEFINIR no entra', async () => {
    simular(configuracion())
    const { unmount } = await renderizarRuta('/intranet/cursos/lenguaje-3A/configuracion')
    expect(await screen.findByText(/No dictas este curso en esa sección/)).toBeInTheDocument()
    unmount()

    guardarSesion(sesionDe('ALUMNO', ['HORARIO_CONSULTAR'], { nombres: 'Lucía García' }))
    const { router } = await renderizarRuta('/intranet/cursos/matematica-3A/configuracion')
    await waitFor(() => expect(router.state.location.pathname).not.toBe('/intranet/cursos/matematica-3A/configuracion'))
  })

  it('la lista de mis cursos lleva a la configuración de cada uno', async () => {
    simular(configuracion())
    const { router } = await renderizarRuta('/intranet/notas')

    await userEvent.click(await screen.findByRole('link', { name: 'Configurar la regla de Matemática en 3° A' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/intranet/cursos/matematica-3A/configuracion'))
    expect(await screen.findByText('Regla actual: Promedio ponderado')).toBeInTheDocument()
  })
})
