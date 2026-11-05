import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../compartido/auth/sesion'
import type { AvanceBimestre, CursoAvance } from '../../../modulos/evaluacion/api'
import { problema, sesionDe } from '../../../test/msw/handlers'
import { servidor } from '../../../test/msw/servidor'
import { renderizarRuta } from '../../../test/utilidades'

function curso(id: string, nombre: string, seccion: string, docente: string, porcentaje: number): CursoAvance {
  return {
    asignacionId: id,
    curso: nombre,
    seccion,
    docente,
    celdasTotales: 100,
    celdasCompletas: porcentaje,
    porcentaje,
    completo: porcentaje === 100,
  }
}

function avance(numero: number, cursos: CursoAvance[], extra: Partial<AvanceBimestre> = {}): AvanceBimestre {
  const incompletos = cursos.filter((c) => !c.completo)
  return {
    anio: 2027,
    bimestre: { numero, fechaInicio: '2027-07-16', fechaFin: '2027-09-30', ultimo: numero === 4 },
    bimestres: [1, 2, 3, 4].map((n) => ({ numero: n, estado: 'SIN_CONSOLIDAR' as const })),
    estado: 'SIN_CONSOLIDAR',
    fechaConsolidacion: null,
    totales: { cursos: cursos.length, completos: cursos.length - incompletos.length, incompletos: incompletos.length },
    cursos,
    puedeConsolidar: incompletos.length === 0,
    motivo: incompletos.length === 0 ? null : `No se puede consolidar el Bimestre ${['', 'I', 'II', 'III', 'IV'][numero]}: ${incompletos.length} secciones incompletas`,
    docentesPendientes: incompletos.map((c) => c.docente),
    editable: true,
    resultado: null,
    ...extra,
  }
}

const INCOMPLETOS = [
  curso('a1', 'Inglés', '5° A', 'Víctor Salinas Quiroz', 60),
  curso('a2', 'Ciencia y Tecnología', '2° B', 'Hugo Gonzales Paredes', 75),
  curso('a3', 'Matemática', '3° A', 'Rosa Quispe Mendoza', 98),
  curso('a4', 'Comunicación', '1° B', 'Elena Vargas Ruiz', 100),
]

describe('Consolidar notas (UC-18, RF-27, RF-28, RF-35)', () => {
  beforeEach(() => guardarSesion(sesionDe('ADMINISTRADOR', ['NOTA_CONSOLIDAR'], { nombres: 'Ricardo Paredes', cargo: 'Directivo' })))

  it('muestra el avance por curso y sección, los totales y los docentes pendientes con el botón deshabilitado', async () => {
    servidor.use(http.get('*/api/v1/anios-escolares/2027/bimestres/3/avance', () => HttpResponse.json(avance(3, INCOMPLETOS))))
    await renderizarRuta('/intranet/academico/consolidar-notas?bimestre=3')

    expect(await screen.findByRole('heading', { name: 'Consolidar notas' })).toBeInTheDocument()
    expect(await screen.findByText(/Cierre del bimestre: 30\/09\/2027/)).toBeInTheDocument()
    expect(screen.getByText(/4 cursos por sección · Completos 1 · Incompletos 3/)).toBeInTheDocument()
    const filas = within(screen.getByRole('table', { name: 'Avance por curso y sección' })).getAllByRole('row').slice(1)
    expect(filas[0]).toHaveTextContent('Inglés5° AVíctor Salinas Quiroz60 %Incompleto')
    expect(filas[3]).toHaveTextContent('Comunicación1° BElena Vargas Ruiz100 %Completo')
    expect(screen.getByText('No se puede consolidar el Bimestre III: 3 secciones incompletas')).toBeInTheDocument()
    const pendientes = within(screen.getByRole('list', { name: 'Docentes pendientes' })).getAllByRole('listitem')
    expect(pendientes.map((p) => p.textContent)).toEqual([
      'Víctor Salinas Quiroz · Inglés · 5° A · 60 % registrado',
      'Hugo Gonzales Paredes · Ciencia y Tecnología · 2° B · 75 % registrado',
      'Rosa Quispe Mendoza · Matemática · 3° A · 98 % registrado',
    ])
    expect(screen.getByRole('button', { name: 'Consolidar Bimestre III' })).toBeDisabled()
    expect(screen.getByText(/El botón se habilita cuando todas las secciones tengan 100 % de sus notas/)).toBeInTheDocument()
  })

  it('con todo completo habilita el botón y consolida, mostrando el resumen', async () => {
    let consolidado = false
    servidor.use(
      http.get('*/api/v1/anios-escolares/2027/bimestres/1/avance', () =>
        HttpResponse.json(avance(1, [curso('a1', 'Matemática', '3° A', 'Rosa Quispe', 100)], consolidado ? { estado: 'COMPLETADA', fechaConsolidacion: '2027-03-15T10:00:00-05:00' } : {})),
      ),
      http.post('*/api/v1/anios-escolares/2027/bimestres/1/consolidacion', () => {
        consolidado = true
        return HttpResponse.json(
          avance(1, [curso('a1', 'Matemática', '3° A', 'Rosa Quispe', 100)], {
            estado: 'COMPLETADA',
            fechaConsolidacion: '2027-03-15T10:00:00-05:00',
            resultado: { creadas: 8, actualizadas: 0, sinCambios: 0, libretas: 3, finalizada: false, notasAnuales: null },
          }),
        )
      }),
    )
    await renderizarRuta('/intranet/academico/consolidar-notas')
    const boton = await screen.findByRole('button', { name: 'Consolidar Bimestre I' })
    expect(boton).toBeEnabled()

    await userEvent.click(boton)

    expect(await screen.findByText('Bimestre I consolidado: 8 notas nuevas, 0 actualizadas y 0 sin cambios; 3 libretas al día.')).toBeInTheDocument()
    expect(screen.getByText(/Consolidado el 15\/03\/2027/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Volver a consolidar Bimestre I' })).toBeEnabled()
  })

  it('en el último bimestre informa que se calcularon las notas finales', async () => {
    servidor.use(
      http.get('*/api/v1/anios-escolares/2027/bimestres/4/avance', () => HttpResponse.json(avance(4, [curso('a1', 'Matemática', '3° A', 'Rosa Quispe', 100)]))),
      http.post('*/api/v1/anios-escolares/2027/bimestres/4/consolidacion', () =>
        HttpResponse.json(
          avance(4, [curso('a1', 'Matemática', '3° A', 'Rosa Quispe', 100)], {
            estado: 'COMPLETADA',
            resultado: {
              creadas: 8,
              actualizadas: 0,
              sinCambios: 0,
              libretas: 3,
              finalizada: true,
              notasAnuales: { creadas: 8, actualizadas: 0, sinCambios: 0, sinNota: 0 },
            },
          }),
        ),
      ),
    )
    await renderizarRuta('/intranet/academico/consolidar-notas?bimestre=4')

    expect(await screen.findByText(/En el último bimestre también se calculan las notas finales del año/)).toBeInTheDocument()
    await userEvent.click(await screen.findByRole('button', { name: 'Consolidar Bimestre IV' }))

    expect(await screen.findByText(/Se calcularon las notas finales del año \(8 nuevas o cambiadas\) y las libretas quedaron finales/)).toBeInTheDocument()
  })

  it('un bimestre desactualizado avisa que hay que volver a consolidar', async () => {
    servidor.use(
      http.get('*/api/v1/anios-escolares/2027/bimestres/1/avance', () =>
        HttpResponse.json(avance(1, [curso('a1', 'Matemática', '3° A', 'Rosa Quispe', 100)], { estado: 'DESACTUALIZADA', fechaConsolidacion: '2027-03-15T10:00:00-05:00' })),
      ),
    )
    await renderizarRuta('/intranet/academico/consolidar-notas')

    expect(await screen.findByText(/Se modificaron logros después de consolidar el Bimestre I/)).toBeInTheDocument()
    expect(screen.getByText(/Desactualizado el 15\/03\/2027/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Volver a consolidar Bimestre I' })).toBeEnabled()
  })

  it('muestra el error del servidor si algo cambió y no se pudo consolidar', async () => {
    servidor.use(
      http.get('*/api/v1/anios-escolares/2027/bimestres/1/avance', () => HttpResponse.json(avance(1, [curso('a1', 'Matemática', '3° A', 'Rosa Quispe', 100)]))),
      http.post('*/api/v1/anios-escolares/2027/bimestres/1/consolidacion', () =>
        problema(422, 'BIMESTRE_INCOMPLETO', 'No se puede consolidar el Bimestre I: 1 sección incompleta'),
      ),
    )
    await renderizarRuta('/intranet/academico/consolidar-notas')

    await userEvent.click(await screen.findByRole('button', { name: 'Consolidar Bimestre I' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se puede consolidar el Bimestre I: 1 sección incompleta')
  })

  it('cambiar de bimestre consulta el avance de ese bimestre', async () => {
    servidor.use(
      http.get('*/api/v1/anios-escolares/2027/bimestres/1/avance', () => HttpResponse.json(avance(1, [curso('a1', 'Matemática', '3° A', 'Rosa Quispe', 100)]))),
      http.get('*/api/v1/anios-escolares/2027/bimestres/2/avance', () => HttpResponse.json(avance(2, INCOMPLETOS))),
    )
    const { router } = await renderizarRuta('/intranet/academico/consolidar-notas')
    await screen.findByRole('button', { name: 'Consolidar Bimestre I' })

    await userEvent.selectOptions(screen.getByLabelText('Bimestre'), '2')

    await waitFor(() => expect(router.state.location.search).toEqual({ bimestre: 2 }))
    expect(await screen.findByRole('button', { name: 'Consolidar Bimestre II' })).toBeDisabled()
  })

  it('con el año cerrado se consulta pero no se consolida', async () => {
    servidor.use(
      http.get('*/api/v1/anios-escolares/2027/bimestres/1/avance', () =>
        HttpResponse.json(avance(1, [curso('a1', 'Matemática', '3° A', 'Rosa Quispe', 100)], { editable: false, puedeConsolidar: false, motivo: 'El año escolar está cerrado: ya no se consolida.' })),
      ),
    )
    await renderizarRuta('/intranet/academico/consolidar-notas')

    expect(await screen.findByText(/El año escolar está cerrado: ya no se consolida/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Consolidar Bimestre I' })).toBeDisabled()
  })

  it('un docente o un alumno no entran a la pantalla', async () => {
    guardarSesion(sesionDe('DOCENTE', ['NOTA_REGISTRAR'], { nombres: 'Rosa Quispe' }))
    const { router } = await renderizarRuta('/intranet/academico/consolidar-notas')

    await waitFor(() => expect(router.state.location.pathname).not.toBe('/intranet/academico/consolidar-notas'))
  })
})
