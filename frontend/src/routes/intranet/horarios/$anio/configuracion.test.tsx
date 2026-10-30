import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion } from '../../../../compartido/auth/sesion'
import type { CargasHorarias, PeriodoDisponibilidad, VinculacionesAnio } from '../../../../modulos/horarios/api'
import { problema, sesionDe } from '../../../../test/msw/handlers'
import { servidor } from '../../../../test/msw/servidor'
import { renderizarRuta } from '../../../../test/utilidades'

const SESION = sesionDe('ADMINISTRADOR', ['HORARIO_CONFIGURAR', 'HORARIO_CONSULTAR'], { cargo: 'Secretaría académica' })

const PERIODO_ABIERTO: PeriodoDisponibilidad = {
  anio: 2027,
  configurado: true,
  fechaHoraInicio: '2027-01-04T08:00:00-05:00',
  fechaHoraFin: '2099-01-15T23:59:00-05:00',
  estado: 'HABILITADO',
  abierto: true,
}

const VINCULACIONES: VinculacionesAnio = {
  anio: 2027,
  estado: 'ACTIVO',
  cursos: [
    { id: 'c-mat', nombre: 'Matemática' },
    { id: 'c-ing', nombre: 'Inglés' },
  ],
  docentes: [
    {
      docenteId: 'd-rosa',
      nombre: 'Rosa Quispe Mendoza',
      vinculacionId: 'v-rosa',
      vinculado: true,
      habilitado: true,
      cargaSemanalReferencia: 20,
      cursoIds: ['c-mat'],
      disponibilidadConfirmada: true,
      bloquesDisponibles: 38,
      ultimaConfirmacion: '2027-01-10T16:12:00-05:00',
    },
    {
      docenteId: 'd-nuevo',
      nombre: 'Marta Ibáñez Ruiz',
      vinculacionId: null,
      vinculado: false,
      habilitado: false,
      cargaSemanalReferencia: 0,
      cursoIds: [],
      disponibilidadConfirmada: false,
      bloquesDisponibles: 0,
      ultimaConfirmacion: null,
    },
  ],
}

const CARGAS: CargasHorarias = {
  anio: 2027,
  estado: 'ACTIVO',
  bloquesPorSemana: 45,
  grados: [
    { id: 'g1', numeroNivel: 1 },
    { id: 'g2', numeroNivel: 2 },
  ],
  cursos: [
    { cursoId: 'c-mat', curso: 'Matemática', horasLectivas: 5, grados: [{ gradoId: 'g1', bloques: 5, personalizada: false }, { gradoId: 'g2', bloques: 6, personalizada: true }] },
    { cursoId: 'c-ing', curso: 'Inglés', horasLectivas: 3, grados: [{ gradoId: 'g1', bloques: 3, personalizada: false }, { gradoId: 'g2', bloques: 3, personalizada: false }] },
  ],
  totales: [
    { gradoId: 'g1', total: 8, excedeLaSemana: false },
    { gradoId: 'g2', total: 9, excedeLaSemana: false },
  ],
}

describe('Configuración de horarios (RF-46, RF-47, RF-43)', () => {
  beforeEach(() => guardarSesion(SESION))

  it('periodo: muestra hasta cuándo está abierto y guarda las fechas con el huso de Lima', async () => {
    const cuerpos: unknown[] = []
    servidor.use(
      http.get('*/api/v1/anios-escolares/2027/periodo-disponibilidad', () => HttpResponse.json(PERIODO_ABIERTO)),
      http.put('*/api/v1/anios-escolares/2027/periodo-disponibilidad', async ({ request }) => {
        const c = (await request.json()) as { fechaHoraInicio: string; fechaHoraFin: string; habilitado: boolean }
        cuerpos.push(c)
        return HttpResponse.json({ ...PERIODO_ABIERTO, fechaHoraInicio: c.fechaHoraInicio, fechaHoraFin: c.fechaHoraFin, estado: 'CERRADO', abierto: false })
      }),
    )
    await renderizarRuta('/intranet/horarios/2027/configuracion')

    expect(await screen.findByText(/Abierto hasta/)).toBeInTheDocument()
    const form = screen.getByRole('form', { name: 'Periodo de disponibilidad' })
    expect(within(form).getByLabelText('Desde')).toHaveValue('2027-01-04T08:00')
    await userEvent.click(within(form).getByRole('checkbox'))
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar periodo' }))

    expect(await screen.findByText('Se guardó el periodo de registro de disponibilidad.')).toBeInTheDocument()
    expect(cuerpos).toEqual([{ fechaHoraInicio: '2027-01-04T08:00:00-05:00', fechaHoraFin: '2099-01-15T23:59:00-05:00', habilitado: false }])
    expect(await screen.findByText('Cerrado')).toBeInTheDocument()
  })

  it('periodo: sin configurar exige las dos fechas', async () => {
    servidor.use(
      http.get('*/api/v1/anios-escolares/2027/periodo-disponibilidad', () =>
        HttpResponse.json({ anio: 2027, configurado: false, fechaHoraInicio: null, fechaHoraFin: null, estado: null, abierto: false }),
      ),
    )
    await renderizarRuta('/intranet/horarios/2027/configuracion')

    expect(await screen.findByText('Sin configurar')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Guardar periodo' }))
    expect(await screen.findByText('Indica cuándo empieza el periodo.')).toBeInTheDocument()
    expect(screen.getByText('Indica cuándo termina el periodo.')).toBeInTheDocument()
  })

  it('docentes: lista vinculados y no vinculados con su disponibilidad y guarda una vinculación nueva', async () => {
    const recibido: { docenteId: string; cuerpo: unknown }[] = []
    servidor.use(
      http.get('*/api/v1/anios-escolares/2027/vinculaciones-docentes', () => HttpResponse.json(VINCULACIONES)),
      http.put('*/api/v1/anios-escolares/2027/vinculaciones-docentes/:docenteId', async ({ request, params }) => {
        recibido.push({ docenteId: String(params.docenteId), cuerpo: await request.json() })
        return HttpResponse.json(VINCULACIONES.docentes[1])
      }),
    )
    await renderizarRuta('/intranet/horarios/2027/configuracion?seccion=docentes')

    const tabla = await screen.findByRole('table', { name: 'Docentes del año' })
    expect(within(tabla).getByRole('row', { name: /Rosa Quispe/ })).toHaveTextContent('38 bloques')
    expect(within(tabla).getByRole('row', { name: /Marta Ibáñez/ })).toHaveTextContent('No vinculado')

    await userEvent.click(screen.getByRole('button', { name: 'Vincular a Marta Ibáñez Ruiz' }))
    const form = screen.getByRole('form', { name: 'Vinculación de Marta Ibáñez Ruiz' })
    const carga = within(form).getByLabelText('Carga semanal (bloques)')
    await userEvent.clear(carga)
    await userEvent.type(carga, '10')
    await userEvent.click(within(form).getByRole('checkbox', { name: 'Inglés' }))
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar vinculación' }))

    expect(await screen.findByText('Se guardó la vinculación de Marta Ibáñez Ruiz.')).toBeInTheDocument()
    expect(recibido).toEqual([{ docenteId: 'd-nuevo', cuerpo: { habilitado: true, cargaSemanalReferencia: 10, cursoIds: ['c-ing'] } }])
  })

  it('docentes: muestra por qué no se puede quitar un curso con clases en el borrador', async () => {
    servidor.use(
      http.get('*/api/v1/anios-escolares/2027/vinculaciones-docentes', () => HttpResponse.json(VINCULACIONES)),
      http.put('*/api/v1/anios-escolares/2027/vinculaciones-docentes/d-rosa', () =>
        problema(409, 'DOCENTE_CON_CLASES', 'Rosa Quispe Mendoza tiene clases de Matemática en el borrador del horario: quítalas antes de desasociar el curso'),
      ),
    )
    await renderizarRuta('/intranet/horarios/2027/configuracion?seccion=docentes')

    await userEvent.click(await screen.findByRole('button', { name: 'Editar a Rosa Quispe Mendoza' }))
    const form = screen.getByRole('form', { name: 'Vinculación de Rosa Quispe Mendoza' })
    await userEvent.click(within(form).getByRole('checkbox', { name: 'Matemática' }))
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar vinculación' }))

    expect(await within(form).findByText(/tiene clases de Matemática en el borrador/)).toBeInTheDocument()
  })

  it('cargas: muestra la matriz y guarda solo las celdas que cambiaron', async () => {
    const cuerpos: unknown[] = []
    servidor.use(
      http.get('*/api/v1/anios-escolares/2027/cargas-horarias', () => HttpResponse.json(CARGAS)),
      http.put('*/api/v1/anios-escolares/2027/cargas-horarias', async ({ request }) => {
        cuerpos.push(await request.json())
        return HttpResponse.json(CARGAS)
      }),
    )
    await renderizarRuta('/intranet/horarios/2027/configuracion?seccion=cargas')

    expect(await screen.findByText(/La semana tiene 45 bloques/)).toBeInTheDocument()
    expect(screen.getByLabelText('Matemática 2°')).toHaveValue('6')
    expect(screen.getByRole('button', { name: 'Guardar cargas' })).toBeDisabled()
    const celda = screen.getByLabelText('Inglés 1°')
    await userEvent.clear(celda)
    await userEvent.type(celda, '4')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cargas' }))

    expect(await screen.findByText('Se guardaron las cargas horarias.')).toBeInTheDocument()
    expect(cuerpos).toEqual([{ cargas: [{ gradoId: 'g1', cursoId: 'c-ing', bloques: 4 }] }])
  })

  it('cargas: una celda inválida bloquea el guardado y un grado que excede la semana se advierte', async () => {
    servidor.use(
      http.get('*/api/v1/anios-escolares/2027/cargas-horarias', () =>
        HttpResponse.json({ ...CARGAS, totales: [{ gradoId: 'g1', total: 50, excedeLaSemana: true }, CARGAS.totales[1]] }),
      ),
    )
    await renderizarRuta('/intranet/horarios/2027/configuracion?seccion=cargas')

    expect(await screen.findByText(/su horario no se podrá completar/)).toBeInTheDocument()
    const celda = screen.getByLabelText('Inglés 2°')
    await userEvent.clear(celda)
    await userEvent.type(celda, '0')
    expect(screen.getByText('Cada celda debe ser un número entero de 1 a 20.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar cargas' })).toBeDisabled()
  })

  it('exige HORARIO_CONFIGURAR', async () => {
    guardarSesion(sesionDe('DOCENTE', ['HORARIO_CONSULTAR']))
    servidor.use(http.get('*/api/v1/anios-escolares/2027/periodo-disponibilidad', () => HttpResponse.json(PERIODO_ABIERTO)))
    await renderizarRuta('/intranet/horarios/2027/configuracion')

    expect(screen.queryByRole('tablist', { name: 'Secciones de la configuración' })).not.toBeInTheDocument()
  })
})
